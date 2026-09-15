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
import com.arslan.shizuwall.firewall.PerUidFirewall
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
import com.arslan.shizuwall.firewall.FirewallCommands
import com.arslan.shizuwall.firewall.FirewallTargets

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
        
        android.util.Log.d(TAG, "Received action: ${intent.action}")
        
        if (intent.action != MainActivity.ACTION_FIREWALL_CONTROL) return

        val pending = goAsync()
        val enabled = intent.getBooleanExtra(MainActivity.EXTRA_FIREWALL_ENABLED, false)
        val csv = intent.getStringExtra(MainActivity.EXTRA_PACKAGES_CSV)
        val automationEvent = intent.getBooleanExtra(EXTRA_AUTOMATION_EVENT, false)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                val mode = prefs.getString(MainActivity.KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"
                val firewallMode = FirewallMode.fromName(prefs.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name))

                val rawPackages = if (!csv.isNullOrBlank()) {
                    csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                } else {
                    val saved = prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())?.toList() ?: emptyList()
                    if (firewallMode == FirewallMode.WHITELIST) {
                        val showSys = prefs.getBoolean(MainActivity.KEY_SHOW_SYSTEM_APPS, false)
                        com.arslan.shizuwall.utils.WhitelistFilter.compute(context, saved, showSys).toBlock
                    } else {
                        saved
                    }
                }

                val whitelistAllowApps = if (enabled && !csv.isNullOrBlank() && firewallMode == FirewallMode.WHITELIST) {
                    emptyList()
                } else if (enabled && csv.isNullOrBlank() && firewallMode == FirewallMode.WHITELIST) {
                    val saved = prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())?.toList() ?: emptyList()
                    val showSys = prefs.getBoolean(MainActivity.KEY_SHOW_SYSTEM_APPS, false)
                    com.arslan.shizuwall.utils.WhitelistFilter.compute(context, saved, showSys).toAllow
                } else {
                    emptyList()
                }

                val requestedPackages = rawPackages.filterNot { ShizukuPackageResolver.isShizukuPackage(context, it) || it == context.packageName }
                val packages = if (enabled) {
                    FirewallTargets.effectiveBlockList(
                        firewallMode,
                        requestedPackages.filter { PerUidFirewall.isBlockableKey(context, it) },
                        ScreenLockModeReceiver.isDeviceLocked(context),
                        FirewallTargets.parseAppModes(prefs.getString(MainActivity.KEY_APP_MODES, "{}"))
                    )
                } else {
                    requestedPackages
                }

                android.util.Log.d(TAG, "Packages to process: $packages, enabled: $enabled, firewallMode: $firewallMode")

                if (enabled && packages.isEmpty() && !firewallMode.allowsDynamicSelection()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.no_apps_selected), Toast.LENGTH_SHORT).show()
                    }
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
                            if (mode == "LADB") {
                                context.getString(R.string.daemon_not_running)
                            } else if (mode == "ROOT") {
                                context.getString(R.string.root_not_found_message)
                            } else {
                                context.getString(R.string.shizuku_not_available)
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                suspend fun execShell(cmd: String): ShellResult {
                    return ShellExecutorProvider.forContext(context).exec(cmd)
                }

                suspend fun execShellBatch(cmds: List<String>): List<ShellResult> {
                    if (cmds.isEmpty()) return emptyList()
                    return ShellExecutorProvider.forContext(context).execBatch(cmds)
                }

                val successful = mutableListOf<String>()
                var globalCommandSuccess = true
                var hadCommandFailure = false

                if (enabled) {
                    val chainEnableResult = execShell(FirewallCommands.CHAIN3_ENABLE)
                    globalCommandSuccess = chainEnableResult.success
                    if (!chainEnableResult.success && mode == "LADB") {
                        appendLadbFailureLog(context, "Failed to enable firewall chain", chainEnableResult)
                    }
                    if (!chainEnableResult.success) {
                        hadCommandFailure = true
                    }
                    if (globalCommandSuccess) {
                        val blockResults = execShellBatch(
                            FirewallCommands.blockAll(packages)
                        )
                        packages.forEachIndexed { index, pkg ->
                            val blockResult = blockResults[index]
                            if (blockResult.isEffectivelySuccess) {
                                successful.add(pkg)
                            } else if (mode == "LADB") {
                                appendLadbFailureLog(context, "Failed to block package $pkg", blockResult)
                                hadCommandFailure = true
                            } else {
                                hadCommandFailure = true
                            }
                        }
                        val allowResults = execShellBatch(
                            FirewallCommands.unblockAll(whitelistAllowApps)
                        )
                        whitelistAllowApps.forEachIndexed { index, pkg ->
                            val allowResult = allowResults[index]
                            if (!allowResult.isEffectivelySuccess && mode == "LADB") {
                                appendLadbFailureLog(context, "Failed to allow package $pkg", allowResult)
                            }
                        }
                    }
                } else {
                    val unblockResults = execShellBatch(
                        FirewallCommands.unblockAll(packages)
                    )
                    packages.forEachIndexed { index, pkg ->
                        val allowResult = unblockResults[index]
                        if (allowResult.isEffectivelySuccess) {
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
                        val chainDisableResult = execShell(FirewallCommands.CHAIN3_DISABLE)
                        globalCommandSuccess = chainDisableResult.success
                        if (!chainDisableResult.success && mode == "LADB") {
                            appendLadbFailureLog(context, "Failed to disable firewall chain", chainDisableResult)
                        }
                        if (!chainDisableResult.success) {
                            hadCommandFailure = true
                        }
                    }
                }

                prefs.edit().apply {
                    if (enabled && globalCommandSuccess && (successful.isNotEmpty() || firewallMode.allowsDynamicSelection())) {
                        putBoolean(MainActivity.KEY_FIREWALL_ENABLED, true)
                        putLong(MainActivity.KEY_FIREWALL_SAVED_ELAPSED, SystemClock.elapsedRealtime())
                        
                        if (!csv.isNullOrBlank()) {
                            val currentActive = prefs.getStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
                            currentActive.addAll(successful)
                            putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, currentActive)
                        } else {
                            putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, successful.toSet())
                        }
                        
                        if (firewallMode.allowsDynamicSelection() && successful.isNotEmpty() && firewallMode != FirewallMode.WHITELIST) {
                            val currentSelected = prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
                            currentSelected.addAll(successful)
                            putStringSet(MainActivity.KEY_SELECTED_APPS, currentSelected)
                            putInt(MainActivity.KEY_SELECTED_COUNT, currentSelected.size)
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
                                if (!automationEvent) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, context.getString(R.string.failed_to_disable_firewall), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else {
                            val currentActive = prefs.getStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
                            currentActive.removeAll(successful)
                            putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, currentActive)

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
                    val failureMessage = if (firewallMode == FirewallMode.SCREEN_LOCK_MODE) {
                        context.getString(R.string.screen_lock_mode_operation_failed)
                    } else if (enabled) {
                        context.getString(R.string.failed_to_enable_firewall)
                    } else {
                        context.getString(R.string.failed_to_disable_firewall)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
                    }
                }

                val updateIntent = Intent(context, FirewallWidgetProvider::class.java)
                updateIntent.action = MainActivity.ACTION_FIREWALL_STATE_CHANGED
                context.sendBroadcast(updateIntent)
                ScreenLockMonitorService.sync(context)
                com.arslan.shizuwall.services.ForegroundDetectionService.sync(context)

            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.error_format, t.message), Toast.LENGTH_SHORT).show()
                }
            } finally {
                runCatching { pending.finish() }
            }
        }
    }
}
