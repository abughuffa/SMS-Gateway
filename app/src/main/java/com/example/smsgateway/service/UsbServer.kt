package com.example.smsgateway.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.smsgateway.db.InboxDb
import com.example.smsgateway.sms.SmsSender
import com.example.smsgateway.ui.connection.UsbMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object UsbServer {

    private const val TAG = "UsbServer"
    private const val ACTION_USB_PERMISSION = "com.example.smsgateway.USB_PERMISSION"

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var appContext: Context? = null
    @Volatile private var db: InboxDb? = null
    @Volatile private var statusListener: ((String) -> Unit)? = null
    @Volatile private var lastStatus: String = "Disconnected"

    @Volatile private var running = false
    @Volatile private var currentMode: UsbMode? = null

    @Volatile private var accessoryFd: ParcelFileDescriptor? = null
    @Volatile private var ioJob: Job? = null
    @Volatile private var permissionReceiver: BroadcastReceiver? = null

    fun configure(context: Context, onStatus: (String) -> Unit) {
        val ctx = context.applicationContext
        appContext = ctx
        db = InboxDb.get(ctx)
        statusListener = onStatus
        registerPermissionReceiver(ctx)
    }

    fun isRunning(): Boolean = running
    fun status(): String = lastStatus

    fun start(mode: UsbMode, usbPort: Int, wifiPort: Int) {
        if (running) {
            Log.w(TAG, "start() ignored — already running")
            return
        }
        val ctx = appContext ?: run {
            setStatus("Error: not configured")
            return
        }

        currentMode = mode
        running = true

        when (mode) {
            UsbMode.ADB_FORWARD -> startAdbForward(ctx, usbPort, wifiPort)
            UsbMode.ACCESSORY -> startAccessory(ctx)
        }
    }

    /**
     * Stops the USB session. Idempotent and never blocks the caller.
     * Does NOT cancel the object scope — call [shutdown] for that.
     */
    fun stop() {
        val wasRunning = running
        val modeBefore = currentMode
        running = false

        ioJob?.cancel()
        ioJob = null

        try { accessoryFd?.close() } catch (_: Throwable) {}
        accessoryFd = null

        if (wasRunning && modeBefore == UsbMode.ADB_FORWARD) {
            // Fire-and-forget; WifiServer.stop() is suspend/IO-safe.
            scope.launch {
                try { WifiServer.stop() } catch (_: Throwable) {}
            }
        }

        currentMode = null
        setStatus("Disconnected")
        Log.i(TAG, "USB stopped")
    }

    /**
     * Fully tears down the object: unregisters the broadcast receiver
     * and cancels the shared coroutine scope. After this, [configure]
     * must be called again before [start].
     */
    fun shutdown() {
        stop()
        permissionReceiver?.let {
            try { appContext?.unregisterReceiver(it) } catch (_: Throwable) {}
        }
        permissionReceiver = null
        statusListener = null
        scope.cancel()
        Log.i(TAG, "UsbServer shut down")
    }

    private fun startAdbForward(ctx: Context, usbPort: Int, wifiPort: Int) {
        try {
            WifiServer.configure(
                context = ctx,
                token = null,
                modeLabel = "USB_ADB"
            ) { s -> setStatus("ADB: $s") }
            WifiServer.start(port = wifiPort, bindAll = false)
            setStatus("Ready. On PC run: adb forward tcp:$usbPort tcp:$wifiPort")
            Log.i(TAG, "ADB_FORWARD active on 127.0.0.1:$wifiPort (host port $usbPort)")
        } catch (t: Throwable) {
            Log.e(TAG, "ADB_FORWARD start failed", t)
            setStatus("Error: ${t.message}")
            running = false
        }
    }

    private fun startAccessory(ctx: Context) {
        val usbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        val accessory: UsbAccessory? = usbManager.accessoryList?.firstOrNull()

        if (accessory == null) {
            setStatus("No USB accessory attached")
            running = false
            return
        }

        if (!usbManager.hasPermission(accessory)) {
            setStatus("Requesting USB permission…")
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(
                ctx, 0,
                Intent(ACTION_USB_PERMISSION).setPackage(ctx.packageName),
                flags
            )
            usbManager.requestPermission(accessory, pi)
            return
        }

        openAccessory(ctx, usbManager, accessory)
    }

    private fun openAccessory(
        ctx: Context,
        usbManager: UsbManager,
        accessory: UsbAccessory
    ) {
        val fd = try {
            usbManager.openAccessory(accessory)
        } catch (t: Throwable) {
            Log.e(TAG, "openAccessory failed", t)
            null
        }

        if (fd == null) {
            setStatus("Failed to open accessory")
            running = false
            return
        }

        accessoryFd = fd
        setStatus("Accessory connected: ${accessory.model ?: accessory.manufacturer ?: "unknown"}")

        ioJob = scope.launch { serveAccessory(fd) }
    }

    private suspend fun serveAccessory(fd: ParcelFileDescriptor) {
        val fileIn = FileInputStream(fd.fileDescriptor)
        val fileOut = FileOutputStream(fd.fileDescriptor)
        val reader = BufferedReader(InputStreamReader(fileIn))
        val writer = BufferedWriter(OutputStreamWriter(fileOut))

        try {
            while (running && currentCoroutineContext().isActive) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val response = processCommand(line)
                writer.write(response)
                writer.write("\n")
                writer.flush()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Accessory session ended: ${t.message}")
        } finally {
            try { writer.close() } catch (_: Throwable) {}
            try { reader.close() } catch (_: Throwable) {}
            try { fd.close() } catch (_: Throwable) {}
            accessoryFd = null
            if (running) {
                setStatus("Accessory disconnected")
                running = false
            }
        }
    }

    private fun registerPermissionReceiver(ctx: Context) {
        if (permissionReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != ACTION_USB_PERMISSION) return
                val granted = intent.getBooleanExtra(
                    UsbManager.EXTRA_PERMISSION_GRANTED, false
                )
                val accessory: UsbAccessory? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        intent.getParcelableExtra(
                            UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java
                        )
                    else
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)

                if (!granted || accessory == null) {
                    setStatus("USB permission denied")
                    running = false
                    return
                }
                val mgr = c.getSystemService(Context.USB_SERVICE) as UsbManager
                openAccessory(c, mgr, accessory)
            }
        }

        ContextCompat.registerReceiver(
            ctx,
            receiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        permissionReceiver = receiver
    }

    private fun processCommand(line: String): String {
        val database = db ?: return """{"error":"db_unavailable"}"""
        val ctx = appContext ?: return """{"error":"context_unavailable"}"""

        return try {
            val obj = json.parseToJsonElement(line) as JsonObject
            val cmd = obj["cmd"]?.toString()?.trim('"') ?: ""

            when (cmd) {
                "status" -> buildJsonObject {
                    put("mode", "USB")
                    put("simReady", SmsSender.isSimReady(ctx))
                    put("receivedCount", database.count())
                    put("sentCount", database.getMeta("stat.sent")?.toIntOrNull() ?: 0)
                }.toString()

                "send" -> {
                    val to = obj["to"]?.toString()?.trim('"') ?: ""
                    val body = obj["body"]?.toString()?.trim('"') ?: ""
                    val slot = obj["simSlot"]?.toString()?.toIntOrNull() ?: 0
                    val ref = obj["reference"]?.toString()?.trim('"')
                        ?: java.util.UUID.randomUUID().toString()

                    if (to.isBlank() || body.isBlank()) {
                        buildJsonObject {
                            put("ok", false)
                            put("error", "'to' and 'body' are required")
                        }.toString()
                    } else {
                        val res = SmsSender.send(ctx, to, body, slot, ref)
                        if (res.ok) database.incr("stat.sent")
                        buildJsonObject {
                            put("ok", res.ok)
                            put("id", ref)
                            put("error", res.error ?: "")
                        }.toString()
                    }
                }

                "inbox" -> {
                    val since = obj["since"]?.toString()?.toLongOrNull() ?: 0L
                    val limit = obj["limit"]?.toString()?.toIntOrNull() ?: 200
                    val list = database.since(since, limit)
                    // Proper JSON array, not a JSON-in-string.
                    buildJsonObject {
                        put("count", list.size)
                        put("messages", buildJsonArray {
                            list.forEach { m ->
                                add(buildJsonObject {
                                    put("id", m.id)
                                    put("from", m.from)
                                    put("body", m.body)
                                    put("ts", m.ts)
                                })
                            }
                        })
                    }.toString()
                }
                else -> """{"error":"unknown command"}"""
            }
        } catch (t: Throwable) {
            Log.w(TAG, "processCommand failed: ${t.message}")
            buildJsonObject {
                put("error", t.message ?: "parse_error")
            }.toString()
        }
    }

    private fun setStatus(s: String) {
        lastStatus = s
        statusListener?.invoke(s)
        Log.i(TAG, s)
    }
}