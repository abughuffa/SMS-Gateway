package com.example.smsgateway

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.smsgateway.service.GatewayService
import com.example.smsgateway.ui.connection.ConnectionActivity

class MainActivity : AppCompatActivity() {

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results ignored — user can retry */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

        val tvState = findViewById<TextView>(R.id.tvState)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            requestNeededPermissions()
            ContextCompat.startForegroundService(
                this,
                Intent(this, GatewayService::class.java)
            )
            tvState.text = getString(R.string.service_starting)
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, GatewayService::class.java))
            tvState.text = getString(R.string.service_stopped)
        }

        findViewById<Button>(R.id.btnConnection).setOnClickListener {
            startActivity(Intent(this, ConnectionActivity::class.java))
        }
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()

        listOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE
        ).forEach {
            if (ContextCompat.checkSelfPermission(this, it) !=
                PackageManager.PERMISSION_GRANTED
            ) needed += it
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) needed += Manifest.permission.POST_NOTIFICATIONS
        }

        if (needed.isNotEmpty()) {
            permLauncher.launch(needed.toTypedArray())
        }
    }
}