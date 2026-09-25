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
    private lateinit var act: MaterialAutoCompleteTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

        act = findViewById(R.id.actConnectionType)

        act.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        )

        val saved = vm.config.value.mode
        val initialIndex = modes.indexOf(saved).takeIf { it >= 0 } ?: 0
        act.setText(labels[initialIndex], false)
        showFragment(modes[initialIndex])

        act.setOnItemClickListener { _, _, position, _ ->
            showFragment(modes[position])
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }

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
}