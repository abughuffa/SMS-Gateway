package com.example.smsgateway.service

import android.content.Context
import android.util.Log
import com.example.smsgateway.db.InboxDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object WebhookDispatcher {

    private const val TAG = "Webhook"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { encodeDefaults = true }

    fun dispatch(context: Context, id: Long, from: String, body: String, ts: Long) {
        val db = InboxDb(context.applicationContext)
        val url = db.getMeta("webhook.url") ?: return
        val secret = db.getMeta("webhook.secret")

        val payload = buildJsonObject {
            put("id", id); put("from", from); put("body", body); put("ts", ts)
        }.toString()

        scope.launch {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    if (!secret.isNullOrBlank()) {
                        setRequestProperty("X-Signature", hmacSha256(payload, secret))
                    }
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }
                Log.i(TAG, "Webhook $url -> HTTP ${conn.responseCode}")
                conn.disconnect()
            } catch (t: Throwable) {
                Log.w(TAG, "Webhook failed: ${t.message}")
            }
        }
    }

    private fun hmacSha256(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") {
            "%02x".format(it.toInt() and 0xFF)
        }
    }
}
