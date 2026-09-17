package com.arslan.shizuwall.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import com.arslan.shizuwall.FirewallMode
import com.arslan.shizuwall.R
import com.arslan.shizuwall.daemon.PersistentDaemonManager
import com.arslan.shizuwall.firewall.FirewallCommands
import com.arslan.shizuwall.firewall.FirewallTargets
import com.arslan.shizuwall.firewall.PerUidFirewall
import com.arslan.shizuwall.ladb.LadbLogStore
import com.arslan.shizuwall.profiles.ProfileTileSlots
import com.arslan.shizuwall.receivers.FirewallControlReceiver
import com.arslan.shizuwall.receivers.ScreenLockModeReceiver
import com.arslan.shizuwall.services.FloatingButtonService
import com.arslan.shizuwall.services.ForegroundDetectionService
import com.arslan.shizuwall.services.ScreenLockMonitorService
import com.arslan.shizuwall.shell.RootShellExecutor
import com.arslan.shizuwall.shell.ShellExecutorProvider
import com.arslan.shizuwall.shell.ShellResult
import com.arslan.shizuwall.ui.MainActivity
import com.arslan.shizuwall.widgets.FirewallWidgetProvider
import com.arslan.shizuwall.widgets.ProfileWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

object FirewallUtils {

    data class EnableTargets(val block: List<String>, val allow: List<String>)

    private val stateLock = Mutex()

