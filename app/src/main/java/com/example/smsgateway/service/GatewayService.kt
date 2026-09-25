package com.example.smsgateway.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.smsgateway.MainActivity
import com.example.smsgateway.R
import com.example.smsgateway.ui.connection.ConnectionMode
import com.example.smsgateway.ui.connection.ConnectionPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GatewayService : Service() {

    // Scope used for (re)start and teardown so we never block the main thread.
    private val workScope =
        CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val CHANNEL_ID = "gateway"
        private const val NOTIF_ID = 1
    }

    override fun onCreate() {
        super.onCreate()

       // System.setProperty("jansi.disable", "true")
        createNotificationChannel()
        // SmsReceiver is registered statically in AndroidManifest.xml with
        // android:permission="android.permission.BROADCAST_SMS". Do NOT
        // register it dynamically here — that would create a second,
        // unpermissioned receiver that any app could spoof.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIF_ID,
            buildNotification(getString(R.string.service_starting))
        )

        // Serialize server start behind any in-flight teardown so a rapid
        // Stop → Start sequence cannot race.
        workScope.launch {
            try { WifiServer.stop() } catch (_: Throwable) {}
            try { UsbServer.stop() } catch (_: Throwable) {}

            val cfg = ConnectionPrefs.load(this@GatewayService)
            when (cfg.mode) {
                ConnectionMode.WIFI -> {
                    WifiServer.configure(
                        context = this@GatewayService,
                        token = cfg.wifiToken,
                        modeLabel = "WIFI"
                    ) { updateNotification(it) }
                    WifiServer.start(cfg.wifiPort, cfg.wifiBindAll)
                    updateNotification(WifiServer.status())
                }
                ConnectionMode.USB -> {
                    UsbServer.configure(this@GatewayService) { updateNotification("USB: $it") }
                    UsbServer.start(
                        mode = cfg.usbMode,
                        usbPort = cfg.usbPort,
                        wifiPort = cfg.wifiPort
                    )
                    updateNotification(UsbServer.status())
                }
                ConnectionMode.NONE ->
                    updateNotification(getString(R.string.service_no_mode))
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        // Never block the main thread waiting on Ktor shutdown.
        workScope.launch {
            try { WifiServer.stop() } catch (_: Throwable) {}
            try { UsbServer.stop() } catch (_: Throwable) {}
            try { UsbServer.shutdown() } catch (_: Throwable) {}
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---- Notification helpers -------------------------------------------------

    private fun createNotificationChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun buildNotification(text: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_gateway)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification(text))
    }
}