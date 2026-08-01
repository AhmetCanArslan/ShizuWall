package com.arslan.shizuwall

enum class FirewallMode {
    DEFAULT,
    ADAPTIVE,
    SCREEN_LOCK_MODE,
    SMART_FOREGROUND,
    WHITELIST,
    FOCUS_TRACKER,
    HYBRID;

    companion object {
        fun fromName(name: String?): FirewallMode {
            return try {
                name?.let { valueOf(it) } ?: DEFAULT
            } catch (e: IllegalArgumentException) {
                DEFAULT
            }
        }
    }

    fun allowsDynamicSelection(): Boolean =
        this == ADAPTIVE || this == SCREEN_LOCK_MODE || this == SMART_FOREGROUND || this == WHITELIST || this == HYBRID || this == FOCUS_TRACKER

    fun requiresForegroundDetection(): Boolean =
        this == SMART_FOREGROUND || this == HYBRID || this == FOCUS_TRACKER
}
