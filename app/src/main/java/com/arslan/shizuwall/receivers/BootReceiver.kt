package com.arslan.shizuwall.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.os.UserManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.arslan.shizuwall.R
import com.arslan.shizuwall.WorkingMode
import com.arslan.shizuwall.services.AppMonitorService
import com.arslan.shizuwall.shell.RootShellExecutor
import com.arslan.shizuwall.ui.MainActivity
import com.arslan.shizuwall.utils.ShizukuPackageResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "shizuwall_boot_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "BootReceiver"
        private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z0-9_.]+$")
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d(TAG, "onReceive action=$action")

        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.d(TAG, "Ignoring action: $action")
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleBootEvent(appContext, action)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleBootEvent(context: Context, action: String?) {
        val dpPrefs = getDeviceProtectedPrefs(context)
        val normalPrefs = getCredentialProtectedPrefsOrNull(context)

        val enabled = readBoolean(dpPrefs, normalPrefs, MainActivity.KEY_FIREWALL_ENABLED, false)
        val savedElapsed = readLong(dpPrefs, normalPrefs, MainActivity.KEY_FIREWALL_SAVED_ELAPSED, -1L)
        val appMonitorEnabled = readBoolean(dpPrefs, normalPrefs, MainActivity.KEY_APP_MONITOR_ENABLED, false)
        val floatingButtonEnabled = readBoolean(dpPrefs, normalPrefs, com.arslan.shizuwall.services.FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, false)
        Log.d(TAG, "prefs: enabled=$enabled, savedElapsed=$savedElapsed, appMonitorEnabled=$appMonitorEnabled, floatingButtonEnabled=$floatingButtonEnabled")

        if (appMonitorEnabled) {
            val monitorIntent = Intent(context, AppMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(monitorIntent)
            } else {
                context.startService(monitorIntent)
            }
        }

        if (floatingButtonEnabled) {
            com.arslan.shizuwall.services.FloatingButtonService.start(context)
        }

        val rebootDetected = enabled && savedElapsed > 0L && SystemClock.elapsedRealtime() < savedElapsed
        if (!rebootDetected) {
            Log.d(TAG, "No reboot detected (currentElapsed >= savedElapsed)")
            return
        }

        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.d(TAG, "Deferring reboot state handling until BOOT_COMPLETED")
            return
        }

        val workingMode = WorkingMode.fromName(
            readString(dpPrefs, normalPrefs, MainActivity.KEY_WORKING_MODE, WorkingMode.SHIZUKU.name)
        )
        val rootReapplyEnabled = readBoolean(
            dpPrefs,
            normalPrefs,
            MainActivity.KEY_APPLY_ROOT_RULES_AFTER_REBOOT,
            false
        )

        val shouldAttemptRootReapply =
            workingMode == WorkingMode.ROOT &&
                rootReapplyEnabled

        if (shouldAttemptRootReapply) {
            val activePackages = loadActivePackages(context, dpPrefs, normalPrefs, context.packageName)
            val reapplied = reapplyRootFirewallRules(activePackages)
            if (reapplied) {
                val elapsed = SystemClock.elapsedRealtime()
                updateFirewallStateAfterReapply(dpPrefs, elapsed)
                updateFirewallStateAfterReapply(normalPrefs, elapsed)
                Log.d(TAG, "Successfully re-applied firewall rules after reboot via root")
                return
            }
            Log.w(TAG, "Root re-apply failed, clearing persisted firewall state")
        }

        clearFirewallState(dpPrefs)
        clearFirewallState(normalPrefs)

        val messageRes = if (shouldAttemptRootReapply) {
            R.string.firewall_reboot_apply_failed_message
        } else {
            R.string.firewall_reboot_message
        }
        postBootNotification(context, messageRes)
    }

    private fun getDeviceProtectedPrefs(context: Context): SharedPreferences {
        return try {
            val dpCtx = context.createDeviceProtectedStorageContext()
            dpCtx.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to access device-protected prefs, falling back to normal prefs", e)
            context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun getCredentialProtectedPrefsOrNull(context: Context): SharedPreferences? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val userManager = context.getSystemService(UserManager::class.java)
            if (userManager != null && !userManager.isUserUnlocked) {
                Log.d(TAG, "Credential-protected prefs unavailable until user unlock")
                return null
            }
        }

        return try {
            context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Credential-protected prefs unavailable", e)
            null
        }
    }

    private fun readBoolean(
        primary: SharedPreferences,
        fallback: SharedPreferences?,
        key: String,
        defaultValue: Boolean
    ): Boolean {
        return if (primary.contains(key)) primary.getBoolean(key, defaultValue)
        else fallback?.getBoolean(key, defaultValue) ?: defaultValue
    }

    private fun readLong(
        primary: SharedPreferences,
        fallback: SharedPreferences?,
        key: String,
        defaultValue: Long
    ): Long {
        return if (primary.contains(key)) primary.getLong(key, defaultValue)
        else fallback?.getLong(key, defaultValue) ?: defaultValue
    }

    private fun readString(
        primary: SharedPreferences,
        fallback: SharedPreferences?,
        key: String,
        defaultValue: String
    ): String {
        return if (primary.contains(key)) primary.getString(key, defaultValue) ?: defaultValue
        else fallback?.getString(key, defaultValue) ?: defaultValue
    }

    private fun loadActivePackages(
        context: Context,
        primary: SharedPreferences,
        fallback: SharedPreferences?,
        selfPackage: String
    ): List<String> {
        val active = if (primary.contains(MainActivity.KEY_ACTIVE_PACKAGES)) {
            primary.getStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet())
        } else {
            fallback?.getStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet())
        } ?: emptySet()

        return active
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it == selfPackage }
            .filterNot { ShizukuPackageResolver.isShizukuPackage(context, it) }
            .filter { PACKAGE_NAME_REGEX.matches(it) }
            .distinct()
    }

    private suspend fun reapplyRootFirewallRules(activePackages: List<String>): Boolean {
        if (!RootShellExecutor.hasRootAccess()) {
            Log.w(TAG, "Root access not available during reboot re-apply")
            return false
        }

        val rootExecutor = RootShellExecutor()
        val chainEnabled = rootExecutor.exec("cmd connectivity set-chain3-enabled true").success
        if (!chainEnabled) {
            Log.w(TAG, "Failed to enable chain3 during reboot re-apply")
            return false
        }

        val applied = mutableListOf<String>()
        for (pkg in activePackages) {
            val success = rootExecutor.exec("cmd connectivity set-package-networking-enabled false $pkg").success
            if (success) {
                applied.add(pkg)
                continue
            }

            rootExecutor.exec("cmd connectivity set-chain3-enabled false")
            Log.w(TAG, "Failed to apply package rule during reboot re-apply: $pkg")
            return false
        }

        return true
    }

    private fun updateFirewallStateAfterReapply(prefs: SharedPreferences?, elapsed: Long) {
        prefs ?: return
        prefs.edit()
            .putBoolean(MainActivity.KEY_FIREWALL_ENABLED, true)
            .putLong(MainActivity.KEY_FIREWALL_SAVED_ELAPSED, elapsed)
            .putLong(MainActivity.KEY_FIREWALL_UPDATE_TS, System.currentTimeMillis())
            .apply()
    }

    private fun clearFirewallState(prefs: SharedPreferences?) {
        prefs ?: return
        prefs.edit().apply {
            remove(MainActivity.KEY_FIREWALL_ENABLED)
            remove(MainActivity.KEY_FIREWALL_SAVED_ELAPSED)
            remove(MainActivity.KEY_ACTIVE_PACKAGES)
            apply()
        }
    }

    private fun postBootNotification(context: Context, messageRes: Int) {
        createChannelIfNeeded(context)
        val intentToOpen = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intentToOpen,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val title = context.getString(R.string.firewall_reboot_title)
        val text = context.getString(messageRes)

        val notifBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notifBuilder.build())
            Log.d(TAG, "Posted boot notification (id=$NOTIFICATION_ID)")
        } catch (se: SecurityException) {
            Log.w(TAG, "Failed to post notification: missing permission or security error", se)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while posting notification", e)
        }
    }

    private fun createChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "ShizuWall boot notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications about firewall status after device reboot"
                }
                nm.createNotificationChannel(channel)
                Log.d(TAG, "Created notification channel $CHANNEL_ID")
            }
        }
    }
}
