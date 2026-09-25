package com.example.smsgateway.ui.connection

import android.content.Context
import androidx.core.content.edit

enum class ConnectionMode { NONE, USB, BLUETOOTH, WIFI }
enum class UsbMode { ADB_FORWARD, ACCESSORY }

data class ConnectionConfig(
    val mode: ConnectionMode = ConnectionMode.NONE,
    val wifiPort: Int = 8080,
    val wifiBindAll: Boolean = true,
    val wifiToken: String? = null,
    val btServiceName: String = "SMS-Gateway",
    val btUuid: String = "00001101-0000-1000-8000-00805F9B34FB",
    val btDiscoverable: Boolean = true,
    val usbMode: UsbMode = UsbMode.ADB_FORWARD,
    val usbPort: Int = 8081
)

object ConnectionPrefs {
    private const val PREFS = "connection_prefs"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): ConnectionConfig {
        val p = prefs(ctx)
        return ConnectionConfig(
            mode = ConnectionMode.valueOf(
                p.getString("mode", ConnectionMode.NONE.name)!!
            ),
            wifiPort = p.getInt("wifi_port", 8080),
            wifiBindAll = p.getBoolean("wifi_bind_all", true),
            wifiToken = p.getString("wifi_token", null),
            btServiceName = p.getString("bt_name", "SMS-Gateway")!!,
            btUuid = p.getString(
                "bt_uuid", "00001101-0000-1000-8000-00805F9B34FB"
            )!!,
            btDiscoverable = p.getBoolean("bt_disc", true),
            usbMode = UsbMode.valueOf(
                p.getString("usb_mode", UsbMode.ADB_FORWARD.name)!!
            ),
            usbPort = p.getInt("usb_port", 8081)
        )
    }

    fun save(ctx: Context, cfg: ConnectionConfig) {
        prefs(ctx).edit {
            putString("mode", cfg.mode.name)
            putInt("wifi_port", cfg.wifiPort)
            putBoolean("wifi_bind_all", cfg.wifiBindAll)
            putString("wifi_token", cfg.wifiToken)
            putString("bt_name", cfg.btServiceName)
            putString("bt_uuid", cfg.btUuid)
            putBoolean("bt_disc", cfg.btDiscoverable)
            putString("usb_mode", cfg.usbMode.name)
            putInt("usb_port", cfg.usbPort)
        }
    }
}
