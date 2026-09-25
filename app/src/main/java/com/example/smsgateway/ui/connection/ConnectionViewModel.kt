package com.example.smsgateway.ui.connection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectionViewModel(app: Application) : AndroidViewModel(app) {

    private val _config = MutableStateFlow(ConnectionPrefs.load(app))
    val config: StateFlow<ConnectionConfig> = _config.asStateFlow()

    private val _wifiStatus = MutableStateFlow("Stopped")
    val wifiStatus: StateFlow<String> = _wifiStatus.asStateFlow()

    private val _usbStatus = MutableStateFlow("Disconnected")
    val usbStatus: StateFlow<String> = _usbStatus.asStateFlow()

    fun update(transform: (ConnectionConfig) -> ConnectionConfig) {
        val newCfg = transform(_config.value)
        _config.value = newCfg
        ConnectionPrefs.save(getApplication(), newCfg)
    }

    fun setWifiStatus(s: String) { _wifiStatus.value = s }
    fun setUsbStatus(s: String) { _usbStatus.value = s }
}