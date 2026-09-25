package com.example.smsgateway.ui.connection

import android.content.Context
import androidx.core.content.edit

enum class ConnectionMode { NONE, USB, WIFI }
enum class UsbMode { ADB_FORWARD, ACCESSORY }

data class ConnectionConfig(
    val mode: ConnectionMode = ConnectionMode.NONE,
    val wifiPort: Int = 8080,
    val wifiBindAll: Boolean = true,
    val wifiToken: String? = null,
    val usbMode: UsbMode = UsbMode.ADB_FORWARD,
    val usbPort: Int = 8081
)

object ConnectionPrefs {
    private const val PREFS = "connection_prefs"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): ConnectionConfig {
        val p = prefs(ctx)

        val rawMode = p.getString("mode", ConnectionMode.NONE.name)!!
        // Guard against stale / corrupt values from previous installs.
        val mode = runCatching { ConnectionMode.valueOf(rawMode) }
            .getOrDefault(ConnectionMode.NONE)

        val rawUsb = p.getString("usb_mode", UsbMode.ADB_FORWARD.name)!!
        val usbMode = runCatching { UsbMode.valueOf(rawUsb) }
            .getOrDefault(UsbMode.ADB_FORWARD)

        return ConnectionConfig(
            mode = mode,
            wifiPort = p.getInt("wifi_port", 8080).coerceIn(1, 65535),
            wifiBindAll = p.getBoolean("wifi_bind_all", true),
            wifiToken = p.getString("wifi_token", null),
            usbMode = usbMode,
            usbPort = p.getInt("usb_port", 8081).coerceIn(1, 65535)
        )
    }

    fun save(ctx: Context, cfg: ConnectionConfig) {
        prefs(ctx).edit {
            putString("mode", cfg.mode.name)
            putInt("wifi_port", cfg.wifiPort)
            putBoolean("wifi_bind_all", cfg.wifiBindAll)
            putString("wifi_token", cfg.wifiToken)
            putString("usb_mode", cfg.usbMode.name)
            putInt("usb_port", cfg.usbPort)
        }
    }
}