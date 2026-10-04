package com.example.smsgateway.ui.connection

import android.content.Context

data class ConnectionConfig(
    val mode: ConnectionMode = ConnectionMode.NONE,
    val wifiPort: Int = 8080,
    val wifiToken: String? = null,
    val wifiBindAll: Boolean = false,
    val usbPort: Int = 8081)

enum class ConnectionMode { WIFI, USB, NONE }

object ConnectionPrefs {
    private const val PREFS_NAME = "connection_prefs"
    private const val KEY_MODE = "mode"
    private const val KEY_WIFI_PORT = "wifi_port"
    private const val KEY_WIFI_TOKEN = "wifi_token"
    private const val KEY_WIFI_BIND_ALL = "wifi_bind_all"
    private const val KEY_USB_PORT = "usb_port"
    fun load(context: Context): ConnectionConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ConnectionConfig(
            mode = ConnectionMode.valueOf(
                prefs.getString(KEY_MODE, ConnectionMode.NONE.name) ?: ConnectionMode.NONE.name
            ),
            wifiPort = prefs.getInt(KEY_WIFI_PORT, 8080),
            wifiToken = prefs.getString(KEY_WIFI_TOKEN, null),
            wifiBindAll = prefs.getBoolean(KEY_WIFI_BIND_ALL, false),
            usbPort = prefs.getInt(KEY_USB_PORT, 8081)
        )
    }

    /**
     * Save config to SharedPreferences.
     * Returns true if any value actually changed.
     */
    fun save(context: Context, config: ConnectionConfig): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val old = load(context)

        // Only write if something changed
        if (old == config) return false

        prefs.edit().apply {
            putString(KEY_MODE, config.mode.name)
            putInt(KEY_WIFI_PORT, config.wifiPort)
            putString(KEY_WIFI_TOKEN, config.wifiToken)
            putBoolean(KEY_WIFI_BIND_ALL, config.wifiBindAll)
            putInt(KEY_USB_PORT, config.usbPort)
            apply()
        }
        return true
    }
}