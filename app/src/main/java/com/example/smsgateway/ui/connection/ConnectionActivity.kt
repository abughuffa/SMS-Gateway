package com.example.smsgateway.ui.connection

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.example.smsgateway.R
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout

class ConnectionActivity : AppCompatActivity() {

    private val vm: ConnectionViewModel by viewModels()

    /** Dropdown labels, index-aligned with [modes]. */
    private val labels = listOf("USB", "Bluetooth", "Wi-Fi")
    private val modes = listOf(
        ConnectionMode.USB,
        ConnectionMode.BLUETOOTH,
        ConnectionMode.WIFI
    )

    private var currentMode: ConnectionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        //val til = findViewById<TextInputLayout>(R.id.tilConnectionType)
        val act = findViewById<MaterialAutoCompleteTextView>(R.id.actConnectionType)

        // 1. Populate the dropdown
        act.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        )

        // 2. Preselect the current mode from the ViewModel
        val saved = vm.config.value.mode
        val initialIndex = modes.indexOf(saved).takeIf { it >= 0 } ?: 0
        act.setText(labels[initialIndex], false)
        showFragment(modes[initialIndex])

        // 3. React to user selection
        act.setOnItemClickListener { _, _, position, _ ->
            showFragment(modes[position])
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            // Config is already persisted on every edit; just exit.
            finish()
        }
        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }
    }

    private fun showFragment(mode: ConnectionMode) {
        if (mode == currentMode) return
        currentMode = mode

        // Persist the mode as soon as the user switches
        vm.update { it.copy(mode = mode) }

        val fragment: Fragment = when (mode) {
            ConnectionMode.USB -> UsbConnectionFragment()
            ConnectionMode.BLUETOOTH -> BluetoothConnectionFragment()
            ConnectionMode.WIFI -> WifiConnectionFragment()
            ConnectionMode.NONE -> UsbConnectionFragment() // fallback
        }

        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, fragment)
        }
    }
}



//
//
//package com.example.smsgateway.ui.connection
//
//import android.os.Bundle
//import android.widget.Button
//import androidx.activity.viewModels
//import androidx.appcompat.app.AppCompatActivity
//import androidx.fragment.app.Fragment
//import androidx.viewpager2.adapter.FragmentStateAdapter
//import androidx.viewpager2.widget.ViewPager2
//import com.example.smsgateway.R
//import com.google.android.material.tabs.TabLayout
//import com.google.android.material.tabs.TabLayoutMediator
//
//class ConnectionActivity : AppCompatActivity() {
//
//    private val vm: ConnectionViewModel by viewModels()
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_connection)
//
//        val tabs = findViewById<TabLayout>(R.id.tabLayout)
//        val pager = findViewById<ViewPager2>(R.id.viewPager)
//
//        pager.adapter = object : FragmentStateAdapter(this) {
//            override fun getItemCount() = 3
//            override fun createFragment(position: Int): Fragment = when (position) {
//                0 -> UsbConnectionFragment()
//                1 -> BluetoothConnectionFragment()
//                else -> WifiConnectionFragment()
//            }
//        }
//
//        TabLayoutMediator(tabs, pager) { tab, pos ->
//            tab.text = when (pos) {
//                0 -> "USB"
//                1 -> "Bluetooth"
//                else -> "Wi-Fi"
//            }
//        }.attach()
//
//        findViewById<Button>(R.id.btnSave).setOnClickListener { finish() }
//        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }
//    }
//}
