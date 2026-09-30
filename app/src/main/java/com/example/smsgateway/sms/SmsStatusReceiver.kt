package com.example.smsgateway.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
//import android.util.Log
import com.example.smsgateway.db.InboxDb
import com.example.smsgateway.log.Log

/**
 * Receives SMS_SENT / SMS_DELIVERED result broadcasts fired by the
 * telephony stack in response to the PendingIntents we attach in SmsSender.
 *
 * The PendingIntents are addressed to our own package, so this receiver is
 * not exported and only sees intents we created.
 */
class SmsStatusReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "SmsStatus"
        const val ACTION_SENT = "com.example.smsgateway.SMS_SENT"
        const val ACTION_DELIVERED = "com.example.smsgateway.SMS_DELIVERED"
        const val EXTRA_REFERENCE = "reference"
        const val EXTRA_PART_INDEX = "part_index"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reference = intent.getStringExtra(EXTRA_REFERENCE) ?: "?"
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0)

        when (intent.action) {
            ACTION_SENT -> {
                val ok = resultCode == Activity.RESULT_OK
                val reason = intent.getStringExtra("error") // set by some ROMs
                Log.i(TAG, "SMS_SENT ref=$reference part=$partIndex ok=$ok err=$reason")
                if (!ok) {
                    InboxDb.get(context).incr("stat.sent_failed")
                }
            }
            ACTION_DELIVERED -> {
                val ok = resultCode == Activity.RESULT_OK
                Log.i(TAG, "SMS_DELIVERED ref=$reference part=$partIndex ok=$ok")
            }
        }
    }
}