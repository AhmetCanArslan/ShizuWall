package com.arslan.shizuwall.services

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.arslan.shizuwall.R
import com.arslan.shizuwall.ui.MainActivity
import com.arslan.shizuwall.utils.FirewallUtils
import kotlinx.coroutines.*

class FloatingButtonService : Service() {

    companion object {
        const val CHANNEL_ID = "floating_button_channel"
        const val NOTIFICATION_ID = 4001
        const val KEY_FLOATING_BUTTON_ENABLED = "floating_button_enabled"

        const val KEY_FLOATING_IDLE_OPACITY = "floating_button_idle_opacity"
        const val KEY_FLOATING_SIZE = "floating_button_size"
        const val KEY_FLOATING_FADE_DELAY = "floating_button_fade_delay"
        const val KEY_FLOATING_EDGE_SNAP = "floating_button_edge_snap"
        const val KEY_FLOATING_DISABLE_DIM = "floating_button_disable_dim"

        const val DEFAULT_IDLE_OPACITY = 30
        const val DEFAULT_SIZE_DP = 56
        const val DEFAULT_FADE_DELAY = 3

        fun start(context: Context) {
            val intent = Intent(context, FloatingButtonService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingButtonService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var fabIcon: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private lateinit var sharedPreferences: SharedPreferences

    private var idleOpacityFraction = DEFAULT_IDLE_OPACITY / 100f
    private var sizePx = 0
    private var iconSizePx = 0
    private var fadeDelayMs = DEFAULT_FADE_DELAY * 1000L
    private var hideAtEdge = false
    private var disableDim = false

    private var snappedRight = true   
    private var isTucked = false      
    private var lastX = 0
    private var lastY = 0
    private var inactivityJob: Job? = null
    private var xAnimator: android.animation.ValueAnimator? = null

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            MainActivity.KEY_FIREWALL_ENABLED,
            MainActivity.KEY_ACTIVE_PACKAGES,
            MainActivity.KEY_FIREWALL_SAVED_ELAPSED -> updateFabAppearance()

            KEY_FLOATING_IDLE_OPACITY,
            KEY_FLOATING_SIZE,
            KEY_FLOATING_FADE_DELAY,
            KEY_FLOATING_EDGE_SNAP,
            KEY_FLOATING_DISABLE_DIM -> {

                loadFloatingSettings()
                applyFloatingSettingsLive()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        loadFloatingSettings()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefsListener)
        showFloatingButton()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun loadFloatingSettings() {
        val opacity = sharedPreferences.getInt(KEY_FLOATING_IDLE_OPACITY, DEFAULT_IDLE_OPACITY).coerceIn(0, 100)
        idleOpacityFraction = opacity / 100f
        val sizeDp = sharedPreferences.getInt(KEY_FLOATING_SIZE, DEFAULT_SIZE_DP).coerceIn(40, 96)
        sizePx = dp(sizeDp)
        iconSizePx = dp((sizeDp * 0.5f).toInt())
        val delay = sharedPreferences.getInt(KEY_FLOATING_FADE_DELAY, DEFAULT_FADE_DELAY).coerceIn(1, 30)
        fadeDelayMs = delay * 1000L
        hideAtEdge = sharedPreferences.getBoolean(KEY_FLOATING_EDGE_SNAP, false)
        disableDim = sharedPreferences.getBoolean(KEY_FLOATING_DISABLE_DIM, false)
    }

    override fun onDestroy() {
        try {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (_: Exception) {}
        removeFloatingButton()
        job.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.floating_button_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.floating_button_notification_channel_description)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.floating_button_notification_title))
            .setContentText(getString(R.string.notification_hold_to_disable))
            .setSmallIcon(R.drawable.ic_quick_tile)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showFloatingButton() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            android.widget.Toast.makeText(this, getString(R.string.overlay_permission_required), android.widget.Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_firewall_button, null)
        fabIcon = floatingView?.findViewById(R.id.fabFirewallIcon)
        fabIcon?.layoutParams = FrameLayout.LayoutParams(iconSizePx, iconSizePx, Gravity.CENTER)

        snappedRight = true
        isTucked = false

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,

            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = lastX
            y = lastY
        }
        layoutParams = params

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false
        var wasTucked = false

        resetInactivityTimer()

        floatingView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    inactivityJob?.cancel()
                    v.background?.alpha = 255
                    fabIcon?.animate()?.cancel()
                    fabIcon?.alpha = 1.0f
                    wasTucked = isTucked
                    if (hideAtEdge && isTucked) untuckFromEdge()

                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    v.alpha = 0.7f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!moved &&
                        (Math.abs(initialTouchX - event.rawX) > touchSlop ||
                            Math.abs(event.rawY - initialTouchY) > touchSlop)
                    ) {
                        moved = true

                        xAnimator?.cancel()
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                    }
                    if (moved) {
                        params.x = initialX + (initialTouchX - event.rawX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1.0f
                    if (moved) {
                        snapToEdge()
                    } else if (event.action == MotionEvent.ACTION_UP && !wasTucked) {

                        onFabClicked()
                    }
                    lastX = params.x
                    lastY = params.y
                    resetInactivityTimer()
                    true
                }
                else -> false
            }
        }

        updateFabAppearance()

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeFloatingButton() {
        inactivityJob?.cancel()
        xAnimator?.cancel()
        try {
            floatingView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        floatingView = null
        fabIcon = null
        layoutParams = null
    }

    private fun resetInactivityTimer() {
        inactivityJob?.cancel()
        floatingView?.background?.alpha = 255
        fabIcon?.animate()?.cancel()
        fabIcon?.alpha = 1.0f
        if (disableDim && !hideAtEdge) return
        inactivityJob = scope.launch {
            delay(fadeDelayMs)
            if (!disableDim) {
                val targetBgAlpha = (idleOpacityFraction * 255).toInt()
                val animator = android.animation.ValueAnimator.ofInt(255, targetBgAlpha)
                animator.duration = 300
                animator.addUpdateListener { animation ->
                    floatingView?.background?.alpha = animation.animatedValue as Int
                }
                animator.start()
                fabIcon?.animate()?.alpha(idleOpacityFraction)?.setDuration(300)?.start()
            }
            if (hideAtEdge) tuckToEdge()
        }
    }

    private fun applyFloatingSettingsLive() {
        val params = layoutParams ?: return
        params.width = sizePx
        params.height = sizePx
        fabIcon?.layoutParams = FrameLayout.LayoutParams(iconSizePx, iconSizePx, Gravity.CENTER)
        snapToEdge()
        resetInactivityTimer()
    }

    private fun animateWindowX(targetX: Int) {
        val params = layoutParams ?: return
        xAnimator?.cancel()
        if (params.x == targetX) return
        xAnimator = android.animation.ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 260
            interpolator = android.view.animation.DecelerateInterpolator(1.5f)
            addUpdateListener { animation ->
                val p = layoutParams ?: return@addUpdateListener
                p.x = animation.animatedValue as Int
                try { windowManager?.updateViewLayout(floatingView, p) } catch (_: Exception) {}
            }
            start()
        }
    }

    private fun snapToEdge() {
        val params = layoutParams ?: return
        val screenWidth = resources.displayMetrics.widthPixels
        val bw = params.width
        snappedRight = params.x <= (screenWidth - bw) / 2
        isTucked = false
        animateWindowX(if (snappedRight) 0 else screenWidth - bw)
    }

    private fun tuckToEdge() {
        val params = layoutParams ?: return
        val bw = params.width
        val screenWidth = resources.displayMetrics.widthPixels
        snappedRight = params.x <= (screenWidth - bw) / 2
        val tuck = bw / 2
        isTucked = true
        animateWindowX(if (snappedRight) -tuck else (screenWidth - bw + tuck))
    }

    private fun untuckFromEdge() {
        val params = layoutParams ?: return
        val screenWidth = resources.displayMetrics.widthPixels
        isTucked = false
        animateWindowX(if (snappedRight) 0 else screenWidth - params.width)
    }

    private fun updateFabAppearance() {
        val enabled = loadFirewallEnabled()
        fabIcon?.setImageResource(
            if (enabled) R.drawable.ic_quick_tile else R.drawable.ic_firewall_enabled 
        )
        val tint = if (enabled) {
            android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
        } else {
            android.content.res.ColorStateList.valueOf(0xFFBDBDBD.toInt())
        }
        fabIcon?.imageTintList = tint
    }

    private fun onFabClicked() {
        if (loadFirewallEnabled()) {
            if (!FirewallUtils.checkBackendReady(this)) return
            scope.launch {
                FirewallUtils.disable(this@FloatingButtonService, sharedPreferences)
                updateFabAppearance()
            }
            return
        }
        val targets = FirewallUtils.enableTargets(this, sharedPreferences) ?: return
        if (!FirewallUtils.checkBackendReady(this)) return
        scope.launch {
            FirewallUtils.enable(this@FloatingButtonService, sharedPreferences, targets)
            updateFabAppearance()
        }
    }

    private fun loadFirewallEnabled(): Boolean = FirewallUtils.loadFirewallEnabled(sharedPreferences)
}
