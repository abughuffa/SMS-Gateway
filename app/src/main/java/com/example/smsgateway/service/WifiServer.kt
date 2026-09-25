package com.example.smsgateway.service

import android.content.Context
import android.util.Log
import com.example.smsgateway.db.InboxDb
import com.example.smsgateway.model.*
import com.example.smsgateway.sms.SmsSender
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID

object WifiServer {

    private const val TAG = "WifiServer"

    @Volatile private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    @Volatile private var startedAt: Long = 0
    @Volatile private var lastStatus: String = "Stopped"
    @Volatile private var apiKey: String? = null
    @Volatile private lateinit var db: InboxDb
    @Volatile private lateinit var appContext: Context
    @Volatile private var statusListener: ((String) -> Unit)? = null

    fun configure(context: Context, token: String?, onStatus: (String) -> Unit) {
        appContext = context.applicationContext
        db = InboxDb(appContext)
        apiKey = token?.takeIf { it.isNotBlank() }
        statusListener = onStatus
    }

    fun isRunning(): Boolean = server != null
    fun status(): String = lastStatus

    fun start(port: Int, bindAll: Boolean) {
        if (server != null) {
            Log.w(TAG, "start() ignored — already running")
            return
        }
        val host = if (bindAll) "0.0.0.0" else "127.0.0.1"
        try {
            val s = embeddedServer(
                factory = CIO,
                port = port,
                host = host,
                module = { module() }
            ).start(wait = false)

            server = s
            startedAt = System.currentTimeMillis()
            setStatus("Listening on $host:$port")
            Log.i(TAG, "Ktor started on $host:$port")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start Ktor", t)
            setStatus("Error: ${t.message}")
            server = null
        }
    }

    fun stop() {
        val s = server ?: return
        server = null
        startedAt = 0
        runBlocking {
            try { s.stop(500, 1000) } catch (t: Throwable) { Log.w(TAG, "Stop error", t) }
        }
        setStatus("Stopped")
        Log.i(TAG, "Ktor stopped")
    }

    private fun setStatus(s: String) {
        lastStatus = s
        statusListener?.invoke(s)
    }

    private fun Application.module() {
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

        intercept(ApplicationCallPipeline.Plugins) {
            val required = apiKey
            if (!required.isNullOrBlank()) {
                val header = call.request.headers["X-Api-Key"]
                    ?: call.request.headers["Authorization"]
                        ?.removePrefix("Bearer ")?.trim()
                if (header != required) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse("unauthorized", "Invalid or missing API key")
                    )
                    finish()
                }
            }
        }

        routing {
            get("/status") {
                call.respond(buildStatus())
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
                        context = appContext,
                        phone = req.to,
                        message = req.body,
                        simSlot = req.simSlot ?: 0
                    )
                }
                if (result.ok) db.incr("stat.sent")
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
                val list = withContext(Dispatchers.IO) { db.since(since, limit) }
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
                db.setMeta("webhook.url", req.url)
                db.setMeta("webhook.secret", req.secret)
                call.respond(HttpStatusCode.OK, mapOf("ok" to true))
            }

            delete("/webhook") {
                db.setMeta("webhook.url", null)
                db.setMeta("webhook.secret", null)
                call.respond(HttpStatusCode.OK, mapOf("ok" to true))
            }
        }
    }

    private fun buildStatus(): StatusResponse {
        val uptimeSec = if (startedAt == 0L) 0L
        else (System.currentTimeMillis() - startedAt) / 1000
        return StatusResponse(
            mode = "WIFI",
            uptimeSec = uptimeSec,
            simReady = SmsSender.isSimReady(appContext),
            sentCount = db.getMeta("stat.sent")?.toIntOrNull() ?: 0,
            receivedCount = db.count()
        )
    }
}