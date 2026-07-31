package com.arslan.shizuwall.model

import com.arslan.shizuwall.utils.AppKey

data class AppInfo(
    val appName: String,
    val packageName: String,
    val isSelected: Boolean = false,
    val isSystem: Boolean = false,
    val isFavorite: Boolean = false,
    val installTime: Long = 0,
    val appFirewallMode: Int = 0,
    val userId: Int = 0,
    val uid: Int = -1
) {

    val key: String get() = AppKey.of(userId, packageName)
}
