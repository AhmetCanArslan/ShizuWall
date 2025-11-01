package com.arslan.shizuwall

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Background notification service removed.
 *
 * Kept as a small no-op stub to keep the codebase compile-safe and
 * to document that background/foreground activity has been removed.
 */
class FirewallNotificationService : Service() {

    companion object {
        /**
         * No-op startService replacement — background service removed.
         */
        fun startService(context: Context, appCount: Int) {
            // no-op
        }

        /**
         * No-op stopService replacement.
         */
        fun stopService(context: Context) {
            // no-op
        }

        fun sendFirewallEnabledNotification(context: Context, appCount: Int) {
            // no-op
        }

        fun cancelFirewallEnabledNotification(context: Context) {
            // no-op
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Service not used; return non-sticky to avoid being restarted.
        return START_NOT_STICKY
    }
}
