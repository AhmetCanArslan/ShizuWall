package com.arslan.shizuwall.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arslan.shizuwall.FirewallMode
import com.arslan.shizuwall.R
import com.arslan.shizuwall.receivers.ScreenLockModeReceiver
import com.arslan.shizuwall.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ScreenLockMonitorService : Service() {

    companion object {
        private const val TAG = "ScreenLockMonitorSvc"
        private const val CHANNEL_ID = "screen_lock_mode_channel"
        private const val NOTIFICATION_ID = 4003
        private const val UNLOCK_CHECK_INTERVAL_MS = 500L
        private const val UNLOCK_CHECK_MAX_MS = 30000L

        fun sync(context: Context) {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

            val enabled = prefs.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
            val mode = FirewallMode.fromName(
                prefs.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name)
            )
            val shouldRun = enabled && mode == FirewallMode.SCREEN_LOCK_MODE

            val serviceIntent = Intent(appContext, ScreenLockMonitorService::class.java)
            if (shouldRun) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(serviceIntent)
                } else {
                    appContext.startService(serviceIntent)
                }
            } else {
                appContext.stopService(serviceIntent)
            }
        }
    }

    private lateinit var prefs: SharedPreferences
    private var receiverRegistered = false
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)
    private var unlockCheckJob: Job? = null
    private var pendingLockJob: Job? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == MainActivity.KEY_FIREWALL_ENABLED || key == MainActivity.KEY_FIREWALL_MODE) {
            if (!isModeActive()) {
                stopSelf()
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val action = intent?.action ?: return
            if (!isModeActive()) return

            when (action) {
                Intent.ACTION_SCREEN_OFF -> {
                    pendingLockJob?.cancel()
                    unlockCheckJob?.cancel()
                    startLockDelay()
                }

                Intent.ACTION_USER_PRESENT -> {
                    pendingLockJob?.cancel()
                    unlockCheckJob?.cancel()
                    dispatchToReceiver(Intent.ACTION_USER_PRESENT)
                }

                Intent.ACTION_SCREEN_ON -> {
                    pendingLockJob?.cancel()
                    // Some OEM builds may skip USER_PRESENT; poll keyguard state after SCREEN_ON.
                    startUnlockPolling()
                }
            }
        }
    }

    private fun startLockDelay() {
        pendingLockJob?.cancel()
        pendingLockJob = serviceScope.launch {
            if (!isModeActive()) return@launch

            val delaySeconds = prefs
                .getInt(
                    MainActivity.KEY_SCREEN_LOCK_DELAY_SECONDS,
                    MainActivity.DEFAULT_SCREEN_LOCK_DELAY_SECONDS
                )
                .coerceIn(2, 10)

            delay(delaySeconds * 1000L)

            if (!isModeActive()) return@launch
            if (ScreenLockModeReceiver.isDeviceLocked(this@ScreenLockMonitorService)) {
                dispatchToReceiver(Intent.ACTION_SCREEN_OFF)
            }
        }
    }

    private fun startUnlockPolling() {
        unlockCheckJob?.cancel()
        unlockCheckJob = serviceScope.launch {
            var waited = 0L
            while (waited <= UNLOCK_CHECK_MAX_MS) {
                if (!isModeActive()) return@launch

                if (!ScreenLockModeReceiver.isDeviceLocked(this@ScreenLockMonitorService)) {
                    dispatchToReceiver(Intent.ACTION_USER_PRESENT)
                    return@launch
                }

                delay(UNLOCK_CHECK_INTERVAL_MS)
                waited += UNLOCK_CHECK_INTERVAL_MS
            }
        }
    }

    private fun dispatchToReceiver(action: String) {
        try {
            ScreenLockModeReceiver().handleAction(this@ScreenLockMonitorService, action)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to process screen action=$action", t)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        receiverRegistered = true

        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        Log.d(TAG, "Screen lock monitor started")

        if (!isModeActive()) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isModeActive()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        pendingLockJob?.cancel()
        unlockCheckJob?.cancel()
        serviceScope.cancel()
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        } catch (_: Exception) {
        }
        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: Exception) {
            }
        }
        Log.d(TAG, "Screen lock monitor stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun isModeActive(): Boolean {
        val enabled = prefs.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
        val mode = FirewallMode.fromName(
            prefs.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name)
        )
        return enabled && mode == FirewallMode.SCREEN_LOCK_MODE
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.screen_lock_mode_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.screen_lock_mode_notification_channel_description)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.screen_lock_mode_notification_title))
            .setContentText(getString(R.string.screen_lock_mode_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
