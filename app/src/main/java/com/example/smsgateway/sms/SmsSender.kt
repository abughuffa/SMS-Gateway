package com.example.smsgateway.sms

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

data class SendResult(val ok: Boolean, val error: String? = null)

object SmsSender {

    private const val TAG = "SmsSender"

    fun send(
        context: Context,
        phone: String,
        message: String,
        simSlot: Int = 0
    ): SendResult {
        return try {
            val sms = smsManagerForSlot(context, simSlot)
                ?: defaultSmsManager(context)
                ?: return SendResult(false, "SMS manager unavailable")

            val parts = sms.divideMessage(message)

            val sentIntents = ArrayList<PendingIntent>(parts.size)
            parts.forEachIndexed { idx, _ ->
                sentIntents += PendingIntent.getBroadcast(
                    context,
                    idx,
                    Intent("com.example.smsgateway.SMS_SENT.$idx")
                        .setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            if (parts.size == 1) {
                sms.sendTextMessage(phone, null, parts[0], sentIntents[0], null)
            } else {
                sms.sendMultipartTextMessage(phone, null, parts, sentIntents, null)
            }
            Log.i(TAG, "SMS queued to $phone (${parts.size} part(s))")
            SendResult(ok = true)
        } catch (t: Throwable) {
            Log.e(TAG, "send() failed", t)
            SendResult(ok = false, error = t.message ?: t.javaClass.simpleName)
        }
    }

    /** Returns a per-SIM SmsManager on API 31+; falls back to the deprecated helper below 31. */
    private fun smsManagerForSlot(context: Context, slot: Int): SmsManager? {
        return try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "READ_PHONE_STATE not granted; cannot select SIM")
                return null
            }

            val subMgr = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as? SubscriptionManager
                ?: return null

            val sub = subMgr.activeSubscriptionInfoList
                ?.firstOrNull { it.simSlotIndex == slot }
                ?: subMgr.activeSubscriptionInfoList?.firstOrNull()
                ?: return null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
                    ?.createForSubscriptionId(sub.subscriptionId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(sub.subscriptionId)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "smsManagerForSlot() failed", t)
            null
        }
    }

    /** Default SmsManager; uses the API 31+ system service when available. */
    private fun defaultSmsManager(context: Context): SmsManager? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "defaultSmsManager() failed", t)
            null
        }
    }

    fun isSimReady(context: Context): Boolean {
        return try {
            context.getSystemService(TelephonyManager::class.java)
                ?.simState == TelephonyManager.SIM_STATE_READY
        } catch (_: Throwable) {
            false
        }
    }
}