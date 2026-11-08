package com.arslan.shizuwall.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.arslan.shizuwall.R
import com.arslan.shizuwall.ui.MainActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    private const val API = "https://api.github.com/repos/ahmetcanarslan/shizuwall/releases/latest"
    private const val TIMEOUT_MS = 8000

    data class Release(val tag: String, val url: String)

    fun isNewer(remote: String, local: String): Boolean {
        val r = parts(remote)
        val l = parts(local)
        val diff = (0 until maxOf(r.size, l.size)).map { r.getOrElse(it) { 0 } - l.getOrElse(it) { 0 } }
        return diff.firstOrNull { it != 0 }?.let { it > 0 } ?: false
    }

    private fun parts(v: String) = v.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    fun fetchLatest(): Release? {
        val conn = URL(API).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            Release(json.getString("tag_name"), json.getString("html_url"))
        } finally {
            conn.disconnect()
        }
    }

    fun showDialog(context: Context, release: Release) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.update_available_title)
            .setMessage(context.getString(R.string.update_available_message, release.tag.removePrefix("v")))
            .setPositiveButton(R.string.update_download) { _, _ ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.url)))
            }
            .setNeutralButton(R.string.update_skip_version) { _, _ ->
                context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE).edit()
                    .putString(MainActivity.KEY_SKIPPED_UPDATE, release.tag).apply()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
