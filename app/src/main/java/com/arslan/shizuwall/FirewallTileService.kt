package com.arslan.shizuwall

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.lang.reflect.Method

@RequiresApi(Build.VERSION_CODES.N)
class FirewallTileService : TileService() {

    private lateinit var sharedPreferences: SharedPreferences
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)

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
            if (!checkPermission()) return
            scope.launch {
                applyDisableFirewall()
            }
        } else {
            // Enable firewall
            val selectedApps = loadSelectedApps()
            val adaptiveMode = sharedPreferences.getBoolean(MainActivity.KEY_ADAPTIVE_MODE, false)
            
            if (selectedApps.isEmpty() && !adaptiveMode) {
                Toast.makeText(this@FirewallTileService, "No apps selected", Toast.LENGTH_SHORT).show()
                return
            }
            if (!checkPermission()) return
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
        tile.label = "Firewall"
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
        return sharedPreferences.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())
            ?.filterNot { it == "moe.shizuku.privileged.api" }
            ?.toList() ?: emptyList()
    }

    private fun checkPermission(): Boolean {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "Shizuku not running", Toast.LENGTH_SHORT).show()
            return false
        }
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Shizuku permission required", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
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
            disableFirewall(activePackages.toList())
            saveFirewallEnabled(false)
            saveActivePackages(emptySet())
        }
        updateTile()
    }

    private fun enableFirewall(packageNames: List<String>): List<String> {
        val successful = mutableListOf<String>()
        if (!runShizukuShellCommand("cmd connectivity set-chain3-enabled true")) return successful
        for (pkg in packageNames) {
            if (runShizukuShellCommand("cmd connectivity set-package-networking-enabled false $pkg")) {
                successful.add(pkg)
            }
        }
        return successful
    }

    private fun disableFirewall(packageNames: List<String>) {
        for (pkg in packageNames) {
            runShizukuShellCommand("cmd connectivity set-package-networking-enabled true $pkg")
        }
        runShizukuShellCommand("cmd connectivity set-chain3-enabled false")
    }

    private fun runShizukuShellCommand(command: String): Boolean {
        return try {
            val process = SHIZUKU_NEW_PROCESS_METHOD.invoke(
                null,
                arrayOf("/system/bin/sh", "-c", command),
                null,
                null
            ) as? rikka.shizuku.ShizukuRemoteProcess ?: return false
            process.outputStream.close()
            process.inputStream.close()
            process.errorStream.close()
            val exitCode = process.waitFor()
            process.destroy()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun saveFirewallEnabled(enabled: Boolean) {
        val elapsed = if (enabled) android.os.SystemClock.elapsedRealtime() else -1L
        sharedPreferences.edit()
            .putBoolean(MainActivity.KEY_FIREWALL_ENABLED, enabled)
            .putLong(MainActivity.KEY_FIREWALL_SAVED_ELAPSED, elapsed)
            .apply()
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
