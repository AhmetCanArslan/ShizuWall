package com.arslan.shizuwall.profiles

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import com.arslan.shizuwall.model.Profile
import com.arslan.shizuwall.services.ProfileTileService1
import com.arslan.shizuwall.services.ProfileTileService2
import com.arslan.shizuwall.services.ProfileTileService3
import com.arslan.shizuwall.services.ProfileTileService4
import com.arslan.shizuwall.services.ProfileTileService5

object ProfileTileSlots {

    private val slotClasses = listOf(
        ProfileTileService1::class.java,
        ProfileTileService2::class.java,
        ProfileTileService3::class.java,
        ProfileTileService4::class.java,
        ProfileTileService5::class.java
    )

    val slotCount: Int get() = slotClasses.size

    fun canRequestAdd(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun componentFor(context: Context, slot: Int): ComponentName =
        ComponentName(context.applicationContext, slotClasses[slot])

    fun profileForSlot(context: Context, slot: Int): Profile? =
        ProfilesStore.getProfiles(context).firstOrNull { it.tileSlot == slot }

    fun claimSlot(context: Context, profile: Profile): Int? {
        val used = ProfilesStore.getProfiles(context)
            .filter { it.id != profile.id }
            .map { it.tileSlot }
            .toSet()
        if (profile.tileSlot in 0 until slotCount && profile.tileSlot !in used) return profile.tileSlot
        val free = (0 until slotCount).firstOrNull { it !in used } ?: return null
        ProfilesStore.update(context, profile.copy(tileSlot = free))
        setComponentEnabled(context, free, true)
        return free
    }

    fun releaseSlot(context: Context, profile: Profile) {
        val slot = profile.tileSlot
        if (slot < 0 || slot >= slotCount) return
        ProfilesStore.update(context, profile.copy(tileSlot = -1))
        setComponentEnabled(context, slot, false)
    }

    private fun setComponentEnabled(context: Context, slot: Int, enabled: Boolean) {
        val appContext = context.applicationContext
        val component = componentFor(appContext, slot)
        val wanted = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        runCatching {
            if (appContext.packageManager.getComponentEnabledSetting(component) != wanted) {
                appContext.packageManager.setComponentEnabledSetting(
                    component,
                    wanted,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }

    fun sync(context: Context) = refreshTiles(context)

    fun refreshTiles(context: Context) {
        val appContext = context.applicationContext
        val claimed = ProfilesStore.getProfiles(appContext).map { it.tileSlot }.toSet()
        for (slot in 0 until slotCount) {
            if (slot !in claimed) continue
            runCatching { TileService.requestListeningState(appContext, componentFor(appContext, slot)) }
        }
    }

    fun requestAddTile(context: Context, slot: Int, profile: Profile) {
        if (!canRequestAdd()) return
        val appContext = context.applicationContext
        runCatching {
            appContext.getSystemService(StatusBarManager::class.java)?.requestAddTileService(
                componentFor(appContext, slot),
                profile.name,
                Icon.createWithResource(appContext, ProfileIcons.resFor(profile.icon)),
                { runnable -> runnable.run() },
                { }
            )
        }
    }
}
