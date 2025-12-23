package com.arslan.shizuwall.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.arslan.shizuwall.ui.main.MainActivity

data class FirewallState(val enabled: Boolean, val activePackages: Set<String>)

class FirewallStateRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<FirewallState> = _state

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == MainActivity.KEY_FIREWALL_ENABLED ||
            key == MainActivity.KEY_ACTIVE_PACKAGES ||
            key == MainActivity.KEY_FIREWALL_UPDATE_TS
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
        return FirewallState(enabled, active)
    }

    fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
