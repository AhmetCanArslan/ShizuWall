package com.arslan.shizuwall.model

data class AppInfo(
    val appName: String,
    val packageName: String,
    val isSelected: Boolean = false,
    val isSystem: Boolean = false,
    val isFavorite: Boolean = false
)
