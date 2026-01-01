package com.arslan.shizuwall.services

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.arslan.shizuwall.R
import com.arslan.shizuwall.receivers.NotificationActionReceiver
import com.arslan.shizuwall.ui.MainActivity

class AppMonitorService : Service() {

    private companion object {
        const val CHANNEL_ID_SILENT = "app_monitor_channel_silent"
        const val CHANNEL_ID_LOUD = "app_monitor_channel_loud"
        const val FOREGROUND_NOTIFICATION_ID = 2001
        const val APP_INSTALL_NOTIFICATION_ID_BASE = 3000
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_PACKAGE_ADDED) {
                val packageName = intent.data?.schemeSpecificPart ?: return
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (!isReplacing) {
                    showNewAppNotification(context, packageName)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
        
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
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

        return NotificationCompat.Builder(this, CHANNEL_ID_SILENT)
            .setContentTitle(getString(R.string.app_monitor_service))
            .setContentText(getString(R.string.app_monitor_service_description))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
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

        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val isFirewallEnabled = prefs.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)

        val actionText = if (isFirewallEnabled) {
            context.getString(R.string.firewall_app)
        } else {
            context.getString(R.string.add_to_selected_list)
        }

        val notificationId = APP_INSTALL_NOTIFICATION_ID_BASE + packageName.hashCode()

        val actionIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = if (isFirewallEnabled) NotificationActionReceiver.ACTION_FIREWALL_APP else NotificationActionReceiver.ACTION_ADD_TO_LIST
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
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(drawableToBitmap(appIcon))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, actionText, pendingActionIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) return drawable.bitmap
        
        val bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        }
        
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
