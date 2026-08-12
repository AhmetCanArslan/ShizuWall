package com.arslan.shizuwall.repo

import android.content.Context
import android.content.SharedPreferences
import com.arslan.shizuwall.FirewallMode
import com.arslan.shizuwall.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class FirewallState(
    val enabled: Boolean,
    val activePackages: Set<String>,
    val selectedApps: Set<String> = emptySet(),
    val appModesJson: String = "{}",
    val firewallMode: String = FirewallMode.DEFAULT.name,
    val showSystemApps: Boolean = false,
    val activeProfileId: String? = null
)

class FirewallStateRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<FirewallState> = _state

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == MainActivity.KEY_FIREWALL_ENABLED ||
            key == MainActivity.KEY_ACTIVE_PACKAGES ||
            key == MainActivity.KEY_FIREWALL_UPDATE_TS ||
            key == MainActivity.KEY_SELECTED_APPS ||
            key == MainActivity.KEY_APP_MODES ||
            key == MainActivity.KEY_FIREWALL_MODE ||
            key == MainActivity.KEY_SHOW_SYSTEM_APPS ||
            key == MainActivity.KEY_ACTIVE_PROFILE_ID
        ) {
            _state.value = loadState()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun loadState(): FirewallState {
        val enabled = prefs.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
        val active = prefs.getStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet()) ?: emptySet()
        val selected = prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        return FirewallState(
            enabled = enabled,
            activePackages = active,
            selectedApps = selected,
            appModesJson = prefs.getString(MainActivity.KEY_APP_MODES, "{}") ?: "{}",
            firewallMode = prefs.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name)
                ?: FirewallMode.DEFAULT.name,
            showSystemApps = prefs.getBoolean(MainActivity.KEY_SHOW_SYSTEM_APPS, false),
            activeProfileId = prefs.getString(MainActivity.KEY_ACTIVE_PROFILE_ID, null)
        )
    }

    fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
