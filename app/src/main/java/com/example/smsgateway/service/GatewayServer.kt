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
import androidx.core.content.ContextCompat
import com.example.smsgateway.db.InboxDb
import com.example.smsgateway.log.Log
import com.example.smsgateway.model.ErrorResponse
import com.example.smsgateway.model.InboxResponse
import com.example.smsgateway.model.SendRequest
import com.example.smsgateway.model.SendResponse
import com.example.smsgateway.model.StatusResponse
import com.example.smsgateway.model.WebhookRequest
import com.example.smsgateway.sms.SmsSender
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import java.util.UUID

object GatewayServer {

    private const val TAG = "GatewayServer"
    private const val ACTION_USB_PERMISSION = "com.example.smsgateway.USB_PERMISSION"

    // Turn off expensive pretty-printing; avoid unnecessary object churn
    private val json = Json { ignoreUnknownKeys = true }

    // Use a supervisor job to allow independent failure of sub-tasks
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var appContext: Context? = null
    @Volatile private var db: InboxDb? = null
    @Volatile private var statusListener: ((String) -> Unit)? = null
    @Volatile private var lastStatus: String = "Disconnected"
    @Volatile private var running = false
    @Volatile private var modeLabel: String = "Gateway"
    @Volatile private var apiKey: String? = null

    @Volatile private var httpServer: EmbeddedServer<*, *>? = null
    @Volatile private var serverStartedAt: Long = 0L
    @Volatile private var currentMode: ConnectionMode? = null

    @Volatile private var accessoryFd: ParcelFileDescriptor? = null
    @Volatile private var usbIoJob: Job? = null
    @Volatile private var permissionReceiver: BroadcastReceiver? = null

    enum class ConnectionMode {
        WIFI,
        USB_ADB,
        USB_ACCESSORY
    }

    fun configure(context: Context, onStatus: (String) -> Unit) {
        val ctx = context.applicationContext
        appContext = ctx
        db = InboxDb.get(ctx)
        statusListener = onStatus
        registerPermissionReceiver(ctx)
        Log.i(TAG, "Configured")
    }

    fun isRunning(): Boolean = running
    fun status(): String = lastStatus

    fun start(mode: ConnectionMode, port: Int, apiToken: String? = null, bindAll: Boolean = false) {
        if (running) {
            Log.w(TAG, "start() ignored — already running")
            return
        }

        val ctx = appContext ?: run {
            setStatus("Error: not configured")
            return
        }

        running = true
        apiKey = apiToken?.takeIf { it.isNotBlank() }

        when (mode) {
            ConnectionMode.WIFI -> startWifi(ctx, port, bindAll)
            ConnectionMode.USB_ADB -> startUsbAdb(ctx, port)
            ConnectionMode.USB_ACCESSORY -> startUsbAccessory(ctx)
        }
    }

    fun stop() {
        val wasRunning = running
        running = false

        stopHttpServer()
        stopUsbAccessory()
        usbIoJob?.cancel()
        usbIoJob = null

        currentMode = null
        setStatus("Disconnected")

        if (wasRunning) {
            Log.i(TAG, "Gateway stopped")
        }
    }

    fun shutdown() {
        stop()
        permissionReceiver?.let {
            try {
                appContext?.unregisterReceiver(it)
            } catch (_: Throwable) {
            }
        }
        permissionReceiver = null
        statusListener = null
        scope.cancel()
        Log.i(TAG, "Gateway shut down")
    }

    private fun startWifi(ctx: Context, port: Int, bindAll: Boolean) {
        modeLabel = "WIFI"
        val host = if (bindAll) "0.0.0.0" else "127.0.0.1"

        try {
            val s = embeddedServer(
                factory = CIO,
                port = port,
                host = host,
                module = { httpModule() }
            ).start(wait = false)

            httpServer = s
            serverStartedAt = System.currentTimeMillis()
            setStatus("Listening on $host:$port")
            Log.i(TAG, "HTTP server started on $host:$port")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start HTTP server", t)
            setStatus("Error: ${t.message}")
            running = false
        }
    }

    private fun startUsbAdb(ctx: Context, port: Int) {
        try {
            modeLabel = "USB_ADB"
            val host = "127.0.0.1"
            val s = embeddedServer(
                factory = CIO,
                port = port,
                host = host,
                module = { httpModule() }
            ).start(wait = false)

            httpServer = s
            serverStartedAt = System.currentTimeMillis()
            setStatus("Ready. On PC run: adb forward tcp:$port tcp:$port")
            Log.i(TAG, "USB ADB active on $host:$port")
        } catch (t: Throwable) {
            Log.e(TAG, "USB ADB start failed", t)
            setStatus("Error: ${t.message}")
            running = false
        }
    }

