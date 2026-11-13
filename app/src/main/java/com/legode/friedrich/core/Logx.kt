package com.legode.friedrich.core

import android.util.Log

/** Журнал, который не падает даже там, где android.util.Log недоступен (юнит-проверки логики). */
object Logx {
    private const val TAG = "Friedrich"

    fun d(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (_: Throwable) {
        }
    }

    fun w(msg: String, t: Throwable? = null) {
        try {
            if (t != null) Log.w(TAG, msg, t) else Log.w(TAG, msg)
        } catch (_: Throwable) {
        }
    }

    fun e(msg: String, t: Throwable? = null) {
        try {
            if (t != null) Log.e(TAG, msg, t) else Log.e(TAG, msg)
        } catch (_: Throwable) {
        }
    }
}
