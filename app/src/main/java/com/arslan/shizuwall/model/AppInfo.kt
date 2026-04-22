package com.arslan.shizuwall.model

data class AppInfo(
    val appName: String,
    val packageName: String,
    val isSelected: Boolean = false,
    val isSystem: Boolean = false,
    val isFavorite: Boolean = false,
    val installTime: Long = 0,
    val appFirewallMode: Int = 0 // 0: Default, 1: Smart Foreground, 2: Screen Lock
)
