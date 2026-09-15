package com.arslan.shizuwall.receivers

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.arslan.shizuwall.FirewallMode
import com.arslan.shizuwall.firewall.FirewallTargets
import com.arslan.shizuwall.ui.MainActivity

class ScreenLockModeReceiver {

    companion object {
        private const val TAG = "ScreenLockMode"

        fun isDeviceLocked(context: Context): Boolean {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                ?: return false
            return km.isDeviceLocked
        }
    }

    fun handleAction(context: Context, action: String) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        when (action) {
            Intent.ACTION_SCREEN_OFF -> {
                if (!isScreenLockModeActive(prefs) || !isFirewallEnabled(prefs)) {
                    return
                }

                if (!isDeviceLocked(appContext)) {
                    Log.d(TAG, "Lock event ignored: device is not locked")
                    return
                }

                val csv = selectedPackagesCsv(prefs)
                if (csv.isEmpty()) {
                    Log.d(TAG, "Lock event ignored: selected apps are empty")
                    return
                }

                sendFirewallControlBroadcast(appContext, enabled = true, csv = csv)
                Log.d(TAG, "Lock confirmed: requested block for selected apps")
            }

            Intent.ACTION_USER_PRESENT -> {
                if (!isScreenLockModeActive(prefs) || !isFirewallEnabled(prefs)) {
                    return
                }

                val csv = activePackagesCsv(prefs)
                if (csv.isEmpty()) {
                    Log.d(TAG, "Unlock event ignored: no active blocked apps (interrupted or already clean)")
                    return
                }

                sendFirewallControlBroadcast(appContext, enabled = false, csv = csv)
                Log.d(TAG, "Unlock detected: requested unblock for selected apps")
            }
        }
    }

    private fun isScreenLockModeActive(prefs: android.content.SharedPreferences): Boolean {
        val mode = FirewallMode.fromName(
            prefs.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name)
        )
        return mode == FirewallMode.SCREEN_LOCK_MODE || mode == FirewallMode.HYBRID
    }

    private fun isFirewallEnabled(prefs: android.content.SharedPreferences): Boolean {
        return prefs.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
    }

    private fun activePackagesCsv(prefs: android.content.SharedPreferences): String =
        packagesCsv(prefs, MainActivity.KEY_ACTIVE_PACKAGES)

    private fun selectedPackagesCsv(prefs: android.content.SharedPreferences): String =
        packagesCsv(prefs, MainActivity.KEY_SELECTED_APPS)

    private fun packagesCsv(prefs: android.content.SharedPreferences, key: String): String {
        val packages = prefs.getStringSet(key, emptySet()) ?: emptySet()
        val screenLockOnly = FirewallMode.fromName(prefs.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name)) == FirewallMode.HYBRID
        val appModes = FirewallTargets.parseAppModes(prefs.getString(MainActivity.KEY_APP_MODES, "{}"))
        return packages
            .map { it.trim() }
            .filter { it.isNotEmpty() && (!screenLockOnly || appModes[it] == FirewallTargets.APP_MODE_SCREEN_LOCK) }
            .joinToString(",")
    }

    private fun sendFirewallControlBroadcast(context: Context, enabled: Boolean, csv: String) {
        val controlIntent = Intent(context, FirewallControlReceiver::class.java).apply {
            action = MainActivity.ACTION_FIREWALL_CONTROL
            putExtra(MainActivity.EXTRA_FIREWALL_ENABLED, enabled)
            putExtra(MainActivity.EXTRA_PACKAGES_CSV, csv)
            putExtra(FirewallControlReceiver.EXTRA_AUTOMATION_EVENT, true)
        }
        context.sendBroadcast(controlIntent)
    }
}
