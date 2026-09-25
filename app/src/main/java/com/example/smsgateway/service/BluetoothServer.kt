package com.example.smsgateway.service

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.smsgateway.db.InboxDb
import com.example.smsgateway.model.InboxMessage
import com.example.smsgateway.sms.SmsSender
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID

object BluetoothServer {

    private const val TAG = "BluetoothServer"

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var running = false
    @Volatile private var lastStatus: String = "Stopped"
    @Volatile private lateinit var appContext: Context
    @Volatile private lateinit var db: InboxDb
    @Volatile private var statusListener: ((String) -> Unit)? = null

    fun configure(context: Context, onStatus: (String) -> Unit) {
        appContext = context.applicationContext
        db = InboxDb(appContext)
        statusListener = onStatus
    }

    fun status(): String = lastStatus

    fun start(serviceName: String, uuidStr: String) {
        if (running) return

        val adapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE)
                as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            setStatus("Bluetooth disabled")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            setStatus("Missing BLUETOOTH_CONNECT permission")
            return
        }

        running = true

        scope.launch {
            try {
                val uuid = UUID.fromString(uuidStr)
                serverSocket = adapter.listenUsingRfcommWithServiceRecord(serviceName, uuid)
                setStatus("BT listening as '$serviceName'")
                Log.i(TAG, "Listening on $uuidStr")

                while (running) {
                    val socket = try { serverSocket?.accept() }
                    catch (_: Throwable) { null } ?: break
                    handleClient(socket)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "BT server error", t)
                setStatus("BT error: ${t.message}")
            } finally {
                running = false
            }
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Throwable) {}
        serverSocket = null
        setStatus("Stopped")
    }

    private fun handleClient(socket: BluetoothSocket) = scope.launch {
        Log.i(TAG, "Client connected: ${socket.remoteDevice?.address}")
        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            val writer = BufferedWriter(OutputStreamWriter(socket.outputStream))

            while (running && socket.isConnected) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val response = processCommand(line)
                writer.write(response)
                writer.write("\n")
                writer.flush()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Client session ended: ${t.message}")
        } finally {
            try { socket.close() } catch (_: Throwable) {}
        }
    }

    private fun processCommand(line: String): String {
        return try {
            val obj = json.parseToJsonElement(line) as JsonObject
            val cmd = obj["cmd"]?.toString()?.trim('"') ?: ""
            when (cmd) {
                "status" -> buildJsonObject {
                    put("mode", "BLUETOOTH")
                    put("simReady", SmsSender.isSimReady(appContext))
                    put("receivedCount", db.count())
                    put("sentCount", db.getMeta("stat.sent")?.toIntOrNull() ?: 0)
                }.toString()

                "send" -> {
                    val to = obj["to"]?.toString()?.trim('"') ?: ""
                    val body = obj["body"]?.toString()?.trim('"') ?: ""
                    if (to.isBlank() || body.isBlank()) {
                        """{"ok":false,"error":"'to' and 'body' are required"}"""
                    } else {
                        val res = SmsSender.send(appContext, to, body, 0)
                        if (res.ok) db.incr("stat.sent")
                        buildJsonObject {
                            put("ok", res.ok)
                            put("error", res.error ?: "")
                        }.toString()
                    }
                }

                "inbox" -> {
                    val since = obj["since"]?.toString()?.toLongOrNull() ?: 0L
                    val list = db.since(since)
                    buildJsonObject {
                        put("count", list.size)
                        put(
                            "messages",
                            json.encodeToString(
                                ListSerializer(InboxMessage.serializer()),
                                list
                            )
                        )
                    }.toString()
                }
                else -> """{"error":"unknown command"}"""
            }
        } catch (t: Throwable) {
            """{"error":"${t.message ?: "parse_error"}"}"""
        }
    }

    private fun setStatus(s: String) {
        lastStatus = s
        statusListener?.invoke(s)
    }
}
