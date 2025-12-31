package com.arslan.shizuwall.services

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.arslan.shizuwall.R
import com.arslan.shizuwall.shell.ShellExecutorBlocking
import com.arslan.shizuwall.ui.MainActivity
import kotlinx.coroutines.*
import com.arslan.shizuwall.widgets.FirewallWidgetProvider
import rikka.shizuku.Shizuku

class FirewallTileService : TileService() {

    private lateinit var sharedPreferences: SharedPreferences
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    // SharedPreferences listener to update tile whenever relevant prefs change
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == MainActivity.KEY_FIREWALL_ENABLED ||
            key == MainActivity.KEY_ACTIVE_PACKAGES ||
            key == MainActivity.KEY_FIREWALL_SAVED_ELAPSED
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
            val adaptiveMode = sharedPreferences.getBoolean(MainActivity.KEY_ADAPTIVE_MODE, false)
            
            if (selectedApps.isEmpty() && !adaptiveMode) {
                Toast.makeText(this@FirewallTileService, getString(R.string.no_apps_selected), Toast.LENGTH_SHORT).show()
                return
            }
            if (!checkBackendReady()) return
            scope.launch {
                applyEnableFirewall(selectedApps)
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

    private fun loadFirewallEnabled(): Boolean {
        val enabled = sharedPreferences.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
        if (!enabled) return false
        val savedElapsed = sharedPreferences.getLong(MainActivity.KEY_FIREWALL_SAVED_ELAPSED, -1L)
        if (savedElapsed == -1L) return false
        val currentElapsed = android.os.SystemClock.elapsedRealtime()
        return currentElapsed >= savedElapsed
    }

    private fun loadSelectedApps(): List<String> {
        val selfPkg = packageName
        return sharedPreferences.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())
            ?.filterNot { it == "moe.shizuku.privileged.api" || it == selfPkg }
            ?.toList() ?: emptyList()
    }

    private fun checkBackendReady(): Boolean {
        val mode = sharedPreferences.getString(MainActivity.KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"
        return if (mode == "LADB") {
            val daemonManager = com.arslan.shizuwall.daemon.PersistentDaemonManager(this)
            
            if (daemonManager.isDaemonRunning()) {
                true
            } else {
                Toast.makeText(this, getString(R.string.daemon_not_running), Toast.LENGTH_SHORT).show()
                try {
                    val i = Intent(this, com.arslan.shizuwall.LadbSetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(i)
                } catch (_: Exception) {
                }
                false
            }
        } else {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, getString(R.string.shizuku_not_running), Toast.LENGTH_SHORT).show()
                return false
            }
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.shizuku_permission_required), Toast.LENGTH_SHORT).show()
                return false
            }
            true
        }
    }

    private suspend fun applyEnableFirewall(packageNames: List<String>) {
        withContext(Dispatchers.IO) {
            val successful = enableFirewall(packageNames)
            if (successful.isNotEmpty()) {
                saveFirewallEnabled(true)
                saveActivePackages(successful.toSet())
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
                    Toast.makeText(this@FirewallTileService, "Failed to disable firewall", Toast.LENGTH_SHORT).show()
                }
            }
        }
        updateTile()
    }

    private fun enableFirewall(packageNames: List<String>): List<String> {
        val successful = mutableListOf<String>()
        if (!ShellExecutorBlocking.runBlockingSuccess(this, "cmd connectivity set-chain3-enabled true")) return successful
        val selfPkg = packageName
        for (pkg in packageNames) {
            // never target the app itself or Shizuku
            if (pkg == selfPkg || pkg == "moe.shizuku.privileged.api") continue
            if (ShellExecutorBlocking.runBlockingSuccess(this, "cmd connectivity set-package-networking-enabled false $pkg")) {
                successful.add(pkg)
            }
        }
        return successful
    }

    private fun disableFirewall(packageNames: List<String>): Boolean {
        var allSuccessful = true
        val selfPkg = packageName
        for (pkg in packageNames) {
            // never target the app itself or Shizuku
            if (pkg == selfPkg || pkg == "moe.shizuku.privileged.api") continue
            if (!ShellExecutorBlocking.runBlockingSuccess(this, "cmd connectivity set-package-networking-enabled true $pkg")) {
                allSuccessful = false
            }
        }
        if (!ShellExecutorBlocking.runBlockingSuccess(this, "cmd connectivity set-chain3-enabled false")) {
            allSuccessful = false
        }
        return allSuccessful
    }

    private fun saveFirewallEnabled(enabled: Boolean) {
        val elapsed = if (enabled) android.os.SystemClock.elapsedRealtime() else -1L
        sharedPreferences.edit()
            .putBoolean(MainActivity.KEY_FIREWALL_ENABLED, enabled)
            .putLong(MainActivity.KEY_FIREWALL_SAVED_ELAPSED, elapsed)
            .apply()

        // Notify widget to update
        val intent = Intent(this, FirewallWidgetProvider::class.java)
        intent.action = MainActivity.ACTION_FIREWALL_STATE_CHANGED
        sendBroadcast(intent)
    }

    private fun saveActivePackages(packages: Set<String>) {
        sharedPreferences.edit()
            .putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, packages)
            .apply()
    }

    private fun loadActivePackages(): Set<String> {
        return sharedPreferences.getStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet()) ?: emptySet()
    }
}
