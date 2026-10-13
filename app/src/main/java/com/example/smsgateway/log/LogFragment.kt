package com.example.smsgateway.log

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smsgateway.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class LogFragment : Fragment(R.layout.fragment_log) {

    private lateinit var rv: RecyclerView
    private lateinit var adapter: LogAdapter
    private lateinit var etFilter: TextInputEditText
    private lateinit var cbAutoScroll: CheckBox
    private lateinit var levelButtons: List<Pair<MaterialButton, LogBuffer.Level>>

    private var filterText: String = ""
    private val activeLevels: MutableSet<LogBuffer.Level> = mutableSetOf(
        LogBuffer.Level.DEBUG,
        LogBuffer.Level.INFO,
        LogBuffer.Level.WARN,
        LogBuffer.Level.ERROR
    )

    /** Posted to the view's handler because LogBuffer notifies on a logging thread. */
    private val liveListener = LogBuffer.Listener { entry ->
        val v = view ?: return@Listener
        v.post {
            // View may have been destroyed while the runnable was queued.
            val root = view ?: return@post
            if (matches(entry)) {
                adapter.append(entry)
                if (cbAutoScroll.isChecked) {
                    rv.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rv           = view.findViewById(R.id.rvLog)
        etFilter     = view.findViewById(R.id.etFilter)
        cbAutoScroll = view.findViewById(R.id.cbAutoScroll)

        levelButtons = listOf(
            view.findViewById<MaterialButton>(R.id.btnLevelDebug) to LogBuffer.Level.DEBUG,
            view.findViewById<MaterialButton>(R.id.btnLevelInfo)  to LogBuffer.Level.INFO,
            view.findViewById<MaterialButton>(R.id.btnLevelWarn)  to LogBuffer.Level.WARN,
            view.findViewById<MaterialButton>(R.id.btnLevelError) to LogBuffer.Level.ERROR
        )

        adapter = LogAdapter()
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // Initial snapshot
        adapter.submit(LogBuffer.snapshot().filter { matches(it) })
        rv.scrollToPosition((adapter.itemCount - 1).coerceAtLeast(0))

        etFilter.doAfterTextChanged { text ->
            filterText = text?.toString()?.trim().orEmpty()
            adapter.submit(LogBuffer.snapshot().filter { matches(it) })
            rv.scrollToPosition((adapter.itemCount - 1).coerceAtLeast(0))
        }

        setupLevelButtons()

        view.findViewById<MaterialButton>(R.id.btnClear).setOnClickListener {
            LogBuffer.clear()
            adapter.clear()
        }
        view.findViewById<MaterialButton>(R.id.btnShare).setOnClickListener {
            val dump = LogBuffer.dump()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "SMS Gateway log")
                putExtra(Intent.EXTRA_TEXT, dump)
            }
            startActivity(Intent.createChooser(intent, "Share log"))
        }
    }

    override fun onStart() {
        super.onStart()
        LogBuffer.addListener(liveListener)
    }

    override fun onStop() {
        LogBuffer.removeListener(liveListener)
        super.onStop()
    }

    private fun setupLevelButtons() {
        levelButtons.forEach { (button, level) ->
            button.setOnClickListener {
                toggleLevel(level)
                refreshLevelButtons()
            }
        }
        refreshLevelButtons()
    }

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

    private fun refreshLevelButtons() {
        val activeBg   = ContextCompat.getColor(requireContext(), R.color.level_active_bg)
        val activeFg   = ContextCompat.getColor(requireContext(), R.color.level_active_text)
        val inactiveBg = ContextCompat.getColor(requireContext(), R.color.level_inactive_bg)
        val inactiveFg = ContextCompat.getColor(requireContext(), R.color.level_inactive_text)
        val inactiveStroke = ContextCompat.getColor(requireContext(), R.color.level_inactive_stroke)

        levelButtons.forEach { (button, level) ->
            val active = activeLevels.contains(level)
            button.backgroundTintList =
                ColorStateList.valueOf(if (active) activeBg else inactiveBg)
            button.setTextColor(if (active) activeFg else inactiveFg)
            button.strokeColor =
                ColorStateList.valueOf(if (active) activeBg else inactiveStroke)
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