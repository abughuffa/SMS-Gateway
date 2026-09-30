package com.example.smsgateway.log

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
        mutableSetOf(LogBuffer.Level.DEBUG, LogBuffer.Level.INFO,
            LogBuffer.Level.WARN, LogBuffer.Level.ERROR)

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

        // Filter
        etFilter.doAfterTextChanged { text ->
            filterText = text?.toString()?.trim().orEmpty()
            adapter.submit(LogBuffer.snapshot().filter { matches(it) })
            rv.scrollToPosition((adapter.itemCount - 1).coerceAtLeast(0))
        }

        // Level toggles
        findViewById<MaterialButton>(R.id.btnLevelDebug).setOnClickListener {
            toggleLevel(LogBuffer.Level.DEBUG)
        }
        findViewById<MaterialButton>(R.id.btnLevelInfo).setOnClickListener {
            toggleLevel(LogBuffer.Level.INFO)
        }
        findViewById<MaterialButton>(R.id.btnLevelWarn).setOnClickListener {
            toggleLevel(LogBuffer.Level.WARN)
        }
        findViewById<MaterialButton>(R.id.btnLevelError).setOnClickListener {
            toggleLevel(LogBuffer.Level.ERROR)
        }

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

    private fun toggleLevel(level: LogBuffer.Level) {
        if (activeLevels.contains(level)) {
            // Keep at least one level enabled.
            if (activeLevels.size == 1) return
            activeLevels.remove(level)
        } else {
            activeLevels.add(level)
        }
        adapter.submit(LogBuffer.snapshot().filter { matches(it) })
        rv.scrollToPosition((adapter.itemCount - 1).coerceAtLeast(0))
    }

    private fun matches(e: LogBuffer.Entry): Boolean {
        if (!activeLevels.contains(e.level)) return false
        if (filterText.isEmpty()) return true
        val f = filterText.lowercase()
        return e.tag.lowercase().contains(f) ||
                e.message.lowercase().contains(f)
    }
}