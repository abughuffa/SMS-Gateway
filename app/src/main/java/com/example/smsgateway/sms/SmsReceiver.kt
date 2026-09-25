package com.example.smsgateway.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.smsgateway.db.InboxDb
import com.example.smsgateway.service.WebhookDispatcher

class SmsReceiver : BroadcastReceiver() {

    companion object { private const val TAG = "SmsReceiver" }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val db = InboxDb(context.applicationContext)
        val now = System.currentTimeMillis()

        for (m in messages) {
            val from = m.displayOriginatingAddress ?: "unknown"
            val body = m.displayMessageBody ?: ""
            val id = db.insert(from, body, now)
            db.incr("stat.received")
            Log.i(TAG, "Stored inbound #$id from $from")
            WebhookDispatcher.dispatch(context, id, from, body, now)
        }
    }
}
