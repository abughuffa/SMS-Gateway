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
import com.example.smsgateway.log.LogBuffer
import com.example.smsgateway.log.LogViewerActivity
import com.example.smsgateway.service.GatewayService
import com.example.smsgateway.ui.connection.ConnectionActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvServiceStatus: TextView
    private lateinit var btnToggleService: Button
    private var isServiceRunning = false

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            startGatewayService()
        } else {
            tvServiceStatus.text = getString(R.string.status_stopped)
            tvServiceStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
            btnToggleService.text = getString(R.string.action_start)
            isServiceRunning = false
        }
    }

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

        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        btnToggleService = findViewById(R.id.btnToggleService)

        // Check if service is currently running
        isServiceRunning = isGatewayServiceRunning()
        updateUI()

        // Toggle button: Start or Stop
        btnToggleService.setOnClickListener {
            if (isServiceRunning) {
                stopGatewayService()
            } else {
                requestNeededPermissions()
            }
        }

        findViewById<Button>(R.id.btnConnection).setOnClickListener {
            startActivity(Intent(this, ConnectionActivity::class.java))
        }

        findViewById<Button>(R.id.btnProcessLog).setOnClickListener {
            startActivity(Intent(this, LogViewerActivity::class.java))
        }

        LogBuffer.i("App", "SMS Gateway started, pid=${android.os.Process.myPid()}")
    }

    private fun startGatewayService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, GatewayService::class.java)
        )
        isServiceRunning = true
        updateUI()
        LogBuffer.i("Main", "Starting GatewayService")
    }

    private fun stopGatewayService() {
        stopService(Intent(this, GatewayService::class.java))
        isServiceRunning = false
        updateUI()
        LogBuffer.i("Main", "Stopping GatewayService")
    }

    private fun updateUI() {
        if (isServiceRunning) {
            tvServiceStatus.text = getString(R.string.status_running)
            tvServiceStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, theme))
            btnToggleService.text = getString(R.string.action_stop)
        } else {
            tvServiceStatus.text = getString(R.string.status_stopped)
            tvServiceStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
            btnToggleService.text = getString(R.string.action_start)
        }
    }

    /**
     * Checks all required runtime permissions; if any are missing, requests
     * them and defers starting the service until they are all granted.
     */
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

        if (needed.isEmpty()) {
            startGatewayService()
        } else {
            permLauncher.launch(needed.toTypedArray())
        }

        LogBuffer.i("Main", "Requesting ${needed.size} permissions: ${needed.joinToString { it.substringAfterLast('.') }}")
    }

    /**
     * Check if GatewayService is currently running.
     * Note: This is a simple check. For production, consider using a more robust method.
     */
    private fun isGatewayServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == GatewayService::class.java.name }
    }
}