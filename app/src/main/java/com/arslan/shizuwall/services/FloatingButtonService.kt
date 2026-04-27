package com.arslan.shizuwall.services

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.arslan.shizuwall.FirewallMode
import com.arslan.shizuwall.R
import com.arslan.shizuwall.receivers.ScreenLockModeReceiver
import com.arslan.shizuwall.shell.ShellExecutorBlocking
import com.arslan.shizuwall.ui.MainActivity
import com.arslan.shizuwall.utils.FirewallUtils
import com.arslan.shizuwall.utils.ShizukuPackageResolver
import com.arslan.shizuwall.utils.WhitelistFilter
import com.arslan.shizuwall.widgets.FirewallWidgetProvider
import kotlinx.coroutines.*

class FloatingButtonService : Service() {

    companion object {
        const val CHANNEL_ID = "floating_button_channel"
        const val NOTIFICATION_ID = 4001
        const val KEY_FLOATING_BUTTON_ENABLED = "floating_button_enabled"

        fun start(context: Context) {
            val intent = Intent(context, FloatingButtonService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingButtonService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var fabIcon: ImageView? = null
    private lateinit var sharedPreferences: SharedPreferences

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    // Track state changes via SharedPreferences listener
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == MainActivity.KEY_FIREWALL_ENABLED ||
            key == MainActivity.KEY_ACTIVE_PACKAGES ||
            key == MainActivity.KEY_FIREWALL_SAVED_ELAPSED
        ) {
            updateFabAppearance()
        }
    }

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefsListener)
        showFloatingButton()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
    }

    private fun createNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.floating_button_notification_title))
            .setContentText(getString(R.string.floating_button_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ──────────────── floating window ────────────────

    private fun showFloatingButton() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.overlay_permission_required), Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_firewall_button, null)
        fabIcon = floatingView?.findViewById(R.id.fabFirewallIcon)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 0
            y = 0
        }

        // Use system touch slop for reliable tap vs drag detection
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        var inactivityJob: Job? = null
        val inactivityTimeout = 3000L

        fun resetInactivityTimer() {
            inactivityJob?.cancel()
            floatingView?.background?.alpha = 255
            fabIcon?.animate()?.cancel()
            fabIcon?.alpha = 1.0f
            inactivityJob = scope.launch {
                delay(inactivityTimeout)
                val animator = android.animation.ValueAnimator.ofInt(255, 0)
                animator.duration = 300
                animator.addUpdateListener { animation ->
                    floatingView?.background?.alpha = animation.animatedValue as Int
                }
                animator.start()
                fabIcon?.animate()?.alpha(0.3f)?.setDuration(300)?.start()
            }
        }

        resetInactivityTimer()

        floatingView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    inactivityJob?.cancel()
                    v.background?.alpha = 255
                    fabIcon?.animate()?.cancel()
                    fabIcon?.alpha = 1.0f
                    
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    // Press feedback
                    v.alpha = 0.7f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (initialTouchX - event.rawX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                        moved = true
                    }
                    if (moved) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1.0f
                    if (!moved && event.action == MotionEvent.ACTION_UP) {
                        onFabClicked()
                    }
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
        try {
            floatingView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        floatingView = null
        fabIcon = null
    }

    // ──────────────── appearance ────────────────

    private fun updateFabAppearance() {
        val enabled = loadFirewallEnabled()
        fabIcon?.setImageResource(
            if (enabled) R.drawable.ic_firewall_enabled else R.drawable.ic_quick_tile
        )
        // Tint using standard colours — green when active, grey when off
        val tint = if (enabled) {
            android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt()) // green
        } else {
            android.content.res.ColorStateList.valueOf(0xFFBDBDBD.toInt()) // grey
        }
        fabIcon?.imageTintList = tint
    }

    // ──────────────── firewall toggle ────────────────

    private fun onFabClicked() {
        try {
            val enabled = loadFirewallEnabled()
            if (enabled) {
                if (!checkBackendReady()) return
                scope.launch {
                    try {
                        applyDisableFirewall()
                    } catch (e: Exception) {
                        Toast.makeText(this@FloatingButtonService, getString(R.string.failed_to_disable_firewall), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                val selectedApps = loadSelectedApps()
                val firewallMode = FirewallMode.fromName(
                    sharedPreferences.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name)
                )
                val targetApps: List<String>
                val whitelistAllowApps: List<String>
                if (firewallMode == FirewallMode.WHITELIST) {
                val showSystem = sharedPreferences.getBoolean(MainActivity.KEY_SHOW_SYSTEM_APPS, false)
                val result = WhitelistFilter.compute(this, selectedApps, showSystem)
                    targetApps = result.toBlock
                    whitelistAllowApps = result.toAllow
                } else {
                    targetApps = selectedApps
                    whitelistAllowApps = emptyList()
                }
                if (targetApps.isEmpty() && !firewallMode.allowsDynamicSelection()) {
                    Toast.makeText(this, getString(R.string.no_apps_selected), Toast.LENGTH_SHORT).show()
                    return
                }
                if (!checkBackendReady()) return
                scope.launch {
                    try {
                        applyEnableFirewall(targetApps, whitelistAllowApps)
                    } catch (e: Exception) {
                        Toast.makeText(this@FloatingButtonService, getString(R.string.failed_to_enable_firewall), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ──────────────── helpers ────────────────

    private fun loadFirewallEnabled(): Boolean = FirewallUtils.loadFirewallEnabled(sharedPreferences)

    private fun loadSelectedApps(): List<String> = FirewallUtils.loadSelectedApps(this, sharedPreferences)

    private fun loadActivePackages(): Set<String> = FirewallUtils.loadActivePackages(sharedPreferences)

    private fun checkBackendReady(): Boolean = FirewallUtils.checkBackendReady(this)

    private suspend fun applyEnableFirewall(packageNames: List<String>, whitelistAllowApps: List<String> = emptyList()) {
        val firewallMode = FirewallMode.fromName(
            sharedPreferences.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name)
        )

        // For tracking modes, try to auto-enable accessibility service
        if (firewallMode == FirewallMode.SMART_FOREGROUND || firewallMode == FirewallMode.FOCUS_TRACKER) {
            if (!ForegroundDetectionService.isServiceEnabled(this@FloatingButtonService)) {
                withContext(Dispatchers.IO) {
                    ForegroundDetectionService.enableServiceViaShell(this@FloatingButtonService)
                }
            }
        }

        withContext(Dispatchers.IO) {
            val successful = enableFirewall(packageNames, whitelistAllowApps)
            // In dynamic-selection modes (Adaptive, Smart Foreground, Whitelist),
            // the firewall is valid even if no individual apps were blocked yet —
            // chain3 was turned on and that's enough.
            if (successful.isNotEmpty() || firewallMode.allowsDynamicSelection()) {
                saveFirewallEnabled(true)
                saveActivePackages(successful.toSet())
                
                withContext(Dispatchers.Main) {
                    if (sharedPreferences.getBoolean(KEY_FLOATING_BUTTON_ENABLED, false)) {
                        start(this@FloatingButtonService)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingButtonService, getString(R.string.failed_to_enable_firewall), Toast.LENGTH_SHORT).show()
                }
            }
        }
        updateFabAppearance()
    }

    private suspend fun applyDisableFirewall() {
        val active = loadActivePackages()
        withContext(Dispatchers.IO) {
            val ok = disableFirewall(active.toList())
            if (ok) {
                saveFirewallEnabled(false)
                saveActivePackages(emptySet())
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingButtonService, getString(R.string.failed_to_disable_firewall), Toast.LENGTH_SHORT).show()
                }
            }
        }
        updateFabAppearance()
    }

    private fun enableFirewall(packageNames: List<String>, whitelistAllowApps: List<String> = emptyList()): List<String> {
        val successful = mutableListOf<String>()
        if (!ShellExecutorBlocking.runBlockingSuccess(this, "cmd connectivity set-chain3-enabled true")) return successful

        val firewallMode = FirewallMode.fromName(
            sharedPreferences.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name)
        )
        if (firewallMode == FirewallMode.SMART_FOREGROUND || firewallMode == FirewallMode.FOCUS_TRACKER) {
            return successful
        }

        if (firewallMode == FirewallMode.SCREEN_LOCK_MODE && !ScreenLockModeReceiver.isDeviceLocked(this)) {
            return successful
        }

        val selfPkg = packageName
        for (pkg in packageNames) {
            if (pkg == selfPkg || ShizukuPackageResolver.isShizukuPackage(this, pkg)) continue
            if (ShellExecutorBlocking.runBlockingSuccess(this, "cmd connectivity set-package-networking-enabled false $pkg")) {
                successful.add(pkg)
            }
        }

        for (pkg in whitelistAllowApps) {
            if (pkg == selfPkg || ShizukuPackageResolver.isShizukuPackage(this, pkg)) continue
            ShellExecutorBlocking.runBlockingSuccess(this, "cmd connectivity set-package-networking-enabled true $pkg")
        }

        return successful
    }

    private fun disableFirewall(packageNames: List<String>): Boolean {
        var chainDisabled = false
        val selfPkg = packageName
        val toUnblock = packageNames.toMutableList()

        // In Smart Foreground mode, also unblock the current foreground app
        val firewallMode = FirewallMode.fromName(
            sharedPreferences.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name)
        )
        if (firewallMode == FirewallMode.SMART_FOREGROUND || firewallMode == FirewallMode.FOCUS_TRACKER) {
            val currentFgApp = sharedPreferences.getString(MainActivity.KEY_SMART_FOREGROUND_APP, null)
            if (!currentFgApp.isNullOrEmpty() && !toUnblock.contains(currentFgApp)) {
                toUnblock.add(currentFgApp)
            }
        }

        for (pkg in toUnblock) {
            if (pkg == selfPkg || ShizukuPackageResolver.isShizukuPackage(this, pkg)) continue
            // Ignore per-package unblock failures here; global chain disable is the source of truth.
            ShellExecutorBlocking.runBlockingSuccess(this, "cmd connectivity set-package-networking-enabled true $pkg")
        }
        chainDisabled = ShellExecutorBlocking.runBlockingSuccess(this, "cmd connectivity set-chain3-enabled false")

        if (firewallMode == FirewallMode.SMART_FOREGROUND || firewallMode == FirewallMode.FOCUS_TRACKER) {
            sharedPreferences.edit()
                .putString(MainActivity.KEY_SMART_FOREGROUND_APP, "")
                .putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet())
                .apply()
        }

        return chainDisabled
    }

    private fun saveFirewallEnabled(enabled: Boolean) {
        FirewallUtils.saveFirewallEnabled(this, sharedPreferences, enabled)
    }

    private fun saveActivePackages(packages: Set<String>) {
        FirewallUtils.saveActivePackages(sharedPreferences, packages)
    }
}
