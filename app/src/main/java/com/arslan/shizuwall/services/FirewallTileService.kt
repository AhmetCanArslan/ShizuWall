package com.arslan.shizuwall.services

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.arslan.shizuwall.FirewallMode
import com.arslan.shizuwall.R
import com.arslan.shizuwall.shell.ShellExecutorBlocking
import com.arslan.shizuwall.receivers.ScreenLockModeReceiver
import com.arslan.shizuwall.ui.MainActivity
import kotlinx.coroutines.*
import com.arslan.shizuwall.utils.FirewallUtils
import com.arslan.shizuwall.utils.ShizukuPackageResolver
import com.arslan.shizuwall.utils.WhitelistFilter
import com.arslan.shizuwall.firewall.FirewallCommands

class FirewallTileService : TileService() {

    private lateinit var sharedPreferences: SharedPreferences
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    // SharedPreferences listener to update tile whenever relevant prefs change
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == MainActivity.KEY_FIREWALL_ENABLED ||
            key == MainActivity.KEY_ACTIVE_PACKAGES ||
            key == MainActivity.KEY_FIREWALL_SAVED_ELAPSED ||
            key == MainActivity.KEY_FIREWALL_MODE
        ) {
            // update UI to reflect new saved state
            updateTile()
        }
    }

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
        // register prefs listener so tile updates without broadcasts
        try {
            sharedPreferences.registerOnSharedPreferenceChangeListener(prefsListener)
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        try {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (e: IllegalArgumentException) {
            // not registered
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onClick() {
        super.onClick()
        val isEnabled = loadFirewallEnabled()
        if (isEnabled) {
            // Disable firewall
            if (!checkBackendReady()) return
            scope.launch {
                applyDisableFirewall()
            }
        } else {
            // Enable firewall
            val selectedApps = loadSelectedApps()
            val firewallMode = FirewallMode.fromName(sharedPreferences.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name))
            
            val targetApps: List<String>
            val whitelistAllowApps: List<String>
            if (firewallMode == FirewallMode.WHITELIST) {
                val showSystemApps = sharedPreferences.getBoolean(MainActivity.KEY_SHOW_SYSTEM_APPS, false)
                val result = WhitelistFilter.compute(this, selectedApps, showSystemApps)
                targetApps = result.toBlock
                whitelistAllowApps = result.toAllow
            } else {
                targetApps = selectedApps
                whitelistAllowApps = emptyList()
            }

            if (targetApps.isEmpty() && !firewallMode.allowsDynamicSelection()) {
                Toast.makeText(this@FirewallTileService, getString(R.string.no_apps_selected), Toast.LENGTH_SHORT).show()
                return
            }
            if (!checkBackendReady()) return
            scope.launch {
                applyEnableFirewall(targetApps, whitelistAllowApps)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (_: Exception) {}
        job.cancel()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isEnabled = loadFirewallEnabled()
        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.firewall)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_quick_tile)
        tile.updateTile()
    }

    private fun loadFirewallEnabled(): Boolean = FirewallUtils.loadFirewallEnabled(sharedPreferences)

    private fun loadSelectedApps(): List<String> = FirewallUtils.loadSelectedApps(this, sharedPreferences)

    private fun checkBackendReady(): Boolean = FirewallUtils.checkBackendReady(this)

    private suspend fun applyEnableFirewall(packageNames: List<String>, whitelistAllowApps: List<String> = emptyList()) {
        val firewallMode = FirewallMode.fromName(
            sharedPreferences.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name)
        )

        // For tracking modes, start the foreground detection service
        if (firewallMode.requiresForegroundDetection()) {
            ForegroundDetectionService.start(this@FirewallTileService)
        }

        withContext(Dispatchers.IO) {
            val successful = enableFirewall(packageNames, whitelistAllowApps)
            if (successful.isNotEmpty() || firewallMode.allowsDynamicSelection()) {
                saveFirewallEnabled(true)
                saveActivePackages(successful.toSet())
                
                withContext(Dispatchers.Main) {
                    if (sharedPreferences.getBoolean(FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, false)) {
                        FloatingButtonService.start(this@FirewallTileService)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FirewallTileService, getString(R.string.failed_to_enable_firewall), Toast.LENGTH_SHORT).show()
                }
            }
        }
        updateTile()
    }

    private suspend fun applyDisableFirewall() {
        val activePackages = loadActivePackages()
        withContext(Dispatchers.IO) {
            val success = disableFirewall(activePackages.toList())
            if (success) {
                saveFirewallEnabled(false)
                saveActivePackages(emptySet())
            } else {
                // Show error if disable failed
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FirewallTileService, getString(R.string.failed_to_disable_firewall), Toast.LENGTH_SHORT).show()
                }
            }
        }
        updateTile()
    }

    private fun enableFirewall(packageNames: List<String>, whitelistAllowApps: List<String> = emptyList()): List<String> {
        val successful = mutableListOf<String>()
        if (!ShellExecutorBlocking.runBlockingSuccess(this, FirewallCommands.CHAIN3_ENABLE)) return successful

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
        val toBlock = packageNames.filterNot { it == selfPkg || ShizukuPackageResolver.isShizukuPackage(this, it) }
        val toAllow = whitelistAllowApps.filterNot { it == selfPkg || ShizukuPackageResolver.isShizukuPackage(this, it) }

        val blockResults = ShellExecutorBlocking.execBatchBlocking(
            this,
            FirewallCommands.blockAll(toBlock)
        )
        toBlock.forEachIndexed { index, pkg ->
            if (blockResults[index].isEffectivelySuccess) successful.add(pkg)
        }

        ShellExecutorBlocking.execBatchBlocking(
            this,
            FirewallCommands.unblockAll(toAllow)
        )

        return successful
    }

    private fun disableFirewall(packageNames: List<String>): Boolean {
        var allSuccessful = true
        val selfPkg = packageName
        val toUnblock = packageNames.toMutableList()

        val firewallMode = FirewallMode.fromName(
            sharedPreferences.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name)
        )

        if (firewallMode == FirewallMode.SMART_FOREGROUND || firewallMode == FirewallMode.FOCUS_TRACKER) {
            val currentFgApp = sharedPreferences.getString(MainActivity.KEY_SMART_FOREGROUND_APP, null)
            if (!currentFgApp.isNullOrEmpty() && !toUnblock.contains(currentFgApp)) {
                toUnblock.add(currentFgApp)
            }
        }

        // never target the app itself or Shizuku
        val unblockTargets = toUnblock.filterNot { it == selfPkg || ShizukuPackageResolver.isShizukuPackage(this, it) }
        val unblockResults = ShellExecutorBlocking.execBatchBlocking(
            this,
            FirewallCommands.unblockAll(unblockTargets)
        )
        if (unblockResults.any { !it.isEffectivelySuccess }) allSuccessful = false
        if (!ShellExecutorBlocking.runBlockingSuccess(this, FirewallCommands.CHAIN3_DISABLE)) {
            allSuccessful = false
        }

        if (firewallMode == FirewallMode.SMART_FOREGROUND || firewallMode == FirewallMode.FOCUS_TRACKER) {
            sharedPreferences.edit()
                .putString(MainActivity.KEY_SMART_FOREGROUND_APP, "")
                .putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet())
                .apply()
        }

        return allSuccessful
    }

    private fun saveFirewallEnabled(enabled: Boolean) {
        FirewallUtils.saveFirewallEnabled(this, sharedPreferences, enabled)
    }

    private fun saveActivePackages(packages: Set<String>) {
        FirewallUtils.saveActivePackages(sharedPreferences, packages)
    }

    private fun loadActivePackages(): Set<String> = FirewallUtils.loadActivePackages(sharedPreferences)

}