    fun firewallMode(prefs: SharedPreferences): FirewallMode =
        FirewallMode.fromName(prefs.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name))

    fun loadFirewallEnabled(prefs: SharedPreferences): Boolean {
        val enabled = prefs.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
        if (!enabled) return false
        val savedElapsed = prefs.getLong(MainActivity.KEY_FIREWALL_SAVED_ELAPSED, -1L)
        if (savedElapsed == -1L) return false
        return SystemClock.elapsedRealtime() >= savedElapsed
    }

    fun loadSelectedApps(context: Context, prefs: SharedPreferences): List<String> {
        val selfPkg = context.packageName
        return prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())
            ?.filterNot { ShizukuPackageResolver.isShizukuPackage(context, it) || it == selfPkg }
            ?.toList() ?: emptyList()
    }

    fun applyNewAppPolicy(context: Context, key: String): Boolean {
        val packageName = AppKey.normalize(key)
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
        val mode = firewallMode(prefs)
        val autoFirewalled = enabled &&
            prefs.getBoolean(MainActivity.KEY_AUTO_FIREWALL_NEW_APPS, false) &&
            mode != FirewallMode.WHITELIST
        if (autoFirewalled) {
            val selected = prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
            if (selected.add(packageName)) {
                prefs.edit()
                    .putStringSet(MainActivity.KEY_SELECTED_APPS, selected)
                    .putInt(MainActivity.KEY_SELECTED_COUNT, selected.size)
                    .apply()
            }
        }
        if (autoFirewalled || (enabled && mode == FirewallMode.WHITELIST)) {
            context.sendBroadcast(Intent(context, FirewallControlReceiver::class.java).apply {
                action = MainActivity.ACTION_FIREWALL_CONTROL
                putExtra(MainActivity.EXTRA_FIREWALL_ENABLED, true)
                putExtra(MainActivity.EXTRA_PACKAGES_CSV, packageName)
                putExtra(FirewallControlReceiver.EXTRA_AUTOMATION_EVENT, true)
            })
        }
        return autoFirewalled
    }

    fun loadActivePackages(prefs: SharedPreferences): Set<String> {
        return prefs.getStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet()) ?: emptySet()
    }

    fun loadExternalPackages(prefs: SharedPreferences): Set<String> {
        return prefs.getStringSet(MainActivity.KEY_EXTERNAL_PACKAGES, emptySet()) ?: emptySet()
    }

    fun saveFirewallEnabled(context: Context, prefs: SharedPreferences, enabled: Boolean) {
        val elapsed = SystemClock.elapsedRealtime()
        val now = System.currentTimeMillis()
        val write: SharedPreferences.Editor.() -> Unit = {
            putBoolean(MainActivity.KEY_FIREWALL_ENABLED, enabled)
            if (enabled) putLong(MainActivity.KEY_FIREWALL_SAVED_ELAPSED, elapsed) else remove(MainActivity.KEY_FIREWALL_SAVED_ELAPSED)
            putLong(MainActivity.KEY_FIREWALL_UPDATE_TS, now)
            apply()
        }
        prefs.edit().write()
        try {
            context.createDeviceProtectedStorageContext()
                .getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                .edit().write()
        } catch (_: Exception) {
        }

        val intent = Intent(context, FirewallWidgetProvider::class.java)
        intent.action = MainActivity.ACTION_FIREWALL_STATE_CHANGED
        context.sendBroadcast(intent)

        notifyProfileSurfaces(context)

        ScreenLockMonitorService.sync(context)
        ForegroundDetectionService.sync(context)
    }

    fun notifyProfileSurfaces(context: Context) {
        ProfileTileSlots.refreshTiles(context)
        ProfileWidgetProvider.refreshAll(context)
    }

    fun saveActivePackages(prefs: SharedPreferences, packages: Set<String>) {
        prefs.edit()
            .putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, packages)
            .putLong(MainActivity.KEY_FIREWALL_UPDATE_TS, System.currentTimeMillis())
            .apply()
    }

    private fun enableTargets(context: Context, prefs: SharedPreferences): EnableTargets? {
        val selected = loadSelectedApps(context, prefs)
        val mode = firewallMode(prefs)
        val targets = if (mode == FirewallMode.WHITELIST) {
            val result = WhitelistFilter.compute(context, selected, prefs.getBoolean(MainActivity.KEY_SHOW_SYSTEM_APPS, false))
            EnableTargets(result.toBlock, result.toAllow)
        } else {
            EnableTargets(selected, emptyList())
        }
        if (targets.block.isEmpty() && !mode.allowsDynamicSelection()) {
            Toast.makeText(context, context.getString(R.string.no_apps_selected), Toast.LENGTH_SHORT).show()
            return null
        }
        return targets
    }

    suspend fun toggle(context: Context, prefs: SharedPreferences, enable: Boolean): Boolean {
        val targets = withContext(Dispatchers.Main) {
            when {
                !checkBackendReady(context) -> null
                !enable -> EnableTargets(emptyList(), emptyList())
                else -> enableTargets(context, prefs)
            }
        } ?: return false
        return if (enable) enable(context, prefs, targets) else disable(context, prefs)
    }

    suspend fun applyDelta(
        context: Context,
        prefs: SharedPreferences,
        keys: List<String>,
        block: Boolean,
        external: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val candidates = keys.map { AppKey.normalize(it) }.filterNot { isSelfOrBackend(context, it) }
        val targets = if (block) candidates.filter { PerUidFirewall.isBlockableKey(context, it) } else candidates
        if (targets.isEmpty()) return@withContext false
        stateLock.withLock {
            val executor = ShellExecutorProvider.forContext(context)
            if (block && !executor.exec(FirewallCommands.CHAIN3_ENABLE).isEffectivelySuccess) {
                return@withLock false
            }
            val results = executor.execBatch(FirewallCommands.networkingAll(targets, !block))
            logFailures(context, targets, results)
            val applied = targets.filterIndexed { index, _ -> results[index].isEffectivelySuccess }
            writeSets(prefs, applied, block, external)
            if (!block && loadExternalPackages(prefs).isEmpty() && !loadFirewallEnabled(prefs)) {
                executor.exec(FirewallCommands.CHAIN3_DISABLE)
            }
            applied.size == targets.size
        }
    }

    private fun writeSets(prefs: SharedPreferences, applied: List<String>, block: Boolean, external: Boolean) {
        val active = loadActivePackages(prefs).toMutableSet()
        val owned = loadExternalPackages(prefs).toMutableSet()
        if (block) {
            active.addAll(applied)
            if (external) owned.addAll(applied)
        } else {
            active.removeAll(applied.toSet())
            owned.removeAll(applied.toSet())
        }
        prefs.edit()
            .putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, active)
            .putStringSet(MainActivity.KEY_EXTERNAL_PACKAGES, owned)
            .putLong(MainActivity.KEY_FIREWALL_SAVED_ELAPSED, SystemClock.elapsedRealtime())
            .putLong(MainActivity.KEY_FIREWALL_UPDATE_TS, System.currentTimeMillis())
            .apply()
    }

    private suspend fun enable(context: Context, prefs: SharedPreferences, targets: EnableTargets): Boolean = withContext(Dispatchers.IO) {
        val mode = firewallMode(prefs)
        val executor = ShellExecutorProvider.forContext(context)
        val successful = mutableListOf<String>()
        stateLock.withLock {
            if (executor.exec(FirewallCommands.CHAIN3_ENABLE).isEffectivelySuccess) {
                val effective = FirewallTargets.effectiveBlockList(
                    mode,
                    targets.block,
                    ScreenLockModeReceiver.isDeviceLocked(context),
                    FirewallTargets.parseAppModes(prefs.getString(MainActivity.KEY_APP_MODES, "{}"))
                )
                val external = loadExternalPackages(prefs)
                val toBlock = (effective + external).distinct().filterNot { isSelfOrBackend(context, it) }
                val toAllow = targets.allow.filterNot { isSelfOrBackend(context, it) }
                val stale = loadActivePackages(prefs) - toBlock.toSet()
                executor.execBatch(FirewallCommands.unblockAll(stale.toList() + toAllow))
                val results = executor.execBatch(FirewallCommands.blockAll(toBlock))
                logFailures(context, toBlock, results)
                toBlock.forEachIndexed { index, pkg -> if (results[index].isEffectivelySuccess) successful.add(pkg) }
            }

            if (successful.isNotEmpty() || mode.allowsDynamicSelection()) {
                saveFirewallEnabled(context, prefs, true)
                saveActivePackages(prefs, successful.toSet())
                if (prefs.getBoolean(FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, false)) {
                    withContext(Dispatchers.Main) { FloatingButtonService.start(context) }
                }
                true
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.failed_to_enable_firewall), Toast.LENGTH_SHORT).show()
                }
                false
            }
        }
    }

    private suspend fun disable(context: Context, prefs: SharedPreferences): Boolean = withContext(Dispatchers.IO) {
        val mode = firewallMode(prefs)
        stateLock.withLock {
            val external = loadExternalPackages(prefs)
            val toUnblock = (loadActivePackages(prefs) - external).toMutableList()
            if (mode.requiresForegroundDetection()) {
                val currentFgApp = prefs.getString(MainActivity.KEY_SMART_FOREGROUND_APP, null)
                if (!currentFgApp.isNullOrEmpty() && !toUnblock.contains(currentFgApp)) toUnblock.add(currentFgApp)
            }
            val executor = ShellExecutorProvider.forContext(context)
            val targets = toUnblock.filterNot { isSelfOrBackend(context, it) }
            val results = executor.execBatch(FirewallCommands.unblockAll(targets))
            logFailures(context, targets, results)
            val chainOk = external.isNotEmpty() ||
                executor.exec(FirewallCommands.CHAIN3_DISABLE).isEffectivelySuccess
            if (mode.requiresForegroundDetection()) {
                prefs.edit()
                    .putString(MainActivity.KEY_SMART_FOREGROUND_APP, "")
                    .putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, external)
                    .apply()
            }

            val ok = chainOk && results.all { it.isEffectivelySuccess }
            if (ok) {
                saveFirewallEnabled(context, prefs, false)
                saveActivePackages(prefs, external)
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.failed_to_disable_firewall), Toast.LENGTH_SHORT).show()
                }
            }
            ok
        }
    }

    private fun isSelfOrBackend(context: Context, key: String): Boolean =
        key == context.packageName || ShizukuPackageResolver.isShizukuPackage(context, key)

    private fun logFailures(context: Context, keys: List<String>, results: List<ShellResult>) {
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(MainActivity.KEY_WORKING_MODE, null) != "LADB") return
        keys.forEachIndexed { index, key ->
            val result = results[index]
            if (result.isEffectivelySuccess) return@forEachIndexed
            val detail = result.stderr.ifBlank { result.stdout }.trim().ifEmpty { "exit=${result.exitCode}" }
            LadbLogStore.append(context, "Firewall daemon failure: $key | $detail")
        }
    }

    fun checkBackendReady(context: Context, showToast: Boolean = true): Boolean {
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val mode = prefs.getString(MainActivity.KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"
        val error = when (mode) {
            "ROOT" -> if (RootShellExecutor.hasRootAccess()) 0 else R.string.root_not_found_message
            "LADB" -> if (PersistentDaemonManager(context).isDaemonRunning()) 0 else R.string.daemon_not_running
            else -> when {
                !runCatching { Shizuku.pingBinder() }.getOrDefault(false) -> R.string.shizuku_not_running
                runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false) -> 0
                else -> R.string.shizuku_permission_required
            }
        }
        if (error != 0 && showToast) Toast.makeText(context, context.getString(error), Toast.LENGTH_SHORT).show()
        return error == 0
    }
}
