package com.arslan.shizuwall.profiles

import android.content.Context
import android.content.SharedPreferences
import com.arslan.shizuwall.model.Profile
import com.arslan.shizuwall.ui.MainActivity
import org.json.JSONArray
import java.util.UUID


object ProfilesStore {

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

    fun getProfiles(context: Context): List<Profile> = getProfiles(prefs(context))

    fun getProfiles(prefs: SharedPreferences): List<Profile> {
        val raw = prefs.getString(MainActivity.KEY_PROFILES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { Profile.fromJson(it) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveProfiles(prefs: SharedPreferences, profiles: List<Profile>) {
        val arr = JSONArray()
        profiles.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(MainActivity.KEY_PROFILES, arr.toString()).apply()
    }

    fun getById(context: Context, id: String): Profile? =
        getProfiles(context).firstOrNull { it.id == id }

    fun getByName(context: Context, name: String): Profile? =
        getProfiles(context).firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun activeProfileId(context: Context): String? =
        prefs(context).getString(MainActivity.KEY_ACTIVE_PROFILE_ID, null)?.takeIf { it.isNotBlank() }

    fun setActiveProfileId(context: Context, id: String?) {
        prefs(context).edit().apply {
            if (id.isNullOrBlank()) remove(MainActivity.KEY_ACTIVE_PROFILE_ID)
            else putString(MainActivity.KEY_ACTIVE_PROFILE_ID, id)
            apply()
        }
    }

    fun create(
        context: Context,
        name: String,
        packages: Set<String>,
        firewallMode: String,
        appModesJson: String,
        showSystemApps: Boolean
    ): Profile {
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            packages = packages,
            firewallMode = firewallMode,
            appModesJson = appModesJson,
            showSystemApps = showSystemApps,
            createdAt = System.currentTimeMillis()
        )
        val p = prefs(context)
        saveProfiles(p, getProfiles(p) + profile)
        return profile
    }

    fun update(context: Context, profile: Profile) {
        val p = prefs(context)
        val updated = getProfiles(p).map { if (it.id == profile.id) profile else it }
        saveProfiles(p, updated)
    }

    fun activeProfile(context: Context): Profile? =
        activeProfileId(context)?.let { getById(context, it) }

    fun setIcon(context: Context, id: String, iconKey: String) {
        getById(context, id)?.let { update(context, it.copy(icon = iconKey)) }
    }

    fun rename(context: Context, id: String, newName: String) {
        getById(context, id)?.let { update(context, it.copy(name = newName.trim())) }
    }

    fun delete(context: Context, id: String) {
        val p = prefs(context)
        saveProfiles(p, getProfiles(p).filterNot { it.id == id })
        if (activeProfileId(context) == id) setActiveProfileId(context, null)
    }

    fun captureCurrent(context: Context): CapturedConfig {
        val p = prefs(context)
        return CapturedConfig(
            packages = p.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet()) ?: emptySet(),
            firewallMode = p.getString(MainActivity.KEY_FIREWALL_MODE, "DEFAULT") ?: "DEFAULT",
            appModesJson = p.getString(MainActivity.KEY_APP_MODES, "{}") ?: "{}",
            showSystemApps = p.getBoolean(MainActivity.KEY_SHOW_SYSTEM_APPS, false)
        )
    }


    fun writeSelectionFromProfile(context: Context, profile: Profile) {
        prefs(context).edit().apply {
            putStringSet(MainActivity.KEY_SELECTED_APPS, profile.packages)
            putInt(MainActivity.KEY_SELECTED_COUNT, profile.packages.size)
            putString(MainActivity.KEY_FIREWALL_MODE, profile.firewallMode)
            putString(MainActivity.KEY_APP_MODES, profile.appModesJson)
            putBoolean(MainActivity.KEY_SHOW_SYSTEM_APPS, profile.showSystemApps)
            putString(MainActivity.KEY_ACTIVE_PROFILE_ID, profile.id)
            apply()
        }
    }

    data class CapturedConfig(
        val packages: Set<String>,
        val firewallMode: String,
        val appModesJson: String,
        val showSystemApps: Boolean
    )
}
