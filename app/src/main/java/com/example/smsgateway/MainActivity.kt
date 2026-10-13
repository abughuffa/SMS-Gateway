package com.example.smsgateway

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
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
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.example.smsgateway.log.LogBuffer
import com.example.smsgateway.log.LogFragment
import com.example.smsgateway.service.GatewayService
import com.example.smsgateway.ui.settings.SettingsActivity
import com.example.smsgateway.ui.help.HelpActivity
import com.google.android.material.appbar.MaterialToolbar

class MainActivity : AppCompatActivity() {

    private lateinit var tvServiceStatus: TextView
    private lateinit var btnToggleService: Button
    private lateinit var btnViewLog: Button

    private var isServiceRunning = false

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            startGatewayService()
        } else {
            tvServiceStatus.text = getString(R.string.stopped)
            tvServiceStatus.setTextColor(
                resources.getColor(android.R.color.holo_red_dark, theme)
            )
            btnToggleService.text = getString(R.string.start)
            isServiceRunning = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ── Apply system-bar insets to the root layout ────────────────────
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left   = bars.left,
                top    = bars.top,
                right  = bars.right,
                bottom = bars.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

        // ── Toolbar ──────────────────────────────────────────────────────
        // The MaterialToolbar in activity_main.xml has NO app:menu attribute.
        // We attach it as the SupportActionBar and populate the menu from
        // onCreateOptionsMenu() below. This is the correct pattern when using
        // setSupportActionBar — the two menu-population mechanisms (XML app:menu
        // vs. onCreateOptionsMenu) cannot coexist.
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // ── Views ────────────────────────────────────────────────────────
        tvServiceStatus  = findViewById(R.id.tvServiceStatus)
        btnToggleService = findViewById(R.id.btnToggleService)
        btnViewLog       = findViewById(R.id.btnViewLog)

        // ── Initial UI state ─────────────────────────────────────────────
        isServiceRunning = isGatewayServiceRunning()
        updateUI()

        // ── Button: start / stop the gateway service ─────────────────────
        btnToggleService.setOnClickListener {
            if (isServiceRunning) {
                stopGatewayService()
            } else {
                requestNeededPermissions()
            }
        }

        // ── Button: toggle the log fragment ──────────────────────────────
        btnViewLog.setOnClickListener {
            if (supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                        is LogFragment
            ) {
                supportFragmentManager.popBackStack(
                    BACK_STACK_LOG,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE
                )
            } else {
                showLogViewer()
            }
        }

        // Keep the button label in sync when the log fragment is added/removed
        // (including when the user presses Back to dismiss it).
        supportFragmentManager.addOnBackStackChangedListener {
            val showing = supportFragmentManager
                .findFragmentById(R.id.fragmentContainer) is LogFragment
            btnViewLog.text = getString(
                if (showing) R.string.hide_log
                else R.string.view_log
            )
        }

        LogBuffer.i("App", "SMS Gateway started, pid=${android.os.Process.myPid()}")
    }

    // ─────────────────────────────────────────────────────────────────────
    // Toolbar menu — populated from R.menu.menu_main via the framework
    // ─────────────────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_help -> {
                startActivity(Intent(this, HelpActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Fragment management
    // ─────────────────────────────────────────────────────────────────────

    /** Replace the fragment container with the LogViewerFragment. */
    private fun showLogViewer() {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, LogFragment())
            addToBackStack(BACK_STACK_LOG)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Service lifecycle
    // ─────────────────────────────────────────────────────────────────────

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
            tvServiceStatus.text = getString(R.string.running)
            tvServiceStatus.setTextColor(
                resources.getColor(android.R.color.holo_green_dark, theme)
            )
            btnToggleService.text = getString(R.string.stop)
        } else {
            tvServiceStatus.text = getString(R.string.stopped)
            tvServiceStatus.setTextColor(
                resources.getColor(android.R.color.holo_red_dark, theme)
            )
            btnToggleService.text = getString(R.string.start)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Permissions
    // ─────────────────────────────────────────────────────────────────────

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

        LogBuffer.i(
            "Main",
            "Requesting ${needed.size} permissions: " +
                    needed.joinToString { it.substringAfterLast('.') }
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Check if GatewayService is currently running.
     * Note: This is a simple check. For production, consider using a more robust method.
     */
    @Suppress("DEPRECATION")
    private fun isGatewayServiceRunning(): Boolean {
        val manager =
            getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == GatewayService::class.java.name }
    }

    private companion object {
        const val BACK_STACK_LOG = "log_viewer"
    }
}