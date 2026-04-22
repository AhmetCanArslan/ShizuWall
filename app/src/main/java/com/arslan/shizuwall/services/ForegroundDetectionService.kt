package com.arslan.shizuwall.services

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.content.pm.ServiceInfo
import android.view.inputmethod.InputMethodManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.arslan.shizuwall.FirewallMode
import com.arslan.shizuwall.R
import com.arslan.shizuwall.shell.ShellExecutor
import com.arslan.shizuwall.shell.ShellExecutorProvider
import com.arslan.shizuwall.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import android.util.Log

/**
 * AccessibilityService that monitors foreground app changes for Smart Foreground mode.
 *
 * When enabled, this service detects when a new app comes to the foreground and:
 * 1. Allows the new foreground app (first, to minimize latency)
 * 2. Blocks the previously allowed app
 *
 * Key design decisions:
 * - Events are debounced (150 ms) to avoid thrashing during rapid app switches.
 * - chain3 is validated before each rule-change batch; re-enabled if needed.
 * - Failed shell commands are retried once before giving up.
 * - Skip-packages list is refreshed on package-install/uninstall broadcasts.
 * - The notification subtitle reflects the name of the currently-active app.
 */
class ForegroundDetectionService : AccessibilityService() {

    companion object {
        private const val TAG = "ForegroundDetection"
        private const val CHANNEL_ID = "smart_foreground_channel"
        private const val NOTIFICATION_ID = 4001
        private const val DEBOUNCE_MS = 150L

        private const val RETRY_DELAY_MS = 300L
        private const val UNMANAGED_ALLOW_THROTTLE_MS = 2000L

        // System packages that should never be managed by Smart Foreground
        private val SYSTEM_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.nexuslauncher",
            "com.android.settings",
            "com.android.keyguard",
            "com.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.android.shell",
            "com.android.vending",
            "com.samsung.android.app.routines",
            "com.samsung.android.themestore",
            "com.sec.android.app.launcher"
        )
        
        // Known input method packages to skip
        private val INPUT_METHOD_PACKAGES = setOf(
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.swiftkey.swiftkeyfree",
            "com.touchtype.swiftkey",
            "org.futo.inputmethod.latin"
        )
        
        // Action for broadcasting foreground app changes
        const val ACTION_FOREGROUND_APP_CHANGED = "com.arslan.shizuwall.FOREGROUND_APP_CHANGED"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_PREVIOUS_PACKAGE = "previous_package"
        
        fun isServiceEnabled(context: Context): Boolean {
            val enabledServices = try {
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
            } catch (e: Exception) {
                return false
            }
            
            val serviceName = "${context.packageName}/${ForegroundDetectionService::class.java.canonicalName}"
            // Split by ":" and check exact component matches to avoid substring false positives
            return enabledServices.split(":").any { component ->
                component == serviceName || component.startsWith("${context.packageName}/")
            }
        }

        /**
         * Auto-enable the accessibility service via shell command (Shizuku or LADB daemon).
         * Returns true if the service was successfully enabled.
         */
        suspend fun enableServiceViaShell(context: Context): Boolean {
            return withContext(Dispatchers.IO) {
                try {
                    val executor = ShellExecutorProvider.forContext(context)
                    val componentName = "${context.packageName}/${ForegroundDetectionService::class.java.canonicalName}"

                    val currentResult = executor.exec("settings get secure enabled_accessibility_services")
                    val currentServices = currentResult.stdout.trim().ifEmpty { "null" }

                    if (currentServices.split(":").any { it == componentName }) {
                        Log.d(TAG, "Service already in enabled_accessibility_services")
                        return@withContext true
                    }

                    val newValue = if (currentServices.isEmpty() || currentServices == "null") {
                        componentName
                    } else {
                        "$currentServices:$componentName"
                    }

                    val putResult = executor.exec("settings put secure enabled_accessibility_services '$newValue'")
                    if (!putResult.success) {
                        Log.w(TAG, "Failed to set enabled_accessibility_services: ${putResult.stderr}")
                        return@withContext false
                    }

                    executor.exec("settings put secure accessibility_enabled 1")

                    // Give the system time to process.
                    delay(500)
                    if (!isServiceEnabled(context)) {
                        Log.w(TAG, "Service not actually enabled after shell command")
                        return@withContext false
                    }

                    Log.d(TAG, "Accessibility service auto-enabled via shell")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to auto-enable accessibility service", e)
                    false
                }
            }
        }

