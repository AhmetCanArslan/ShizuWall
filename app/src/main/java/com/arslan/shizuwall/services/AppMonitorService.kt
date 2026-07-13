package com.arslan.shizuwall.services

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.arslan.shizuwall.FirewallMode
import com.arslan.shizuwall.R
import com.arslan.shizuwall.receivers.FirewallControlReceiver
import com.arslan.shizuwall.receivers.NotificationActionReceiver
import com.arslan.shizuwall.ui.MainActivity
import com.arslan.shizuwall.utils.UiUtils

class AppMonitorService : Service() {

    private companion object {
        const val CHANNEL_ID_SILENT = "app_monitor_channel_silent"
        const val CHANNEL_ID_LOUD = "app_monitor_channel_loud"
        const val FOREGROUND_NOTIFICATION_ID = 2001
        const val APP_INSTALL_NOTIFICATION_ID_BASE = 3000
    }

    private lateinit var prefs: SharedPreferences

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == MainActivity.KEY_FIREWALL_ENABLED ||
            key == MainActivity.KEY_SHOW_FIREWALL_STATUS_NOTIFICATION
        ) {
            updateForegroundNotification()
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_PACKAGE_ADDED) {
                val packageName = intent.data?.schemeSpecificPart ?: return
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                val includeRestored = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                    .getBoolean(MainActivity.KEY_AUTO_FIREWALL_INCLUDE_RESTORED, false)
                if (!isReplacing || includeRestored) {
                    showNewAppNotification(context, packageName)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
        }

        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(packageReceiver)
        } catch (_: Exception) {
        }
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Silent channel for foreground service
            val silentName = getString(R.string.app_monitor_notification_channel_name)
            val silentDesc = getString(R.string.app_monitor_notification_channel_description)
            val silentChannel = NotificationChannel(CHANNEL_ID_SILENT, silentName, NotificationManager.IMPORTANCE_LOW).apply {
                description = silentDesc
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(silentChannel)

            // Loud channel for app installations
            val loudName = getString(R.string.app_installed_notification_channel_name)
            val loudDesc = getString(R.string.app_installed_notification_channel_description)
            val loudChannel = NotificationChannel(CHANNEL_ID_LOUD, loudName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = loudDesc
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(loudChannel)
        }
    }

    private fun createForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val showFirewallStatus = prefs.getBoolean(MainActivity.KEY_SHOW_FIREWALL_STATUS_NOTIFICATION, false)
        val (title, text) = if (showFirewallStatus) {
            val firewallEnabled = prefs.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
            if (firewallEnabled) {
                getString(R.string.firewall_status_notification_enabled_title) to
                    getString(R.string.firewall_status_notification_enabled_text)
            } else {
                getString(R.string.firewall_status_notification_disabled_title) to
                    getString(R.string.firewall_status_notification_disabled_text)
            }
        } else {
            getString(R.string.app_monitor_service) to getString(R.string.app_monitor_service_description)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID_SILENT)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_quick_tile)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateForegroundNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
        } catch (_: Exception) {
        }
    }

    private fun showNewAppNotification(context: Context, packageName: String) {
        val pm = context.packageManager
        val appInfo = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return
        }
        val appName = pm.getApplicationLabel(appInfo).toString()
        val appIcon = pm.getApplicationIcon(appInfo)

        if (pm.checkPermission(Manifest.permission.INTERNET, packageName) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean(MainActivity.KEY_APP_MONITOR_ENABLED, false)
        val isFirewallEnabled = prefs.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
        val firewallMode = FirewallMode.fromName(prefs.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name))
        val autoFirewallEnabled = prefs.getBoolean(MainActivity.KEY_AUTO_FIREWALL_NEW_APPS, false)

        // Auto-block path 1: Whitelist mode — new apps are not in the whitelist so block them.
        // Auto-block path 2: "Auto-firewall new apps" toggle — add to selected list and block.
        val wasAutoFirewalled = isFirewallEnabled && autoFirewallEnabled && firewallMode != FirewallMode.WHITELIST
        if (isFirewallEnabled && firewallMode == FirewallMode.WHITELIST) {
            val blockIntent = Intent(context, FirewallControlReceiver::class.java).apply {
                action = MainActivity.ACTION_FIREWALL_CONTROL
                putExtra(MainActivity.EXTRA_FIREWALL_ENABLED, true)
                putExtra(MainActivity.EXTRA_PACKAGES_CSV, packageName)
            }
            context.sendBroadcast(blockIntent)
        } else if (wasAutoFirewalled) {
            val selected = prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
            if (selected.add(packageName)) {
                prefs.edit()
                    .putStringSet(MainActivity.KEY_SELECTED_APPS, selected)
                    .putInt(MainActivity.KEY_SELECTED_COUNT, selected.size)
                    .apply()
            }
            val blockIntent = Intent(context, FirewallControlReceiver::class.java).apply {
                action = MainActivity.ACTION_FIREWALL_CONTROL
                putExtra(MainActivity.EXTRA_FIREWALL_ENABLED, true)
                putExtra(MainActivity.EXTRA_PACKAGES_CSV, packageName)
            }
            context.sendBroadcast(blockIntent)
        }

        // Show notification only when the notifications toggle is on, or when the app was
        // auto-firewalled (so the user can tap "Allow" to undo).
        if (!notificationsEnabled && !wasAutoFirewalled) return

        val (actionText, action) = if (isFirewallEnabled) {
            when {
                wasAutoFirewalled ->
                    // Auto-firewall added the app to selected list; "Allow" must undo both.
                    context.getString(R.string.allow_app) to NotificationActionReceiver.ACTION_ALLOW_AND_UNSELECT
                firewallMode == FirewallMode.WHITELIST ->
                    // Whitelist mode: "Allow" adds to whitelist (keeps in selected list).
                    context.getString(R.string.allow_app) to NotificationActionReceiver.ACTION_WHITELIST_APP
                else ->
                    context.getString(R.string.firewall_app) to NotificationActionReceiver.ACTION_FIREWALL_APP
            }
        } else {
            context.getString(R.string.add_to_selected_list) to NotificationActionReceiver.ACTION_ADD_TO_LIST
        }

        val notificationId = APP_INSTALL_NOTIFICATION_ID_BASE + packageName.hashCode()

        val actionIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(NotificationActionReceiver.EXTRA_PACKAGE_NAME, packageName)
            putExtra("notification_id", notificationId)
        }

        val pendingActionIntent = PendingIntent.getBroadcast(
            context,
            packageName.hashCode(),
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_LOUD)
            .setContentTitle(context.getString(R.string.new_app_installed, appName))
            .setContentText(packageName)
            .setSmallIcon(R.drawable.ic_quick_tile)
            .setLargeIcon(UiUtils.drawableToBitmap(appIcon))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, actionText, pendingActionIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }
}
