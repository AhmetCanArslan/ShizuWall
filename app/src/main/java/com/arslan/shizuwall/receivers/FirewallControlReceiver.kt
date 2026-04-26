package com.arslan.shizuwall.receivers

import android.content.BroadcastReceiver
import android.content.Context
import com.arslan.shizuwall.widgets.FirewallWidgetProvider
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import com.arslan.shizuwall.FirewallMode
import com.arslan.shizuwall.R
import com.arslan.shizuwall.ladb.LadbLogStore
import com.arslan.shizuwall.shell.RootShellExecutor
import com.arslan.shizuwall.shell.ShellResult
import com.arslan.shizuwall.shell.ShellExecutorProvider
import com.arslan.shizuwall.services.ScreenLockMonitorService
import com.arslan.shizuwall.ui.MainActivity
import com.arslan.shizuwall.utils.ShizukuPackageResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Manifest-registered receiver for ACTION_FIREWALL_CONTROL.
 * - Extras:
 *   - MainActivity.EXTRA_FIREWALL_ENABLED (boolean)
 *   - MainActivity.EXTRA_PACKAGES_CSV (string, optional)
 *
 * Performs the same cmd connectivity operations via Shizuku and updates prefs,
 * then broadcasts ACTION_FIREWALL_STATE_CHANGED for UI refresh.
 */
class FirewallControlReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "FirewallControl"
        private const val SHIZUKU_WAIT_MAX_ATTEMPTS = 10
        private const val SHIZUKU_WAIT_DELAY_MS = 300L
        const val EXTRA_AUTOMATION_EVENT = "com.arslan.shizuwall.EXTRA_AUTOMATION_EVENT"
    }

    private suspend fun waitForShizukuBinder(): Boolean {
        for (attempt in 1..SHIZUKU_WAIT_MAX_ATTEMPTS) {
            try {
                if (Shizuku.pingBinder()) {
                    android.util.Log.d(TAG, "Shizuku binder available after $attempt attempt(s)")
                    return true
                }
            } catch (_: Throwable) {
                // Shizuku not ready yet
            }
            if (attempt < SHIZUKU_WAIT_MAX_ATTEMPTS) {
                delay(SHIZUKU_WAIT_DELAY_MS)
            }
        }
        android.util.Log.w(TAG, "Shizuku binder not available after $SHIZUKU_WAIT_MAX_ATTEMPTS attempts")
        return false
    }

    private fun appendLadbFailureLog(context: Context, message: String, result: ShellResult? = null) {
        val details = when {
            result == null -> null
            result.success -> null
            result.stderr.isNotBlank() -> result.stderr.trim()
            result.stdout.isNotBlank() -> result.stdout.trim()
            else -> "exit=${result.exitCode}"
        }

        val fullMessage = if (details.isNullOrBlank()) {
            "Firewall daemon failure: $message"
        } else {
            "Firewall daemon failure: $message | $details"
        }
        LadbLogStore.append(context, fullMessage)
    }

    override fun onReceive(context: Context, intent: Intent) {
        
        // Log received action to help debug ADB commands
        android.util.Log.d(TAG, "Received action: ${intent.action}")
        
        if (intent.action != MainActivity.ACTION_FIREWALL_CONTROL) return

        // Use goAsync pattern via coroutine to avoid blocking receiver thread.
        val pending = goAsync()
        val enabled = intent.getBooleanExtra(MainActivity.EXTRA_FIREWALL_ENABLED, false)
        val csv = intent.getStringExtra(MainActivity.EXTRA_PACKAGES_CSV)
        val automationEvent = intent.getBooleanExtra(EXTRA_AUTOMATION_EVENT, false)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Read SharedPreferences inside IO coroutine to ensure proper initialization
                // when app process is started by this broadcast
                val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                val mode = prefs.getString(MainActivity.KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"
                val firewallMode = FirewallMode.fromName(prefs.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name))

                // Resolve package list: CSV -> saved selected apps
                val rawPackages = if (!csv.isNullOrBlank()) {
                    csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                } else {
                    val saved = prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())?.toList() ?: emptyList()
                    if (firewallMode == FirewallMode.WHITELIST) {
                        val showSys = prefs.getBoolean(MainActivity.KEY_SHOW_SYSTEM_APPS, false)
                        com.arslan.shizuwall.utils.WhitelistFilter.getPackagesToBlock(context, saved, showSys)
                    } else {
                        saved
                    }
                }

                // filter out any Shizuku packages and this app itself from incoming list
                val requestedPackages = rawPackages.filterNot { ShizukuPackageResolver.isShizukuPackage(context, it) || it == context.packageName }
                val packages = if (enabled) {
                    if (firewallMode == FirewallMode.SCREEN_LOCK_MODE && !ScreenLockModeReceiver.isDeviceLocked(context)) {
                        emptyList()
                    } else if (firewallMode == FirewallMode.HYBRID) {
                        val appModesStr = prefs.getString(MainActivity.KEY_APP_MODES, "{}")
                        val appModes = try { org.json.JSONObject(appModesStr!!) } catch (e: Exception) { org.json.JSONObject() }
                        val isLocked = ScreenLockModeReceiver.isDeviceLocked(context)
                        requestedPackages.filter {
                            val modeApp = appModes.optInt(it, 0)
                            when (modeApp) {
                                1 -> false // SMART_FOREGROUND app -> handled dynamically by accessibility service
                                2 -> isLocked // SCREEN_LOCK app -> block if screen locked
                                else -> true // DEFAULT app -> block immediately
                            }
                        }
                    } else if (firewallMode == FirewallMode.SMART_FOREGROUND) {
                        emptyList()
                    } else {
                        requestedPackages
                    }
                } else {
                    requestedPackages
                }

                android.util.Log.d(TAG, "Packages to process: $packages, enabled: $enabled, firewallMode: $firewallMode")

                if (enabled && packages.isEmpty() && !firewallMode.allowsDynamicSelection()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.no_apps_selected), Toast.LENGTH_SHORT).show()
                    }
                    pending.finish()
                    return@launch
                }

                val backendReady = if (mode == "LADB") {
                    val daemonManager = com.arslan.shizuwall.daemon.PersistentDaemonManager(context)
                    try {
                        daemonManager.isDaemonRunning()
                    } catch (e: Exception) {
                        false
                    }
                } else if (mode == "ROOT") {
                    RootShellExecutor.hasRootAccess()
                } else {
                    val binderAvailable = waitForShizukuBinder()
                    if (binderAvailable) {
                        try {
                            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                        } catch (_: Throwable) {
                            false
                        }
                    } else {
                        false
                    }
                }

                if (!backendReady) {
                    android.util.Log.w(TAG, "Backend is not ready for mode=$mode while applying firewall state=${if (enabled) "enable" else "disable"}")
                    if (mode == "LADB") {
                        appendLadbFailureLog(context, "Daemon is not running while applying firewall state=${if (enabled) "enable" else "disable"}")
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            if (automationEvent) {
                                context.getString(R.string.screen_lock_mode_operation_failed)
                            } else if (mode == "LADB") {
                                context.getString(R.string.daemon_not_running)
                            } else if (mode == "ROOT") {
                                context.getString(R.string.root_not_found_message)
                            } else {
                                context.getString(R.string.shizuku_not_available)
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    pending.finish()
                    return@launch
                }

                suspend fun execShell(cmd: String): ShellResult {
                    return ShellExecutorProvider.forContext(context).exec(cmd)
                }

                val successful = mutableListOf<String>()
                var globalCommandSuccess = true
                var hadCommandFailure = false

                if (enabled) {
                    // enable chain3
                    val chainEnableResult = execShell("cmd connectivity set-chain3-enabled true")
                    globalCommandSuccess = chainEnableResult.success
                    if (!chainEnableResult.success && mode == "LADB") {
                        appendLadbFailureLog(context, "Failed to enable firewall chain", chainEnableResult)
                    }
                    if (!chainEnableResult.success) {
                        hadCommandFailure = true
                    }
                    if (globalCommandSuccess) {
                        for (pkg in packages) {
                            val blockResult = execShell("cmd connectivity set-package-networking-enabled false $pkg")
                            if (blockResult.success) {
                                successful.add(pkg)
                            } else if (mode == "LADB") {
                                appendLadbFailureLog(context, "Failed to block package $pkg", blockResult)
                                hadCommandFailure = true
                            } else {
                                hadCommandFailure = true
                            }
                        }
                    }
                } else {
                    for (pkg in packages) {
                        val allowResult = execShell("cmd connectivity set-package-networking-enabled true $pkg")
                        if (allowResult.success) {
                            successful.add(pkg)
                        } else if (mode == "LADB") {
                            appendLadbFailureLog(context, "Failed to unblock package $pkg", allowResult)
                            hadCommandFailure = true
                        } else {
                            hadCommandFailure = true
                        }
                    }
                    val isGlobalDisable = csv.isNullOrBlank()
                    if (!firewallMode.allowsDynamicSelection() || isGlobalDisable) {
                        val chainDisableResult = execShell("cmd connectivity set-chain3-enabled false")
                        globalCommandSuccess = chainDisableResult.success
                        if (!chainDisableResult.success && mode == "LADB") {
                            appendLadbFailureLog(context, "Failed to disable firewall chain", chainDisableResult)
                        }
                        if (!chainDisableResult.success) {
                            hadCommandFailure = true
                        }
                    }
                }

                // Persist state to shared prefs (same keys MainActivity uses)
                // val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                prefs.edit().apply {
                    if (enabled && globalCommandSuccess && (successful.isNotEmpty() || firewallMode.allowsDynamicSelection())) {
                        putBoolean(MainActivity.KEY_FIREWALL_ENABLED, true)
                        putLong(MainActivity.KEY_FIREWALL_SAVED_ELAPSED, SystemClock.elapsedRealtime())
                        putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, successful.toSet())
                        
                        // In Adaptive Mode, sync the selected apps list with what was just enabled.
                        // In Whitelist Mode, blocked apps should NOT be added to the selected (whitelist) list.
                        if (firewallMode.allowsDynamicSelection() && successful.isNotEmpty() && firewallMode != FirewallMode.WHITELIST) {
                            val currentSelected = prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
                            currentSelected.addAll(successful)
                            putStringSet(MainActivity.KEY_SELECTED_APPS, currentSelected)
                            putInt(MainActivity.KEY_SELECTED_COUNT, currentSelected.size)
                        }
                        
                        // Start indicator and floating button if they are enabled
                        if (prefs.getBoolean(MainActivity.KEY_FIREWALL_INDICATOR_ENABLED, false)) {
                            com.arslan.shizuwall.services.ForegroundFirewallIndicatorService.start(context)
                        }
                        if (prefs.getBoolean(com.arslan.shizuwall.services.FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, false)) {
                            com.arslan.shizuwall.services.FloatingButtonService.start(context)
                        }
                    } else {
                        val isGlobalDisable = csv.isNullOrBlank()
                        if (!firewallMode.allowsDynamicSelection() || isGlobalDisable) {
                            if (globalCommandSuccess) {
                                putBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
                                remove(MainActivity.KEY_FIREWALL_SAVED_ELAPSED)
                                putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet())
                                if (firewallMode == FirewallMode.SMART_FOREGROUND || firewallMode == FirewallMode.HYBRID) {
                                    putString(MainActivity.KEY_SMART_FOREGROUND_APP, "")
                                }
                            } else {
                                // Global disable failed, show error but don't update state
                                if (!automationEvent) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, context.getString(R.string.failed_to_disable_firewall), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else {
                            // Partial disable (unblock specific apps), firewall stays ON
                            val currentActive = prefs.getStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
                            currentActive.removeAll(successful)
                            putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, currentActive)

                            // In Whitelist Mode, unblocking an app means adding it to the whitelist (selected list).
                            // In other dynamic modes, unblocking means removing from the selected list.
                            // Screen Lock Mode and Hybrid Mode preserve selected apps across unlock events.
                            if (firewallMode == FirewallMode.WHITELIST) {
                                val currentSelected = prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
                                currentSelected.addAll(successful)
                                putStringSet(MainActivity.KEY_SELECTED_APPS, currentSelected)
                                putInt(MainActivity.KEY_SELECTED_COUNT, currentSelected.size)
                            } else if (firewallMode != FirewallMode.SCREEN_LOCK_MODE && firewallMode != FirewallMode.HYBRID) {
                                val currentSelected = prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
                                currentSelected.removeAll(successful)
                                putStringSet(MainActivity.KEY_SELECTED_APPS, currentSelected)
                                putInt(MainActivity.KEY_SELECTED_COUNT, currentSelected.size)
                            }
                        }
                    }
                    putLong(MainActivity.KEY_FIREWALL_UPDATE_TS, System.currentTimeMillis())
                    apply()
                }

                if (automationEvent && hadCommandFailure) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.screen_lock_mode_operation_failed), Toast.LENGTH_SHORT).show()
                    }
                }

                // Notify widget to update
                val updateIntent = Intent(context, FirewallWidgetProvider::class.java)
                updateIntent.action = MainActivity.ACTION_FIREWALL_STATE_CHANGED
                context.sendBroadcast(updateIntent)
                ScreenLockMonitorService.sync(context)

            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.error_format, t.message), Toast.LENGTH_SHORT).show()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