        /**
         * Auto-disable the accessibility service via shell command (Shizuku or LADB daemon).
         * Returns true if the service was successfully disabled.
         */
        suspend fun disableServiceViaShell(context: Context): Boolean {
            return withContext(Dispatchers.IO) {
                try {
                    val executor = ShellExecutorProvider.forContext(context)
                    val componentName = "${context.packageName}/${ForegroundDetectionService::class.java.canonicalName}"

                    val currentResult = executor.exec("settings get secure enabled_accessibility_services")
                    val currentServices = currentResult.stdout.trim().ifEmpty { "null" }

                    if (!currentServices.split(":").any { it == componentName }) {
                        Log.d(TAG, "Service not in enabled_accessibility_services")
                        return@withContext true
                    }

                    val newValue = currentServices.split(":").filter { it != componentName }.joinToString(":")

                    // Handle empty case - use "null" to properly clear the setting
                    val putResult = if (newValue.isEmpty()) {
                        executor.exec("settings put secure enabled_accessibility_services null")
                    } else {
                        executor.exec("settings put secure enabled_accessibility_services '$newValue'")
                    }
                    if (!putResult.success) {
                        Log.w(TAG, "Failed to set enabled_accessibility_services: ${putResult.stderr}")
                        return@withContext false
                    }

                    // Give the system time to process and verify the service was actually disabled
                    delay(500)
                    if (isServiceEnabled(context)) {
                        Log.w(TAG, "Service still enabled after shell command")
                        return@withContext false
                    }

                    Log.d(TAG, "Accessibility service auto-disabled via shell")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to auto-disable accessibility service", e)
                    false
                }
            }
        }
    }

    private var currentForegroundPackage: String? = null
    private var lastManagedPackage: String? = null
    private var isShizuWallFocused: Boolean? = null
    private var lastObservedForegroundPackage: String? = null

    @Volatile private var pendingBlockPackage: String? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private var debounceJob: Job? = null

    private lateinit var sharedPreferences: SharedPreferences

    @Volatile private var cachedFirewallEnabled = false
    @Volatile private var cachedFirewallMode = FirewallMode.DEFAULT

    @Volatile private var cachedShellExecutor: ShellExecutor? = null
    private val executorLock = Any()

    @Volatile private var dynamicSkipPackages: Set<String> = emptySet()
    @Volatile private var selectedPackages: Set<String> = emptySet()
    @Volatile private var cachedAppModes: Map<String, Int> = emptyMap()
    @Volatile private var lastUnmanagedAllowedPackage: String? = null
    @Volatile private var lastUnmanagedAllowedAtMs: Long = 0L

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            MainActivity.KEY_FIREWALL_ENABLED -> {
                cachedFirewallEnabled = prefs.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)

                if (!cachedFirewallEnabled) {
                    try {
                        stopForeground(true)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to stop foreground on disable", e)
                    }

                    currentForegroundPackage = null
                    lastManagedPackage = null
                    pendingBlockPackage = null
                    isShizuWallFocused = null
                    sharedPreferences.edit()
                        .putString(MainActivity.KEY_SMART_FOREGROUND_APP, "")
                        .putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet())
                        .apply()
                } else if (cachedFirewallMode == FirewallMode.SMART_FOREGROUND || cachedFirewallMode == FirewallMode.HYBRID) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            startForeground(
                                NOTIFICATION_ID,
                                buildNotification(null),
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                            )
                        } else {
                            startForeground(NOTIFICATION_ID, buildNotification(null))
                        }
                    } catch (e: IllegalStateException) {
                        Log.w(TAG, "Failed to start foreground - already in foreground state", e)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to start foreground", e)
                    }
                }
            }
            MainActivity.KEY_FIREWALL_MODE -> {
                val modeName = prefs.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name)
                cachedFirewallMode = FirewallMode.fromName(modeName)

                if (cachedFirewallMode != FirewallMode.SMART_FOREGROUND && cachedFirewallMode != FirewallMode.HYBRID) {
                    try {
                        stopForeground(true)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to stop foreground on mode change", e)
                    }
                    currentForegroundPackage = null
                    lastManagedPackage = null
                    pendingBlockPackage = null
                    isShizuWallFocused = null
                    sharedPreferences.edit()
                        .putString(MainActivity.KEY_SMART_FOREGROUND_APP, "")
                        .putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet())
                        .apply()
                } else if (cachedFirewallEnabled) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            startForeground(
                                NOTIFICATION_ID,
                                buildNotification(null),
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                            )
                        } else {
                            startForeground(NOTIFICATION_ID, buildNotification(null))
                        }
                    } catch (e: IllegalStateException) {
                        Log.w(TAG, "Failed to start foreground - already in foreground state", e)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to start foreground", e)
                    }
                }
            }
            MainActivity.KEY_WORKING_MODE -> {
                synchronized(executorLock) { cachedShellExecutor = null }
            }
            MainActivity.KEY_SELECTED_APPS -> {
                selectedPackages = prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet()) ?: emptySet()
            }
            MainActivity.KEY_APP_MODES -> {
                cachedAppModes = parseAppModes(prefs.getString(MainActivity.KEY_APP_MODES, "{}"))
            }
        }
    }

    private fun parseAppModes(modesStr: String?): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        try {
            val json = org.json.JSONObject(modesStr ?: "{}")
            for (key in json.keys()) {
                map[key] = json.optInt(key, 0)
            }
        } catch (_: Exception) {}
        return map
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            if (action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REMOVED) {
                serviceScope.launch(Dispatchers.Default) {
                    dynamicSkipPackages = resolveDynamicSkipPackages()
                    Log.d(TAG, "Skip-packages refreshed (${dynamicSkipPackages.size} pkgs)")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        cachedFirewallEnabled = sharedPreferences.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
        val modeName = sharedPreferences.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name)
        cachedFirewallMode = FirewallMode.fromName(modeName)

        val restoredApp = sharedPreferences.getString(MainActivity.KEY_SMART_FOREGROUND_APP, null)
        if (!restoredApp.isNullOrEmpty()) {
            lastManagedPackage = restoredApp
        }
        lastObservedForegroundPackage = sharedPreferences.getString(MainActivity.KEY_LAST_FOREGROUND_APP, null)

        selectedPackages = sharedPreferences.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        cachedAppModes = parseAppModes(sharedPreferences.getString(MainActivity.KEY_APP_MODES, "{}"))

        dynamicSkipPackages = resolveDynamicSkipPackages()

        serviceScope.launch(Dispatchers.IO) {
            cleanupStaleBlockedPackages()
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(prefListener)

        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, pkgFilter)

        createNotificationChannel()

        if (cachedFirewallEnabled && (cachedFirewallMode == FirewallMode.SMART_FOREGROUND || cachedFirewallMode == FirewallMode.HYBRID)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, buildNotification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification(null))
            }
        }

        Log.d(TAG, "Service created (restored=$restoredApp, dynamicSkip=${dynamicSkipPackages.size} pkgs)")
    }

    override fun onDestroy() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefListener)
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // Skip same-package events immediately (no debounce needed).
        if (packageName == currentForegroundPackage) return

        // Filter non-Activity windows (dialogs, panels, toasts, etc.) but do so carefully.
        val className = event.className?.toString()
        if (className != null && isNonActivityWindow(className)) return

        publishObservedForegroundApp(packageName)

        // Gate on cached state — zero SharedPrefs reads on the hot path.
        if (cachedFirewallMode != FirewallMode.SMART_FOREGROUND && cachedFirewallMode != FirewallMode.HYBRID) return
        if (cachedFirewallMode != FirewallMode.FOCUS_TRACKER) {
            if (packageName == this.packageName) return
            return
        }
        if (!cachedFirewallEnabled) return

        if (cachedFirewallMode == FirewallMode.FOCUS_TRACKER) {
            processFocusTracker(packageName)
            return
        }

        // Debounce: cancel any previously queued dispatch and re-schedule.
        debounceJob?.cancel()
        // Snapshot package name in a local val to avoid closure capture issues.
        val newPackage = packageName
        debounceJob = serviceScope.launch {
            delay(DEBOUNCE_MS)
            processPackageChange(newPackage)
        }
    }

    private fun processFocusTracker(newPackage: String) {
        // Remove the system ui overlay ignorance feature
        val isNowFocused = (newPackage == this.packageName)
        
        // Only execute when focus changes
        if (isShizuWallFocused == isNowFocused) return
    
        isShizuWallFocused = isNowFocused
    
        serviceScope.launch(Dispatchers.IO) {
            try {
                val executor = getShellExecutor()
                ensureChain3Enabled(executor)
                applyFocusTrackerRules(executor, isNowFocused)
                Log.d(TAG, "ShizuWall Focused: $isNowFocused")
            } catch (e: Exception) {
                Log.e(TAG, "Focus Tracker rule update failed", e)
            }
        }
    }

    private suspend fun processPackageChange(newPackage: String) {
        if ((cachedFirewallMode != FirewallMode.SMART_FOREGROUND && cachedFirewallMode != FirewallMode.HYBRID) || !cachedFirewallEnabled) return
        if (newPackage == currentForegroundPackage) return

        val isPlatformSkip = shouldAlwaysSkipPackage(newPackage)
        val isSelected = selectedPackages.contains(newPackage)
        val appMode = cachedAppModes[newPackage] ?: 0
        val isSmartForegroundApp = cachedFirewallMode == FirewallMode.SMART_FOREGROUND || (cachedFirewallMode == FirewallMode.HYBRID && appMode == 1)
        val shouldManage = isSelected && !isPlatformSkip && isSmartForegroundApp

        if (!shouldManage) {
            // Going to launcher/system or an unselected app: don't manage the new package,
            // but block the previously managed selected package.
            currentForegroundPackage = newPackage
            val previous = lastManagedPackage
            lastManagedPackage = null

            if (previous != null) {
                // Store which package we're about to block so a quick return can cancel it.
                pendingBlockPackage = previous
                delay(DEBOUNCE_MS) // extra grace period before committing the block
                if (pendingBlockPackage == previous) {
                    pendingBlockPackage = null
                    blockPackage(previous)
                }
            }

            // If the user switched to an unselected normal app, proactively allow it.
            // This self-heals stale blocks from past states without bringing it into managed set.
            if (!isPlatformSkip && !isSelected) {
                allowUnmanagedPackage(newPackage)
            }
            return
        }

        // If this package equals the one we were about to block (user switched back quickly),
        // cancel the pending block.
        if (newPackage == pendingBlockPackage) {
            pendingBlockPackage = null
        }

        val previous = lastManagedPackage
        currentForegroundPackage = newPackage
        lastManagedPackage = newPackage

        handleForegroundAppChange(previous, newPackage)
    }

    private fun isNonActivityWindow(className: String): Boolean {
        val lower = className.lowercase()
        return lower.endsWith("dialog") ||
               lower.endsWith("dialogfragment") ||
               lower.endsWith("popup") ||
               lower.endsWith("popupwindow") ||
               lower.endsWith("toast") ||
               lower.endsWith("panel") ||
               lower.endsWith("overlay") ||
               lower.endsWith("bubble") ||
               lower.endsWith("dropdown") ||
               lower.contains("${'$'}") || // inner class — usually a sub-window
               lower == "android.inputmethodservice.softinputwindow"
    }

    private fun publishObservedForegroundApp(packageName: String) {
        if (packageName == lastObservedForegroundPackage) return
        val previous = lastObservedForegroundPackage
        lastObservedForegroundPackage = packageName
        sharedPreferences.edit().putString(MainActivity.KEY_LAST_FOREGROUND_APP, packageName).apply()
        sendBroadcast(Intent(ACTION_FOREGROUND_APP_CHANGED).apply {
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_PREVIOUS_PACKAGE, previous)
        })
    }

    private fun shouldSkipPackage(packageName: String): Boolean {
        if (shouldAlwaysSkipPackage(packageName)) return true

        if (!selectedPackages.contains(packageName)) return true
        return false
    }

    private fun shouldAlwaysSkipPackage(packageName: String): Boolean {
        if (packageName == this.packageName) return true
        if (SYSTEM_PACKAGES.contains(packageName)) return true
        if (INPUT_METHOD_PACKAGES.contains(packageName)) return true
        if (dynamicSkipPackages.contains(packageName)) return true


        if (packageName.contains("launcher", ignoreCase = true)) return true
        if (packageName.startsWith("com.android.") && !packageName.contains("chrome")) return true
        if (packageName.contains("inputmethod", ignoreCase = true) ||
            packageName.contains("keyboard", ignoreCase = true) ||
            packageName.contains(".ime.", ignoreCase = true) ||
            packageName.endsWith(".ime", ignoreCase = true)) {
            return true
        }
        return false
    }

    private fun resolveDynamicSkipPackages(): Set<String> {
        val packages = mutableSetOf<String>()
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            for (info in packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL)) {
                packages.add(info.activityInfo.packageName)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve launchers", e)
        }
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.forEach { packages.add(it.packageName) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve input methods", e)
        }
        return packages
    }

    private suspend fun handleForegroundAppChange(previousPackage: String?, newPackage: String) {
        val start = System.currentTimeMillis()
        val success = updateFirewallRules(previousPackage, newPackage)
        val elapsed = System.currentTimeMillis() - start

        Log.d(TAG, "$previousPackage → $newPackage (${elapsed}ms, ok=$success)")

        if (success) {
            sharedPreferences.edit()
                .putString(MainActivity.KEY_SMART_FOREGROUND_APP, newPackage)
                .apply()

            // Update notification to show the active app name.
            updateNotification(newPackage)

            sendBroadcast(Intent(ACTION_FOREGROUND_APP_CHANGED).apply {
                putExtra(EXTRA_PACKAGE_NAME, newPackage)
                putExtra(EXTRA_PREVIOUS_PACKAGE, previousPackage)
            })
        } else {
            // Rule application failed — reset state so next event can re-try from scratch.
            // Only restore previousPackage if it was a managed (non-skip) app; otherwise
            // clear both to avoid an inconsistent state.
            if (previousPackage != null && !shouldSkipPackage(previousPackage)) {
                lastManagedPackage = previousPackage
                currentForegroundPackage = previousPackage
            } else {
                lastManagedPackage = null
                currentForegroundPackage = null
            }
        }
    }

    private suspend fun blockPackage(packageName: String) {
        withContext(Dispatchers.IO) {
            try {
                val executor = getShellExecutor()
                ensureChain3Enabled(executor)

                val result = execWithRetry(executor, "cmd connectivity set-package-networking-enabled false $packageName")
                if (!result.success) {
                    Log.w(TAG, "Failed to block $packageName: ${result.stderr}")
                } else {
                    Log.d(TAG, "$packageName → [home] blocked")
                }

                val currentActive = sharedPreferences.getStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
                currentActive.add(packageName)

                sharedPreferences.edit()
                    .putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, currentActive)
                    .putString(MainActivity.KEY_SMART_FOREGROUND_APP, "")
                    .apply()

                // Update notification to idle state.
                withContext(Dispatchers.Main) { updateNotification(null) }

                sendBroadcast(Intent(ACTION_FOREGROUND_APP_CHANGED).apply {
                    putExtra(EXTRA_PACKAGE_NAME, "")
                    putExtra(EXTRA_PREVIOUS_PACKAGE, packageName)
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error blocking $packageName", e)
            }
        }
    }

    private suspend fun allowUnmanagedPackage(packageName: String) {
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                if (packageName == lastUnmanagedAllowedPackage &&
                    now - lastUnmanagedAllowedAtMs < UNMANAGED_ALLOW_THROTTLE_MS
                ) {
                    return@withContext
                }

                val executor = getShellExecutor()
                val result = execWithRetry(executor, "cmd connectivity set-package-networking-enabled true $packageName")
                if (result.success || isUidOwnerMapMissingEntry(result)) {
                    lastUnmanagedAllowedPackage = packageName
                    lastUnmanagedAllowedAtMs = now
                    Log.d(TAG, "$packageName -> [unmanaged] allowed")
                } else {
                    val err = result.stderr.ifEmpty { result.stdout }
                    Log.w(TAG, "Failed to allow unmanaged package $packageName: $err")
                }
            } catch (e: CancellationException) {
                // Expected when rapid app switches cancel the in-flight debounce job.
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed unmanaged allow for $packageName", e)
            }
        }
    }

    // Version 1
    private suspend fun applyFocusTrackerRules(executor: ShellExecutor, isFocused: Boolean) {
        val pkgs = selectedPackages.toList()
        val shouldEnable = isFocused

        // Run commands in parallel for faster execution
        coroutineScope {
            pkgs.forEach { pkg ->
                if (pkg == packageName || pkg.contains("shizuku")) return@forEach

                launch(Dispatchers.IO) {
                    executor.exec("cmd connectivity set-package-networking-enabled $shouldEnable $pkg")
                }
            }
        }

        val activePkgs = if (isFocused) emptySet() else selectedPackages
        sharedPreferences.edit()
            .putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, activePkgs)
            .apply()

        withContext(Dispatchers.Main) {
            val title = if (isFocused) getString(R.string.focus_tracker_paused) else getString(R.string.focus_tracker_active)
            val notification = NotificationCompat.Builder(this@ForegroundDetectionService, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(getString(R.string.firewall_mode_focus_tracker_description))
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun getShellExecutor(): ShellExecutor {
        cachedShellExecutor?.let { return it }
        synchronized(executorLock) {
            cachedShellExecutor?.let { return it }
            return ShellExecutorProvider.forContext(this).also { cachedShellExecutor = it }
        }
    }

    /**
     * Execute [command] and retry once on failure after a short delay.
     */
    private suspend fun execWithRetry(executor: ShellExecutor, command: String): com.arslan.shizuwall.shell.ShellResult {
        val first = executor.exec(command)
        if (first.success) return first
        delay(RETRY_DELAY_MS)
        return executor.exec(command)
    }

    private fun isUidOwnerMapMissingEntry(result: com.arslan.shizuwall.shell.ShellResult): Boolean {
        val details = (result.stderr + "\n" + result.stdout).lowercase()
        return details.contains("suidownermap does not have entry for uid")
    }

    private suspend fun cleanupStaleBlockedPackages() {
        val blocked = sharedPreferences.getStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet()) ?: emptySet()
        if (blocked.isEmpty()) return

        val stale = blocked.filter { !selectedPackages.contains(it) }.toSet()
        if (stale.isEmpty()) return

        try {
            val executor = getShellExecutor()
            val remaining = blocked.toMutableSet()
            for (pkg in stale) {
                val result = execWithRetry(executor, "cmd connectivity set-package-networking-enabled true $pkg")
                if (result.success || isUidOwnerMapMissingEntry(result)) {
                    remaining.remove(pkg)
                    Log.d(TAG, "Cleared stale blocked package: $pkg")
                } else {
                    val err = result.stderr.ifEmpty { result.stdout }
                    Log.w(TAG, "Failed to clear stale blocked package $pkg: $err")
                }
            }
            sharedPreferences.edit()
                .putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, remaining)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed stale-block cleanup", e)
        }
    }

    
    private suspend fun ensureChain3Enabled(executor: ShellExecutor) {
        try {
            val checkResult = executor.exec("cmd connectivity get-chain3-enabled")
            val isEnabled = checkResult.stdout.trim().equals("true", ignoreCase = true)
            if (!isEnabled) {
                Log.w(TAG, "chain3 was not enabled — re-enabling")
                executor.exec("cmd connectivity set-chain3-enabled true")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "chain3 enable check/set failed (non-fatal)", e)
            // If check not supported, try to re-enable anyway.
            try {
                executor.exec("cmd connectivity set-chain3-enabled true")
            } catch (e3: CancellationException) {
                throw e3
            } catch (e2: Exception) {
                Log.d(TAG, "chain3 fallback re-enable also failed (non-fatal)", e2)
            }
        }
    }

    
    private suspend fun updateFirewallRules(previousPackage: String?, newPackage: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val executor = getShellExecutor()

                ensureChain3Enabled(executor)
                val allowResult = execWithRetry(executor, "cmd connectivity set-package-networking-enabled true $newPackage")
                val allowOk = allowResult.success || isUidOwnerMapMissingEntry(allowResult)
                if (!allowOk) {
                    val allowErr = allowResult.stderr.ifEmpty { allowResult.stdout }
                    Log.w(TAG, "Failed to allow $newPackage: $allowErr")
                    return@withContext false
                } else if (!allowResult.success && isUidOwnerMapMissingEntry(allowResult)) {
                    Log.d(TAG, "Allow for $newPackage already effective (uid not present in owner map)")
                }

                val shouldBlockPrevious = !previousPackage.isNullOrEmpty() && previousPackage != newPackage
                val blockResult = if (shouldBlockPrevious) {
                    execWithRetry(executor, "cmd connectivity set-package-networking-enabled false $previousPackage")
                } else {
                    null
                }
                val blockOk = blockResult?.success ?: true

                if (!blockOk) {
                    val blockErr = blockResult?.stderr?.ifEmpty { blockResult.stdout } ?: ""
                    Log.w(TAG, "Failed to block $previousPackage: $blockErr")
                }

                val currentActive = sharedPreferences.getStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
                if (blockOk && shouldBlockPrevious) {
                    currentActive.add(previousPackage!!)
                }
                if (allowOk) {
                    currentActive.remove(newPackage)
                }

                sharedPreferences.edit()
                    .putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, currentActive)
                    .apply()

                allowOk && blockOk
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error updating firewall rules", e)
                false
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.smart_foreground_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.smart_foreground_notification_channel_description)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(activePackage: String?): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (activePackage != null) {
            val appLabel = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(activePackage, 0)
                ).toString()
            } catch (_: Exception) {
                activePackage
            }
            getString(R.string.smart_foreground_active_app, appLabel)
        } else {
            getString(R.string.smart_foreground_active_description)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.smart_foreground_active))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(activePackage: String?) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(activePackage))
        } catch (_: Exception) {}
    }
}
