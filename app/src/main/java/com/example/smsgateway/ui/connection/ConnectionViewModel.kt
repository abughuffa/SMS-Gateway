package com.example.smsgateway.ui.connection

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ConnectionViewModel : ViewModel() {

    private val _config = MutableStateFlow(ConnectionConfig())
    val config: StateFlow<ConnectionConfig> = _config

    private val _wifiStatus = MutableStateFlow("Offline")
    val wifiStatus: StateFlow<String> = _wifiStatus

    private val _usbStatus = MutableStateFlow("Offline")
    val usbStatus: StateFlow<String> = _usbStatus

    fun loadConfig(context: Context) {
        _config.value = ConnectionPrefs.load(context)
    }

    /**
     * Update the in-memory config (doesn't save yet).
     * Used while editing fields.
     */
    fun update(block: (ConnectionConfig) -> ConnectionConfig) {
        _config.value = block(_config.value)
    }

    /**
     * Save the current config to SharedPreferences.
     * Only saves if something changed.
     * Returns true if saved, false if no change.
     */
    fun save(context: Context): Boolean {
        return ConnectionPrefs.save(context, _config.value)
    }

//    fun updateWifiStatus(status: String) {
//        _wifiStatus.value = status
//        LogBuffer.i("ConnectionVM", "WiFi status: $status")
//    }
//
//    fun updateUsbStatus(status: String) {
//        _usbStatus.value = status
//        LogBuffer.i("ConnectionVM", "USB status: $status")
//    }
}