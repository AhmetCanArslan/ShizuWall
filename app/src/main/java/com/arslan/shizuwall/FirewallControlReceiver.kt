package com.arslan.shizuwall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.lang.reflect.Method

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
        private val SHIZUKU_NEW_PROCESS_METHOD: Method by lazy {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != MainActivity.ACTION_FIREWALL_CONTROL) return

        // Use goAsync pattern via coroutine to avoid blocking receiver thread.
        val pending = goAsync()
        val enabled = intent.getBooleanExtra(MainActivity.EXTRA_FIREWALL_ENABLED, false)
        val csv = intent.getStringExtra(MainActivity.EXTRA_PACKAGES_CSV)

        // Resolve package list: CSV -> saved selected apps
        val packages = if (!csv.isNullOrBlank()) {
            csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
            prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())?.toList() ?: emptyList()
        }

        if (enabled && packages.isEmpty()) {
            Toast.makeText(context, "No apps selected", Toast.LENGTH_SHORT).show()
            pending.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Ensure Shizuku available and permission granted
                val shizukuAvailable = try {
                    Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                } catch (t: Throwable) {
                    false
                }

                val successful = mutableListOf<String>()

                if (shizukuAvailable) {
                    if (enabled) {
                        // enable chain3
                        runShizukuShellCommand("cmd connectivity set-chain3-enabled true")
                        for (pkg in packages) {
                            if (runShizukuShellCommand("cmd connectivity set-package-networking-enabled false $pkg")) {
                                successful.add(pkg)
                            }
                        }
                    } else {
                        for (pkg in packages) {
                            if (runShizukuShellCommand("cmd connectivity set-package-networking-enabled true $pkg")) {
                                successful.add(pkg)
                            }
                        }
                        runShizukuShellCommand("cmd connectivity set-chain3-enabled false")
                    }
                }

                // Persist state to shared prefs (same keys MainActivity uses)
                val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                prefs.edit().apply {
                    if (enabled && successful.isNotEmpty()) {
                        putBoolean(MainActivity.KEY_FIREWALL_ENABLED, true)
                        putLong(MainActivity.KEY_FIREWALL_SAVED_ELAPSED, SystemClock.elapsedRealtime())
                        putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, successful.toSet())
                    } else {
                        putBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
                        remove(MainActivity.KEY_FIREWALL_SAVED_ELAPSED)
                        putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet())
                    }
                    putLong(MainActivity.KEY_FIREWALL_UPDATE_TS, System.currentTimeMillis())
                    apply()
                }

            } catch (_: Throwable) {
                // best-effort: swallow errors to avoid crashing receiver
            } finally {
                pending.finish()
            }
        }
    }

    private fun runShizukuShellCommand(command: String): Boolean {
        return try {
            val process = SHIZUKU_NEW_PROCESS_METHOD.invoke(
                null,
                arrayOf("/system/bin/sh", "-c", command),
                null,
                null
            ) as? ShizukuRemoteProcess ?: return false

            process.outputStream.close()
            process.inputStream.close()
            process.errorStream.close()
            val exitCode = process.waitFor()
            process.destroy()
            exitCode == 0
        } catch (e: Throwable) {
            false
        }
    }
}
