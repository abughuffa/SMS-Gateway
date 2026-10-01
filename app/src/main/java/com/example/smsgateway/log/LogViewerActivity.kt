package com.example.smsgateway.log

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smsgateway.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class LogViewerActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var adapter: LogAdapter
    private lateinit var etFilter: TextInputEditText
    private lateinit var cbAutoScroll: CheckBox

    private var filterText: String = ""
    private val activeLevels: MutableSet<LogBuffer.Level> =
        mutableSetOf(
            LogBuffer.Level.DEBUG,
            LogBuffer.Level.INFO,
            LogBuffer.Level.WARN,
            LogBuffer.Level.ERROR
        )

    /** Level buttons paired with the level they control. */
    private val levelButtons: List<Pair<MaterialButton, LogBuffer.Level>> by lazy {
        listOf(
            findViewById<MaterialButton>(R.id.btnLevelDebug) to LogBuffer.Level.DEBUG,
            findViewById<MaterialButton>(R.id.btnLevelInfo)  to LogBuffer.Level.INFO,
            findViewById<MaterialButton>(R.id.btnLevelWarn)  to LogBuffer.Level.WARN,
            findViewById<MaterialButton>(R.id.btnLevelError) to LogBuffer.Level.ERROR
        )
    }

    private val liveListener = LogBuffer.Listener { entry ->
        // Called on the logging thread. Marshal to the UI thread.
        runOnUiThread {
            if (matches(entry)) {
                adapter.append(entry)
                if (cbAutoScroll.isChecked) {
                    rv.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left, top = bars.top,
                right = bars.right, bottom = bars.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

        rv = findViewById(R.id.rvLog)
        etFilter = findViewById(R.id.etFilter)
        cbAutoScroll = findViewById(R.id.cbAutoScroll)

        adapter = LogAdapter()
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // Initial snapshot
        adapter.submit(LogBuffer.snapshot().filter { matches(it) })
        rv.scrollToPosition((adapter.itemCount - 1).coerceAtLeast(0))

        // Filter text
        etFilter.doAfterTextChanged { text ->
            filterText = text?.toString()?.trim().orEmpty()
            adapter.submit(LogBuffer.snapshot().filter { matches(it) })
            rv.scrollToPosition((adapter.itemCount - 1).coerceAtLeast(0))
        }

        // Level toggles — wired + visually refreshed in one place
        setupLevelButtons()

        // Actions
        findViewById<MaterialButton>(R.id.btnClear).setOnClickListener {
            LogBuffer.clear()
            adapter.clear()
        }
        findViewById<MaterialButton>(R.id.btnShare).setOnClickListener {
            val dump = LogBuffer.dump()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "SMS Gateway log")
                putExtra(Intent.EXTRA_TEXT, dump)
            }
            startActivity(Intent.createChooser(intent, "Share log"))
        }

        LogBuffer.addListener(liveListener)
    }

    override fun onDestroy() {
        LogBuffer.removeListener(liveListener)
        super.onDestroy()
    }

    /** Wire clicks on every level button and paint their initial state. */
    private fun setupLevelButtons() {
        levelButtons.forEach { (button, level) ->
            button.setOnClickListener {
                toggleLevel(level)
                refreshLevelButtons()
            }
        }
        refreshLevelButtons()
    }

    /** Flip a level on/off (keeping at least one active). */
    private fun toggleLevel(level: LogBuffer.Level) {
        if (activeLevels.contains(level)) {
            if (activeLevels.size == 1) return
            activeLevels.remove(level)
        } else {
            activeLevels.add(level)
        }
        adapter.submit(LogBuffer.snapshot().filter { matches(it) })
        rv.scrollToPosition((adapter.itemCount - 1).coerceAtLeast(0))
    }

    /** Update the visual state of every level button. */
    private fun refreshLevelButtons() {
        val activeBg   = ContextCompat.getColor(this, R.color.level_active_bg)
        val activeFg   = ContextCompat.getColor(this, R.color.level_active_text)
        val inactiveBg = ContextCompat.getColor(this, R.color.level_inactive_bg)
        val inactiveFg = ContextCompat.getColor(this, R.color.level_inactive_text)
        val inactiveStroke = ContextCompat.getColor(this, R.color.level_inactive_stroke)

        levelButtons.forEach { (button, level) ->
            val active = activeLevels.contains(level)
            button.backgroundTintList = ColorStateList.valueOf(
                if (active) activeBg else inactiveBg
            )
            button.setTextColor(if (active) activeFg else inactiveFg)
            button.strokeColor = ColorStateList.valueOf(
                if (active) activeBg else inactiveStroke
            )
        }
    }

    private fun matches(e: LogBuffer.Entry): Boolean {
        if (!activeLevels.contains(e.level)) return false
        if (filterText.isEmpty()) return true
        val f = filterText.lowercase()
        return e.tag.lowercase().contains(f) ||
                e.message.lowercase().contains(f)
    }
}