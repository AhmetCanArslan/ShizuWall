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
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.arslan.shizuwall.FirewallMode
import com.arslan.shizuwall.R
import com.arslan.shizuwall.ui.MainActivity

class ForegroundFirewallIndicatorService : Service() {

    companion object {
        const val CHANNEL_ID = "foreground_firewall_indicator_channel"
        const val NOTIFICATION_ID = 4010

        fun start(context: Context) {
            val intent = Intent(context, ForegroundFirewallIndicatorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ForegroundFirewallIndicatorService::class.java))
        }
    }

    private lateinit var prefs: SharedPreferences
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var indicatorDot: View? = null
    private var currentForegroundPackage: String? = null
    private var lastScreenWidth = 0
    private var lastScreenHeight = 0

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == MainActivity.KEY_FIREWALL_ENABLED ||
            key == MainActivity.KEY_SELECTED_APPS ||
            key == MainActivity.KEY_FIREWALL_INDICATOR_X ||
            key == MainActivity.KEY_FIREWALL_INDICATOR_Y ||
            key == MainActivity.KEY_FIREWALL_INDICATOR_SIZE ||
            key == MainActivity.KEY_LAST_FOREGROUND_APP
        ) {
            applyOverlayPositionAndSize()
            updateIndicatorState()
        }
    }

    private val foregroundReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action != ForegroundDetectionService.ACTION_FOREGROUND_APP_CHANGED) return
            val pkg = intent.getStringExtra(ForegroundDetectionService.EXTRA_PACKAGE_NAME)
            currentForegroundPackage = pkg?.takeIf { it.isNotBlank() }
            if (!currentForegroundPackage.isNullOrEmpty()) {
                prefs.edit().putString(MainActivity.KEY_LAST_FOREGROUND_APP, currentForegroundPackage).apply()
            }
            updateIndicatorState()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        val metrics = resources.displayMetrics
        lastScreenWidth = metrics.widthPixels
        lastScreenHeight = metrics.heightPixels

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.firewall_indicator_overlay_permission_required, Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        currentForegroundPackage = prefs.getString(MainActivity.KEY_LAST_FOREGROUND_APP, null)

        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        val filter = IntentFilter(ForegroundDetectionService.ACTION_FOREGROUND_APP_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(foregroundReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(foregroundReceiver, filter)
        }

        showOverlay()
        updateIndicatorState()
    }

    override fun onDestroy() {
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(foregroundReceiver)
        } catch (_: Exception) {
        }
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.firewall_indicator_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.firewall_indicator_notification_channel_description)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.firewall_indicator_notification_title))
            .setContentText(getString(R.string.firewall_indicator_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.overlay_firewall_indicator, null)
        indicatorDot = floatingView?.findViewById(R.id.firewallIndicatorDot)

        val params = createLayoutParams()
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        floatingView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    v.alpha = 0.8f
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop) {
                        moved = true
                    }
                    if (moved) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(floatingView, params)
                        prefs.edit()
                            .putInt(MainActivity.KEY_FIREWALL_INDICATOR_X, params.x)
                            .putInt(MainActivity.KEY_FIREWALL_INDICATOR_Y, params.y)
                            .apply()
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1.0f
                    true
                }

                else -> false
            }
        }

        try {
            windowManager?.addView(floatingView, params)
            applyOverlayPositionAndSize()
        } catch (_: Exception) {
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val sizePx = prefs.getInt(MainActivity.KEY_FIREWALL_INDICATOR_SIZE, 42)
        return WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(MainActivity.KEY_FIREWALL_INDICATOR_X, 24)
            y = prefs.getInt(MainActivity.KEY_FIREWALL_INDICATOR_Y, 120)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun applyOverlayPositionAndSize() {
        val view = floatingView ?: return
        val wm = windowManager ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val sizePx = prefs.getInt(MainActivity.KEY_FIREWALL_INDICATOR_SIZE, 42).coerceIn(24, 180)
        params.width = sizePx
        params.height = sizePx
        params.x = prefs.getInt(MainActivity.KEY_FIREWALL_INDICATOR_X, 24).coerceIn(-600, 3000)
        params.y = prefs.getInt(MainActivity.KEY_FIREWALL_INDICATOR_Y, 120).coerceIn(-600, 4000)
        try {
            wm.updateViewLayout(view, params)
        } catch (_: Exception) {
        }
    }

    private fun removeOverlay() {
        try {
            floatingView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        floatingView = null
        indicatorDot = null
    }

    private fun updateIndicatorState() {
        val dot = indicatorDot ?: return

        if (!ForegroundDetectionService.isServiceEnabled(this)) {
            tintDot(dot, 0xFF9E9E9E.toInt())
            return
        }

        val firewallEnabled = prefs.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
        if (!firewallEnabled) {
            tintDot(dot, 0xFF9E9E9E.toInt())
            return
        }

        val firewallMode = FirewallMode.fromName(
            prefs.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name)
        )
        if (firewallMode == FirewallMode.SMART_FOREGROUND) {
            // In Smart Foreground mode, the active foreground app is always allowed.
            tintDot(dot, 0xFF4CAF50.toInt())
            return
        }

        val foregroundPkg = prefs.getString(MainActivity.KEY_LAST_FOREGROUND_APP, null)

        if (foregroundPkg.isNullOrBlank()) {
            tintDot(dot, 0xFF9E9E9E.toInt())
            return
        }

        val selectedApps = prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        val isFirewalled = selectedApps.contains(foregroundPkg)

        if (isFirewalled) {
            tintDot(dot, 0xFFF44336.toInt()) // Red when it's a firewalled (selected) app
        } else {
            tintDot(dot, 0xFF4CAF50.toInt()) // Green when it's not a firewalled app
        }
    }

    private fun tintDot(dot: View, color: Int) {
        val bg: Drawable = dot.background ?: return
        bg.mutate().setTint(color)
        dot.backgroundTintList = ColorStateList.valueOf(color)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val metrics = resources.displayMetrics
        val newWidth = metrics.widthPixels
        val newHeight = metrics.heightPixels

        if (newWidth != lastScreenWidth || newHeight != lastScreenHeight) {
            val sizePx = prefs.getInt(MainActivity.KEY_FIREWALL_INDICATOR_SIZE, 42)
            var currentX = prefs.getInt(MainActivity.KEY_FIREWALL_INDICATOR_X, 24)
            var currentY = prefs.getInt(MainActivity.KEY_FIREWALL_INDICATOR_Y, 120)

            val isRight = currentX > lastScreenWidth / 2
            val isBottom = currentY > lastScreenHeight / 2

            if (isRight) {
                val distanceToRight = lastScreenWidth - currentX - sizePx
                currentX = newWidth - distanceToRight - sizePx
            }
            if (isBottom) {
                val distanceToBottom = lastScreenHeight - currentY - sizePx
                currentY = newHeight - distanceToBottom - sizePx
            }

            prefs.edit()
                .putInt(MainActivity.KEY_FIREWALL_INDICATOR_X, currentX)
                .putInt(MainActivity.KEY_FIREWALL_INDICATOR_Y, currentY)
                .apply()

            lastScreenWidth = newWidth
            lastScreenHeight = newHeight
        }
    }
}
