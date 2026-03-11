package com.arslan.shizuwall.ladb

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LadbLogStore {
    private const val PREF_NAME = "ladb_logs"
    private const val KEY_LOGS = "logs"
    private const val KEY_LOGGING_ENABLED = "logging_enabled"
    private const val MAX_LINES = 1000

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOGGING_ENABLED, false)
    }

    fun append(context: Context, message: String) {
        if (!isEnabled(context)) return

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val currentLogs = prefs.getString(KEY_LOGS, "") ?: ""
        val newLogs = currentLogs + "[$timestamp] $message\n"
        val lines = newLogs.split("\n")
        val trimmedLogs = if (lines.size > MAX_LINES) {
            lines.takeLast(MAX_LINES).joinToString("\n") + "\n"
        } else {
            newLogs
        }
        prefs.edit().putString(KEY_LOGS, trimmedLogs).apply()
    }
}
