package com.example.smsgateway.ui.help

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smsgateway.R
import com.google.android.material.appbar.MaterialToolbar

class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        // ── Views ────────────────────────────────────────────────────────
        val toolbar   = findViewById<MaterialToolbar>(R.id.toolbarHelp)
        val tvIntro   = findViewById<TextView>(R.id.tvHelpIntro)
        val tvFooter  = findViewById<TextView>(R.id.tvHelpFooter)
        val rvSections = findViewById<RecyclerView>(R.id.rvHelpSections)

        // ── Toolbar ──────────────────────────────────────────────────────
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // ── Content ──────────────────────────────────────────────────────
        tvIntro.text  = HelpContent.intro()
        tvFooter.text = HelpContent.footer(appVersionName())

        rvSections.layoutManager = LinearLayoutManager(this)
        rvSections.adapter = HelpSectionAdapter(HelpContent.sections())
        rvSections.setHasFixedSize(false)
    }

    /**
     * Reads the version name declared in the module's defaultConfig
     * without depending on BuildConfig (which requires buildConfig = true).
     */
    private fun appVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "—"
    } catch (e: Exception) {
        "—"
    }
}