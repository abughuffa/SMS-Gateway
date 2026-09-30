package com.example.smsgateway.log

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object LogBuffer {

    enum class Level(val label: String) {
        DEBUG("D"), INFO("I"), WARN("W"), ERROR("E")
    }
    fun w(tag: String, msg: String, t: Throwable) =
        add(Level.WARN, tag, "$msg: ${t.javaClass.simpleName}: ${t.message ?: ""}")
    data class Entry(
        val ts: Long,
        val level: Level,
        val tag: String,
        val message: String
    ) {
        fun formattedTime(): String =
            TIME_FMT.format(Date(ts))
    }

    private const val MAX_ENTRIES = 2000

    private val entries = CopyOnWriteArrayList<Entry>()
    private val listeners = CopyOnWriteArrayList<Listener>()

    private val TIME_FMT =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun interface Listener {
        /** Called on the calling thread; UI listeners must marshal to the main thread themselves. */
        fun onEntry(entry: Entry)
    }

    /** Append a line. Bounded — drops the oldest when over capacity. */
    fun add(level: Level, tag: String, message: String) {
        val entry = Entry(System.currentTimeMillis(), level, tag, message)

        // Trim if over capacity. Doing this before add() keeps the list size bounded.
        while (entries.size >= MAX_ENTRIES) {
            entries.removeAt(0)
        }
        entries.add(entry)

        for (l in listeners) {
            try { l.onEntry(entry) } catch (_: Throwable) {}
        }
    }

    fun addListener(l: Listener)    { listeners.addIfAbsent(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    /** Snapshot of all current entries (oldest → newest). */
    fun snapshot(): List<Entry> = entries.toList()

    fun clear() {
        entries.clear()
    }

    // ── Convenience wrappers ────────────────────────────────────────
    fun d(tag: String, msg: String) = add(Level.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = add(Level.INFO,  tag, msg)
    fun w(tag: String, msg: String) = add(Level.WARN,  tag, msg)
    fun e(tag: String, msg: String) = add(Level.ERROR, tag, msg)

    fun e(tag: String, msg: String, t: Throwable) =
        add(Level.ERROR, tag, "$msg: ${t.javaClass.simpleName}: ${t.message ?: ""}")

    /** Render everything as a multi-line string, suitable for share/export. */
    fun dump(): String = buildString {
        for (e in entries) {
            append(e.formattedTime())
            append(' ')
            append(e.level.label)
            append('/')
            append(e.tag)
            append(": ")
            append(e.message)
            append('\n')
        }
    }
}
