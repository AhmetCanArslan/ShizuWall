package com.arslan.shizuwall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class FirewallNotificationService : Service() {

    companion object {
        const val CHANNEL_ID = "FirewallServiceChannel"
        const val NOTIFICATION_ID = 1
        const val EXTRA_APP_COUNT = "extra_app_count"
        private const val EVENT_NOTIFICATION_ID = 2
        private const val EVENT_CHANNEL_ID = "FirewallEventChannel"

        fun startService(context: Context, appCount: Int) {
            val intent = Intent(context, FirewallNotificationService::class.java).apply {
                putExtra(EXTRA_APP_COUNT, appCount)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FirewallNotificationService::class.java)
            context.stopService(intent)
        }

        fun sendFirewallEnabledNotification(context: Context, appCount: Int) {
            createEventNotificationChannel(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, EVENT_CHANNEL_ID)
                .setContentTitle("Firewall Enabled")
                .setContentText("Firewall activated for $appCount app${if (appCount > 1) "s" else ""}")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            notificationManager.notify(EVENT_NOTIFICATION_ID, notification)
        }

        fun cancelFirewallEnabledNotification(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(EVENT_NOTIFICATION_ID)
        }

        private fun createEventNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    EVENT_CHANNEL_ID,
                    "Firewall Events",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for firewall enable/disable events"
                    setShowBadge(true)
                    enableLights(true)
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val appCount = intent?.getIntExtra(EXTRA_APP_COUNT, 0) ?: 0
        val notification = createNotification(appCount)
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Firewall Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows firewall status"
                setShowBadge(true)
                enableLights(true)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(appCount: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (appCount > 0) {
            "Firewall is active for $appCount app${if (appCount > 1) "s" else ""}"
        } else {
            "Firewall is active"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ShizuWall Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