    private fun stopHttpServer() {
        val s = httpServer ?: return
        httpServer = null
        serverStartedAt = 0L

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    s.stop(500, 1000)
                } catch (t: Throwable) {
                    Log.w(TAG, "Stop error", t)
                }
            }
        }
    }

    private fun Application.httpModule() {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = false
            })
        }
        install(CallLogging)
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                Log.e(TAG, "Unhandled error", cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("internal_error", cause.message)
                )
            }
        }

        routing {
            get("/status") {
                call.respond(buildHttpStatus())
            }

            post("/send") {
                val req = call.receive<SendRequest>()
                if (req.to.isBlank() || req.body.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("bad_request", "'to' and 'body' are required")
                    )
                    return@post
                }

                val ref = req.reference ?: UUID.randomUUID().toString()
                val result = withContext(Dispatchers.IO) {
                    SmsSender.send(
                        context = appContext!!,
                        phone = req.to,
                        message = req.body,
                        simSlot = req.simSlot ?: 0,
                        reference = ref
                    )
                }

                if (result.ok) db?.incr("stat.sent")
                call.respond(
                    if (result.ok) HttpStatusCode.Accepted else HttpStatusCode.BadGateway,
                    SendResponse(
                        id = ref,
                        status = if (result.ok) "queued" else "failed",
                        reference = req.reference,
                        error = result.error
                    )
                )
            }

            get("/inbox") {
                val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 200
                val list = withContext(Dispatchers.IO) { db?.since(since, limit) ?: emptyList() }
                call.respond(InboxResponse(list))
            }

            post("/webhook") {
                val req = call.receive<WebhookRequest>()
                if (req.url.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("bad_request", "'url' is required")
                    )
                    return@post
                }

                db?.setMeta("webhook.url", req.url)
                db?.setMeta("webhook.secret", req.secret)
                call.respond(HttpStatusCode.OK, mapOf("ok" to true))
            }

            delete("/webhook") {
                db?.setMeta("webhook.url", null)
                db?.setMeta("webhook.secret", null)
                call.respond(HttpStatusCode.OK, mapOf("ok" to true))
            }
        }
    }

    private fun buildHttpStatus(): StatusResponse {
        val uptimeSec = if (serverStartedAt == 0L) 0L
        else (System.currentTimeMillis() - serverStartedAt) / 1000

        return StatusResponse(
            mode = modeLabel,
            uptimeSec = uptimeSec,
            simReady = SmsSender.isSimReady(appContext!!),
            sentCount = db?.getMeta("stat.sent")?.toIntOrNull() ?: 0,
            receivedCount = db?.count() ?: 0
        )
    }

    private fun startUsbAccessory(ctx: Context) {
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

    private fun openAccessory(ctx: Context, usbManager: UsbManager, accessory: UsbAccessory) {
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

        modeLabel = "USB_ACCESSORY"
        accessoryFd = fd
        setStatus("Accessory connected: ${accessory.model ?: accessory.manufacturer ?: "unknown"}")

        usbIoJob = scope.launch { serveAccessory(fd) }
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
                val response = processAccessoryCommand(line)
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

    private fun stopUsbAccessory() {
        try { accessoryFd?.close() } catch (_: Throwable) {}
        accessoryFd = null
    }

    private fun registerPermissionReceiver(ctx: Context) {
        if (permissionReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != ACTION_USB_PERMISSION) return

                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                val accessory: UsbAccessory? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
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

    private fun processAccessoryCommand(line: String): String {
        val database = db ?: return """{"error":"db_unavailable"}"""
        val ctx = appContext ?: return """{"error":"context_unavailable"}"""

        return try {
            val obj = json.parseToJsonElement(line) as JsonObject
            val cmd = obj["cmd"]?.toString()?.trim('"') ?: ""

            when (cmd) {
                "status" -> buildJsonObject {
                    put("mode", "USB_ACCESSORY")
                    put("simReady", SmsSender.isSimReady(ctx))
                    put("receivedCount", database.count())
                    put("sentCount", database.getMeta("stat.sent")?.toIntOrNull() ?: 0)
                }.toString()

                "send" -> {
                    val to = obj["to"]?.toString()?.trim('"') ?: ""
                    val body = obj["body"]?.toString()?.trim('"') ?: ""
                    val slot = obj["simSlot"]?.toString()?.toIntOrNull() ?: 0
                    val ref = obj["reference"]?.toString()?.trim('"') ?: UUID.randomUUID().toString()

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