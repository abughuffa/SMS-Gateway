package com.example.smsgateway.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.smsgateway.MainActivity
import com.example.smsgateway.R
import com.example.smsgateway.log.LogBuffer
import com.example.smsgateway.ui.connection.ConnectionConfig
import com.example.smsgateway.ui.connection.ConnectionMode
import com.example.smsgateway.ui.connection.ConnectionPrefs
//import com.example.smsgateway.ui.connection.UsbMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GatewayService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    // Scope for background work; cancel on shutdown
    private val workScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val CHANNEL_ID = "gateway"
        private const val NOTIF_ID = 1
    }

    private var lastConfig: ConnectionConfig = ConnectionConfig()
    private var restartJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val prefs = getSharedPreferences("connection_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)

        lastConfig = ConnectionPrefs.load(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val cfg = ConnectionPrefs.load(this)
        lastConfig = cfg

        LogBuffer.i("Gateway", "onStartCommand, mode=${cfg.mode}")

        startForeground(
            NOTIF_ID,
            buildNotification(getString(R.string.status_running))
        )

        restartJob?.cancel()
        restartJob = workScope.launch {
            startGatewayWithConfig(cfg)
        }

        return START_STICKY
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (prefs == null) return

        val newConfig = ConnectionPrefs.load(this)

        // Only restart if config actually changed
        if (newConfig == lastConfig) {
            return
        }

        LogBuffer.i("GatewayService", "Config changed, restarting gateway")

        restartGateway(newConfig)
        lastConfig = newConfig
    }

    private fun restartGateway(cfg: ConnectionConfig) {
        restartJob?.cancel()
        restartJob = workScope.launch {
            try {
                startGatewayWithConfig(cfg)
            } catch (e: Exception) {
                LogBuffer.e("GatewayService", "Error restarting gateway: ${e.message}", e)
                updateNotification("Error: ${e.message}")
            }
        }
    }

    private fun startGatewayWithConfig(cfg: ConnectionConfig) {
        try {
            GatewayServer.stop()
        } catch (_: Throwable) {
            // ignore stop errors from a previous instance
        }

        when (cfg.mode) {
            ConnectionMode.WIFI -> {
                GatewayServer.configure(
                    context = this@GatewayService,
                    onStatus = { updateNotification(it) }
                )
                GatewayServer.start(
                    mode = GatewayServer.ConnectionMode.WIFI,
                    port = cfg.wifiPort,
                    apiToken = cfg.wifiToken,
                    bindAll = cfg.wifiBindAll
                )
                updateNotification(GatewayServer.status())
            }

            ConnectionMode.USB -> {
                GatewayServer.configure(
                    context = this@GatewayService,
                    onStatus = { updateNotification("USB: $it") }
                )

                GatewayServer.start(
                    mode = GatewayServer.ConnectionMode.WIFI,
                    port = cfg.usbPort,
                    apiToken = null,
                    bindAll = false
                )

                updateNotification(GatewayServer.status())
            }

            ConnectionMode.NONE -> {
                updateNotification(getString(R.string.service_no_mode))
            }
        }
    }

    override fun onDestroy() {
        val prefs = getSharedPreferences("connection_prefs", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(this)

        restartJob?.cancel()

        workScope.launch {
            try {
                GatewayServer.stop()
            } catch (_: Throwable) {
            } finally {
                try {
                    GatewayServer.shutdown()
                } catch (_: Throwable) {
                }
            }
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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