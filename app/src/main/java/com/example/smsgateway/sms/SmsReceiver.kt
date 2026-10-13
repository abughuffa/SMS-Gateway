//package com.example.smsgateway.sms
//
//import android.content.BroadcastReceiver
//import android.content.Context
//import android.content.Intent
//import android.provider.Telephony
//import com.example.smsgateway.db.InboxDb
//import com.example.smsgateway.log.Log
//import com.example.smsgateway.service.WebhookDispatcher
//
////class SmsReceiver : BroadcastReceiver() {
////
////    companion object { private const val TAG = "SmsReceiver" }
////
////    override fun onReceive(context: Context, intent: Intent) {
////        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
////
////        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
////        val db = InboxDb.get(context.applicationContext)
////        val now = System.currentTimeMillis()
////
////        for (m in messages) {
////            val from = m.displayOriginatingAddress ?: "unknown"
////            val body = m.displayMessageBody ?: ""
////            val id = db.insert(from, body, now)
////            db.incr("stat.received")
////            Log.i(TAG, "Stored inbound #$id from $from")
////            WebhookDispatcher.dispatch(context, id, from, body, now)
////        }
////    }
////}
//
////import android.content.BroadcastReceiver
////import android.content.Context
////import android.content.Intent
////import android.provider.Telephony
////import com.example.smsgateway.db.InboxDb
////import com.example.smsgateway.log.Log
////import com.example.smsgateway.service.WebhookDispatcher
//
//class SmsReceiver : BroadcastReceiver() {
//
//    companion object { private const val TAG = "SmsReceiver" }
//
//    override fun onReceive(context: Context, intent: Intent) {
//        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
//
//        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
//        val db = InboxDb.get(context.applicationContext)
//
//        // Dual-SIM: subscription id lives on the intent extras.
//        // -1 means unknown / single-SIM.
//        val simSlot = intent.getIntExtra(Telephony.Sms.SUBSCRIPTION_ID, -1)
//
//        for (m in messages) {
//            val from = m.displayOriginatingAddress ?: "unknown"
//            val body = m.displayMessageBody ?: ""
//
//            // Use the PDU's own timestamp, not "now", so parts of the same
//            // logical SMS share a `ts` even if they arrive seconds apart.
//            val ts = if (m.timestampMillis > 0L) m.timestampMillis else System.currentTimeMillis()
//
//            val udh = UdhExtractor.extract(m)
//
//            val id = db.insert(
//                from       = from,
//                body       = body,
//                ts         = ts,
//                simSlot    = simSlot,
//                concatRef  = udh?.concatRef,
//                partNumber = udh?.partNumber,
//                totalParts = udh?.totalParts
//            )
//            db.incr("stat.received")
//
//            Log.i(TAG, "Stored inbound #$id from $from" +
//                    (udh?.let { " [part ${it.partNumber}/${it.totalParts} ref=${it.concatRef}]" } ?: ""))
//
//            WebhookDispatcher.dispatch(
//                context,
//                id         = id,
//                from       = from,
//                body       = body,
//                ts         = ts,
//                simSlot    = simSlot,
//                concatRef  = udh?.concatRef,
//                partNumber = udh?.partNumber,
//                totalParts = udh?.totalParts
//            )
//        }
//    }
//}

package com.example.smsgateway.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.example.smsgateway.db.InboxDb
import com.example.smsgateway.log.Log
import com.example.smsgateway.service.WebhookDispatcher

class SmsReceiver : BroadcastReceiver() {

    companion object { private const val TAG = "SmsReceiver" }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pdus = intent.extras?.get("pdus") as? Array<*>
            ?: run {
                Log.w(TAG, "SMS_RECEIVED with no pdus extra")
                return
            }

        val db = InboxDb.get(context.applicationContext)
        val simSlot = intent.getIntExtra(Telephony.Sms.SUBSCRIPTION_ID, -1)

        for (obj in pdus) {
            val pdu = obj as? ByteArray ?: continue

            // Use SmsMessage only for from/body/timestamp — UDH comes from
            // the raw PDU (getUserDataHeader is blocked on Android 9+).
            val m = try {
                SmsMessage.createFromPdu(pdu)
            } catch (t: Throwable) {
                Log.w(TAG, "createFromPdu failed: ${t.message}")
                continue
            }

            val from = m.displayOriginatingAddress ?: "unknown"
            val body = m.displayMessageBody ?: ""
            val ts   = if (m.timestampMillis > 0L) m.timestampMillis
            else System.currentTimeMillis()

            val udh = UdhExtractor.extract(pdu)

            val id = db.insert(
                from       = from,
                body       = body,
                ts         = ts,
                simSlot    = simSlot,
                concatRef  = udh?.concatRef,
                partNumber = udh?.partNumber,
                totalParts = udh?.totalParts
            )
            db.incr("stat.received")

            Log.i(TAG, "Stored inbound #$id from $from" +
                    (udh?.let { " [part ${it.partNumber}/${it.totalParts} ref=${it.concatRef}]" } ?: ""))

            WebhookDispatcher.dispatch(
                context,
                id         = id,
                from       = from,
                body       = body,
                ts         = ts,
                simSlot    = simSlot,
                concatRef  = udh?.concatRef,
                partNumber = udh?.partNumber,
                totalParts = udh?.totalParts
            )
        }
    }
}