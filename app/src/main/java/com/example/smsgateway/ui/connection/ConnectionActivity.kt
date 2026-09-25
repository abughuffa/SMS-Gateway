package com.example.smsgateway.ui.connection

import android.os.Bundle
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.smsgateway.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class ConnectionActivity : AppCompatActivity() {

    private val vm: ConnectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        val tabs = findViewById<TabLayout>(R.id.tabLayout)
        val pager = findViewById<ViewPager2>(R.id.viewPager)

        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 3
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> UsbConnectionFragment()
                1 -> BluetoothConnectionFragment()
                else -> WifiConnectionFragment()
            }
        }

        TabLayoutMediator(tabs, pager) { tab, pos ->
            tab.text = when (pos) {
                0 -> "USB"
                1 -> "Bluetooth"
                else -> "Wi-Fi"
            }
        }.attach()

        findViewById<Button>(R.id.btnSave).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }
    }
}
