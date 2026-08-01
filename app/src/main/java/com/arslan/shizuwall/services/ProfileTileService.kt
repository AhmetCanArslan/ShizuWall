package com.arslan.shizuwall.services

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.arslan.shizuwall.R
import com.arslan.shizuwall.profiles.ProfileActivator
import com.arslan.shizuwall.profiles.ProfileIcons
import com.arslan.shizuwall.profiles.ProfileTileSlots
import com.arslan.shizuwall.ui.MainActivity

abstract class ProfileTileService : TileService() {

    protected abstract val slot: Int

    private lateinit var prefs: SharedPreferences

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == MainActivity.KEY_PROFILES) refreshTile()
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
    }

    override fun onStartListening() {
        super.onStartListening()
        runCatching { prefs.registerOnSharedPreferenceChangeListener(prefsListener) }
        refreshTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        runCatching { prefs.unregisterOnSharedPreferenceChangeListener(prefsListener) }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val profile = ProfileTileSlots.profileForSlot(this, slot)
        if (profile == null) {
            refreshTile()
            return
        }

        ProfileActivator.activateFromTile(applicationContext, profile)
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val profile = ProfileTileSlots.profileForSlot(this, slot)
        if (profile == null) {
            tile.label = getString(R.string.profile_tile_unassigned)
            tile.subtitle = null
            tile.icon = Icon.createWithResource(this, R.drawable.ic_profiles_24px)
            tile.state = Tile.STATE_UNAVAILABLE
        } else {
            tile.label = profile.name
            tile.subtitle = null
            tile.icon = Icon.createWithResource(this, ProfileIcons.resFor(profile.icon))
            tile.state = Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}

class ProfileTileService1 : ProfileTileService() {
    override val slot = 0
}

class ProfileTileService2 : ProfileTileService() {
    override val slot = 1
}

class ProfileTileService3 : ProfileTileService() {
    override val slot = 2
}

class ProfileTileService4 : ProfileTileService() {
    override val slot = 3
}

class ProfileTileService5 : ProfileTileService() {
    override val slot = 4
}
