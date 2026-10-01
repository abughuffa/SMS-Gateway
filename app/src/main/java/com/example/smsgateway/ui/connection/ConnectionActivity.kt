package com.example.smsgateway.ui.connection

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.smsgateway.R
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.launch

class ConnectionActivity : AppCompatActivity() {

    private val vm: ConnectionViewModel by viewModels()

    private val labels = listOf("USB", "Wi-Fi")
    private val modes = listOf(ConnectionMode.USB, ConnectionMode.WIFI)

    private var currentMode: ConnectionMode? = null
    private lateinit var initialConfig: ConnectionConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        // Only snapshot on first creation. On rotation, restore the saved one
        // so Cancel still reverts to the config the user saw when they opened
        // the screen, not to a partially-edited state.
        initialConfig = savedInstanceState?.let {
            ConnectionConfig(
                mode = ConnectionMode.valueOf(it.getString(STATE_MODE)!!),
                wifiPort = it.getInt(STATE_WIFI_PORT),
                wifiBindAll = it.getBoolean(STATE_WIFI_BIND_ALL),
                wifiToken = it.getString(STATE_WIFI_TOKEN),
                usbMode = UsbMode.valueOf(it.getString(STATE_USB_MODE)!!),
                usbPort = it.getInt(STATE_USB_PORT)
            )
        } ?: ConnectionPrefs.load(this)

        vm.loadConfig(this)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left, top = bars.top,
                right = bars.right, bottom = bars.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

        val act = findViewById<MaterialAutoCompleteTextView>(R.id.actConnectionType)
        act.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, labels))

        val saved = vm.config.value.mode
        val initialIndex = modes.indexOf(saved).takeIf { it >= 0 } ?: 0
        act.setText(labels[initialIndex], false)
        showFragment(modes[initialIndex])

        act.setOnItemClickListener { _, _, position, _ ->
            showFragment(modes[position])
        }

        // SAVE BUTTON: Save both wifi and usb config to SharedPreferences
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val changed = vm.save(this)
            if (changed) {
                findViewById<Button>(R.id.btnSave).text = "Saved!"
                findViewById<Button>(R.id.btnSave).postDelayed({
                    findViewById<Button>(R.id.btnSave).text = getString(R.string.save_and_apply)
                }, 2000)
            }
            finish()
        }

        // CANCEL BUTTON: Revert to initial config
        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            ConnectionPrefs.save(this, initialConfig)
            vm.update { initialConfig }
            finish()
        }

        lifecycleScope.launch {
            vm.config.collect { cfg ->
                val idx = modes.indexOf(cfg.mode)
                if (idx >= 0 && labels[idx] != act.text.toString()) {
                    act.setText(labels[idx], false)
                    showFragment(cfg.mode)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_MODE, initialConfig.mode.name)
        outState.putInt(STATE_WIFI_PORT, initialConfig.wifiPort)
        outState.putBoolean(STATE_WIFI_BIND_ALL, initialConfig.wifiBindAll)
        outState.putString(STATE_WIFI_TOKEN, initialConfig.wifiToken)
        outState.putString(STATE_USB_MODE, initialConfig.usbMode.name)
        outState.putInt(STATE_USB_PORT, initialConfig.usbPort)
    }

    private fun showFragment(mode: ConnectionMode) {
        if (mode == currentMode) return
        currentMode = mode

        vm.update { it.copy(mode = mode) }

        val fragment: Fragment = when (mode) {
            ConnectionMode.USB -> UsbConnectionFragment()
            ConnectionMode.WIFI -> WifiConnectionFragment()
            ConnectionMode.NONE -> UsbConnectionFragment()
        }

        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, fragment)
        }
    }

    private companion object {
        const val STATE_MODE = "snap_mode"
        const val STATE_WIFI_PORT = "snap_wifi_port"
        const val STATE_WIFI_BIND_ALL = "snap_wifi_bind_all"
        const val STATE_WIFI_TOKEN = "snap_wifi_token"
        const val STATE_USB_MODE = "snap_usb_mode"
        const val STATE_USB_PORT = "snap_usb_port"
    }
}