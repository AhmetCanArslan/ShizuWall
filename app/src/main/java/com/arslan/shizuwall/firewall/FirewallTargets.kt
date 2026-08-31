package com.arslan.shizuwall.firewall

import com.arslan.shizuwall.FirewallMode
import org.json.JSONObject

object FirewallTargets {

    const val APP_MODE_INHERIT = 0
    const val APP_MODE_NEVER_BLOCK = 1
    const val APP_MODE_SCREEN_LOCK = 2

    fun parseAppModes(json: String?): Map<String, Int> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence()
                .map { it to obj.optInt(it, APP_MODE_INHERIT) }
                .filter { it.second != APP_MODE_INHERIT }
                .toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun hybridBlocks(appMode: Int, isDeviceLocked: Boolean): Boolean = when (appMode) {
        APP_MODE_NEVER_BLOCK -> false
        APP_MODE_SCREEN_LOCK -> isDeviceLocked
        else -> true
    }

    fun effectiveBlockList(
        mode: FirewallMode,
        candidates: List<String>,
        isDeviceLocked: Boolean,
        appModes: Map<String, Int>
    ): List<String> = when (mode) {
        FirewallMode.SCREEN_LOCK_MODE -> if (isDeviceLocked) candidates else emptyList()
        FirewallMode.HYBRID -> candidates.filter {
            hybridBlocks(appModes[it] ?: APP_MODE_INHERIT, isDeviceLocked)
        }
        FirewallMode.SMART_FOREGROUND, FirewallMode.FOCUS_TRACKER -> emptyList()
        else -> candidates
    }

    fun skipsBlockingAtEnable(mode: FirewallMode): Boolean =
        mode == FirewallMode.SMART_FOREGROUND || mode == FirewallMode.FOCUS_TRACKER

    fun shouldBlockOnToggle(mode: FirewallMode, isSelected: Boolean): Boolean =
        if (mode == FirewallMode.WHITELIST) !isSelected else isSelected
}
