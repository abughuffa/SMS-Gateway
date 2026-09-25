package com.example.smsgateway.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.smsgateway.MainActivity
import com.example.smsgateway.R
import com.example.smsgateway.sms.SmsReceiver
import com.example.smsgateway.ui.connection.ConnectionMode
import com.example.smsgateway.ui.connection.ConnectionPrefs

class GatewayService : Service() {

    private val smsReceiver = SmsReceiver()

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            smsReceiver,
            IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification(getString(R.string.service_starting)))

        val cfg = ConnectionPrefs.load(this)
        when (cfg.mode) {
            ConnectionMode.WIFI -> {
                WifiServer.configure(this, cfg.wifiToken) { updateNotification(it) }
                WifiServer.start(cfg.wifiPort, cfg.wifiBindAll)
                updateNotification(WifiServer.status())
            }
            ConnectionMode.USB -> {
                UsbServer.configure(this) { updateNotification("USB: $it") }
                UsbServer.start(
                    mode = cfg.usbMode,
                    usbPort = cfg.usbPort,
                    wifiPort = cfg.wifiPort,
                    wifiBindAll = false
                )
                updateNotification(UsbServer.status())
            }
            ConnectionMode.NONE -> updateNotification(getString(R.string.service_no_mode))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try { WifiServer.stop() } catch (_: Throwable) {}
        try { UsbServer.stop() } catch (_: Throwable) {}
        try { unregisterReceiver(smsReceiver) } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String): Notification {
        val chanId = "gateway"
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(
                NotificationChannel(
                    chanId,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )

        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, chanId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(1, buildNotification(text))
    }
}