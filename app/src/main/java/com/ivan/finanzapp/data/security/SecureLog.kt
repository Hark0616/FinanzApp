package com.ivan.finanzapp.data.security

import android.util.Log
import com.ivan.finanzapp.BuildConfig

object SecureLog {
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (!BuildConfig.SENSITIVE_LOGGING_ENABLED) return
        if (throwable == null) Log.d(tag, message) else Log.d(tag, message, throwable)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        if (!BuildConfig.SENSITIVE_LOGGING_ENABLED) return
        if (throwable == null) Log.i(tag, message) else Log.i(tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (!BuildConfig.SENSITIVE_LOGGING_ENABLED) return
        if (throwable == null) Log.w(tag, message) else Log.w(tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (!BuildConfig.SENSITIVE_LOGGING_ENABLED) return
        if (throwable == null) Log.e(tag, message) else Log.e(tag, message, throwable)
    }
}
