package com.arslan.shizuwall

/**
 * Enum representing the different firewall operation modes.
 * 
 * - DEFAULT: Traditional firewall mode where selected apps are blocked
 * - ADAPTIVE: Dynamic mode where apps can be selected/deselected while firewall is ON
 * - SCREEN_LOCK_MODE: Blocks selected apps after lock and unblocks on unlock
 * - SMART_FOREGROUND: Only the foreground app is allowed network access
 */
enum class FirewallMode {
    DEFAULT,
    ADAPTIVE,
    SCREEN_LOCK_MODE,
    SMART_FOREGROUND,
    WHITELIST,
    HYBRID;

    companion object {
        /**
         * Convert a string name to FirewallMode.
         * Returns DEFAULT for invalid or null input.
         */
        fun fromName(name: String?): FirewallMode {
            return try {
                name?.let { valueOf(it) } ?: DEFAULT
            } catch (e: IllegalArgumentException) {
                DEFAULT
            }
        }
    }

    /**
     * Check if this mode is Adaptive mode
     */
    fun isAdaptive(): Boolean = this == ADAPTIVE

    /**
     * Check if this mode is Smart Foreground mode
     */
    fun isSmartForeground(): Boolean = this == SMART_FOREGROUND

    /**
     * Check if this mode allows dynamic app selection while firewall is enabled
     */
    fun allowsDynamicSelection(): Boolean =
        this == ADAPTIVE || this == SCREEN_LOCK_MODE || this == SMART_FOREGROUND || this == WHITELIST || this == HYBRID
}
