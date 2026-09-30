package com.example.smsgateway.log

import timber.log.Timber

class BufferTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val level = when (priority) {
            android.util.Log.DEBUG   -> LogBuffer.Level.DEBUG
            android.util.Log.INFO    -> LogBuffer.Level.INFO
            android.util.Log.WARN    -> LogBuffer.Level.WARN
            android.util.Log.ERROR,
            android.util.Log.ASSERT  -> LogBuffer.Level.ERROR
            else                     -> LogBuffer.Level.DEBUG
        }
        LogBuffer.add(level, tag ?: "App", message)
    }
}
