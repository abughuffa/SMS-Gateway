package com.example.smsgateway.log

import android.util.Log as AndroidLog

/**
 * App-wide log facade: writes to logcat AND the in-memory LogBuffer.
 * Use this instead of android.util.Log directly.
 */
object Log {

    fun i(tag: String, msg: String) {
        AndroidLog.i(tag, msg)
        LogBuffer.i(tag, msg)
    }

    fun w(tag: String, msg: String, t: Throwable) {
        AndroidLog.w(tag, msg, t)
        LogBuffer.w(tag, msg, t)
    }

    fun w(tag: String, msg: String) {
        AndroidLog.w(tag, msg)
        LogBuffer.w(tag, msg)
    }

    fun e(tag: String, msg: String, t: Throwable) {
        AndroidLog.e(tag, msg, t)
        LogBuffer.e(tag, msg, t)
    }
}