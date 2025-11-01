package com.arslan.shizuwall

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * On BOOT_COMPLETED, check whether the firewall was enabled before reboot (using the saved elapsedRealtime).
 * If so, send a notification to the user telling them the firewall should be re-enabled after reboot.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "shizuwall_boot_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d(TAG, "onReceive action=$action")

        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.d(TAG, "Ignoring action: $action")
            return
        }

        // Try to read prefs from device-protected storage first (works during direct-boot),
        // fall back to regular context prefs.
        val prefs =
            try {
                val dpCtx = context.createDeviceProtectedStorageContext()
                dpCtx.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to access device-protected prefs, falling back to normal prefs", e)
                context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
            }

        val enabled = prefs.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
        val savedElapsed = prefs.getLong(MainActivity.KEY_FIREWALL_SAVED_ELAPSED, -1L)
        Log.d(TAG, "prefs: enabled=$enabled, savedElapsed=$savedElapsed")

        if (!enabled || savedElapsed == -1L) {
            Log.d(TAG, "No saved firewall state found; nothing to do.")
            return
        }

        val currentElapsed = SystemClock.elapsedRealtime()
        // A reboot occurred if currentElapsed < savedElapsed; in that case the saved flag represents state before reboot.
        if (currentElapsed < savedElapsed) {
            Log.d(TAG, "Detected reboot since firewall was enabled. Preparing notification.")
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

            val title = "Firewall was active before reboot"
            val text = "Firewall should be enabled again after rebooting. Tap to open the app."

            val notifBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            try {
                // Try to post the notification and log any security/permission failures
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notifBuilder.build())
                Log.d(TAG, "Posted boot notification (id=$NOTIFICATION_ID)")
            } catch (se: SecurityException) {
                Log.w(TAG, "Failed to post notification: missing permission or security error", se)
                // If notifications are blocked (Android 13+), the receiver cannot request permission.
                // Log informs you to grant POST_NOTIFICATIONS (adb or UI) to allow the notification.
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error while posting notification", e)
            }
        } else {
            Log.d(TAG, "No reboot detected (currentElapsed >= savedElapsed).")
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
            } else {
                Log.d(TAG, "Notification channel $CHANNEL_ID already exists")
            }
        }
    }
}
