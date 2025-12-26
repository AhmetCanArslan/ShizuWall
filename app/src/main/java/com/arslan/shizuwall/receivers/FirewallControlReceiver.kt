package com.arslan.shizuwall.receivers

import android.content.BroadcastReceiver
import android.content.Context
import com.arslan.shizuwall.widgets.FirewallWidgetProvider
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import com.arslan.shizuwall.R
import com.arslan.shizuwall.shell.ShellExecutorProvider
import com.arslan.shizuwall.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    override fun onReceive(context: Context, intent: Intent) {
        
        // Log received action to help debug ADB commands
        android.util.Log.d("FirewallControl", "Received action: ${intent.action}")
        
        if (intent.action != MainActivity.ACTION_FIREWALL_CONTROL) return

        // Use goAsync pattern via coroutine to avoid blocking receiver thread.
        val pending = goAsync()
        val enabled = intent.getBooleanExtra(MainActivity.EXTRA_FIREWALL_ENABLED, false)
        val csv = intent.getStringExtra(MainActivity.EXTRA_PACKAGES_CSV)

        // Resolve package list: CSV -> saved selected apps
        val rawPackages = if (!csv.isNullOrBlank()) {
            csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
            prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())?.toList() ?: emptyList()
        }

        // filter out any Shizuku packages and this app itself from incoming list
        val packages = rawPackages.filterNot { it == "moe.shizuku.privileged.api" || it == context.packageName }

        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val adaptiveMode = prefs.getBoolean(MainActivity.KEY_ADAPTIVE_MODE, false)

        if (enabled && packages.isEmpty() && !adaptiveMode) {
            Toast.makeText(context, "No apps selected", Toast.LENGTH_SHORT).show()
            pending.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefsLocal = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                val mode = prefsLocal.getString(MainActivity.KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"

                val backendReady = if (mode == "LADB") {
                    val daemonManager = com.arslan.shizuwall.daemon.PersistentDaemonManager(context)
                    try {
                        daemonManager.isDaemonRunning()
                    } catch (e: Exception) {
                        false
                    }
                } else {
                    try {
                        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    } catch (_: Throwable) {
                        false
                    }
                }

                if (!backendReady) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            if (mode == "LADB") context.getString(R.string.daemon_not_running) else "Shizuku not available or permission denied",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    pending.finish()
                    return@launch
                }

                suspend fun runShell(cmd: String): Boolean {
                    return ShellExecutorProvider.forContext(context).exec(cmd).success
                }

                val successful = mutableListOf<String>()
                var globalCommandSuccess = true

                if (enabled) {
                    // enable chain3
                    globalCommandSuccess = runShell("cmd connectivity set-chain3-enabled true")
                    if (globalCommandSuccess) {
                        for (pkg in packages) {
                            if (runShell("cmd connectivity set-package-networking-enabled false $pkg")) {
                                successful.add(pkg)
                            }
                        }
                    }
                } else {
                    for (pkg in packages) {
                        if (runShell("cmd connectivity set-package-networking-enabled true $pkg")) {
                            successful.add(pkg)
                        }
                    }
                    val isGlobalDisable = csv.isNullOrBlank()
                    if (!adaptiveMode || isGlobalDisable) {
                        globalCommandSuccess = runShell("cmd connectivity set-chain3-enabled false")
                    }
                }

                // Persist state to shared prefs (same keys MainActivity uses)
                // val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                prefs.edit().apply {
                    if (enabled && globalCommandSuccess && (successful.isNotEmpty() || adaptiveMode)) {
                        putBoolean(MainActivity.KEY_FIREWALL_ENABLED, true)
                        putLong(MainActivity.KEY_FIREWALL_SAVED_ELAPSED, SystemClock.elapsedRealtime())
                        putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, successful.toSet())
                        
                        // In Adaptive Mode, sync the selected apps list with what was just enabled
                        if (adaptiveMode && successful.isNotEmpty()) {
                            val currentSelected = prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
                            currentSelected.addAll(successful)
                            putStringSet(MainActivity.KEY_SELECTED_APPS, currentSelected)
                            putInt(MainActivity.KEY_SELECTED_COUNT, currentSelected.size)
                        }
                    } else {
                        val isGlobalDisable = csv.isNullOrBlank()
                        if (!adaptiveMode || isGlobalDisable) {
                            if (globalCommandSuccess) {
                                putBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
                                remove(MainActivity.KEY_FIREWALL_SAVED_ELAPSED)
                                putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet())
                            } else {
                                // Global disable failed, show error but don't update state
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Failed to disable firewall", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            // Partial disable (unblock specific apps), firewall stays ON
                            val currentActive = prefs.getStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
                            currentActive.removeAll(successful)
                            putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, currentActive)
                            
                            // Also update selected apps to reflect unblocking
                            val currentSelected = prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
                            currentSelected.removeAll(successful)
                            putStringSet(MainActivity.KEY_SELECTED_APPS, currentSelected)
                            putInt(MainActivity.KEY_SELECTED_COUNT, currentSelected.size)
                        }
                    }
                    putLong(MainActivity.KEY_FIREWALL_UPDATE_TS, System.currentTimeMillis())
                    apply()
                }

                // Notify widget to update
                val updateIntent = Intent(context, FirewallWidgetProvider::class.java)
                updateIntent.action = MainActivity.ACTION_FIREWALL_STATE_CHANGED
                context.sendBroadcast(updateIntent)

            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
