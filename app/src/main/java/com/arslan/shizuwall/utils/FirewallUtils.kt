package com.arslan.shizuwall.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import com.arslan.shizuwall.R
import com.arslan.shizuwall.daemon.PersistentDaemonManager
import com.arslan.shizuwall.shell.RootShellExecutor
import com.arslan.shizuwall.ui.MainActivity
import rikka.shizuku.Shizuku

object FirewallUtils {

    fun loadFirewallEnabled(prefs: SharedPreferences): Boolean {
        val enabled = prefs.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
        if (!enabled) return false
        val savedElapsed = prefs.getLong(MainActivity.KEY_FIREWALL_SAVED_ELAPSED, -1L)
        if (savedElapsed == -1L) return false
        return SystemClock.elapsedRealtime() >= savedElapsed
    }

    fun loadSelectedApps(context: Context, prefs: SharedPreferences): List<String> {
        val selfPkg = context.packageName
        return prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())
            ?.filterNot { ShizukuPackageResolver.isShizukuPackage(context, it) || it == selfPkg }
            ?.toList() ?: emptyList()
    }

    fun loadActivePackages(prefs: SharedPreferences): Set<String> {
        return prefs.getStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet()) ?: emptySet()
    }

    fun saveFirewallEnabled(context: Context, prefs: SharedPreferences, enabled: Boolean) {
        val elapsed = if (enabled) SystemClock.elapsedRealtime() else -1L
        prefs.edit()
            .putBoolean(MainActivity.KEY_FIREWALL_ENABLED, enabled)
            .putLong(MainActivity.KEY_FIREWALL_SAVED_ELAPSED, elapsed)
            .apply()

        val intent = android.content.Intent(context, com.arslan.shizuwall.widgets.FirewallWidgetProvider::class.java)
        intent.action = MainActivity.ACTION_FIREWALL_STATE_CHANGED
        context.sendBroadcast(intent)

        com.arslan.shizuwall.services.ScreenLockMonitorService.sync(context)
    }

    fun saveActivePackages(prefs: SharedPreferences, packages: Set<String>) {
        prefs.edit()
            .putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, packages)
            .apply()
    }

    enum class BackendCheckResult {
        READY, NOT_READY, ERROR
    }

    data class BackendCheckOutcome(
        val result: BackendCheckResult,
        val showToast: Boolean = true
    )

    fun checkBackendReady(context: Context, showToast: Boolean = true): Boolean {
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val mode = prefs.getString(MainActivity.KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"
        return when (mode) {
            "ROOT" -> {
                if (RootShellExecutor.hasRootAccess()) {
                    true
                } else {
                    if (showToast) Toast.makeText(context, context.getString(R.string.root_not_found_message), Toast.LENGTH_SHORT).show()
                    false
                }
            }
            "LADB" -> {
                val dm = PersistentDaemonManager(context)
                if (dm.isDaemonRunning()) {
                    true
                } else {
                    if (showToast) Toast.makeText(context, context.getString(R.string.daemon_not_running), Toast.LENGTH_SHORT).show()
                    false
                }
            }
            else -> {
                val binderAlive = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
                if (!binderAlive) {
                    if (showToast) Toast.makeText(context, context.getString(R.string.shizuku_not_running), Toast.LENGTH_SHORT).show()
                    false
                } else {
                    val granted = try {
                        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    } catch (_: Throwable) { false }
                    if (!granted) {
                        if (showToast) Toast.makeText(context, context.getString(R.string.shizuku_permission_required), Toast.LENGTH_SHORT).show()
                        false
                    } else {
                        true
                    }
                }
            }
        }
    }
}
