package com.arslan.shizuwall.services

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.arslan.shizuwall.R
import com.arslan.shizuwall.ui.MainActivity
import com.arslan.shizuwall.utils.FirewallUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FirewallTileService : TileService() {

    private lateinit var sharedPreferences: SharedPreferences
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == MainActivity.KEY_FIREWALL_ENABLED ||
            key == MainActivity.KEY_ACTIVE_PACKAGES ||
            key == MainActivity.KEY_FIREWALL_SAVED_ELAPSED ||
            key == MainActivity.KEY_FIREWALL_MODE
        ) {
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
        runCatching { sharedPreferences.registerOnSharedPreferenceChangeListener(prefsListener) }
    }

    override fun onStopListening() {
        super.onStopListening()
        runCatching { sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefsListener) }
    }

    override fun onClick() {
        super.onClick()
        if (FirewallUtils.loadFirewallEnabled(sharedPreferences)) {
            if (!FirewallUtils.checkBackendReady(this)) return
            scope.launch {
                FirewallUtils.disable(this@FirewallTileService, sharedPreferences)
                updateTile()
            }
            return
        }
        val targets = FirewallUtils.enableTargets(this, sharedPreferences) ?: return
        if (!FirewallUtils.checkBackendReady(this)) return
        scope.launch {
            FirewallUtils.enable(this@FirewallTileService, sharedPreferences, targets)
            updateTile()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefsListener) }
        job.cancel()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.state = if (FirewallUtils.loadFirewallEnabled(sharedPreferences)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.firewall)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_quick_tile)
        tile.updateTile()
    }
}
