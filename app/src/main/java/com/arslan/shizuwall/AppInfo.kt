package com.arslan.shizuwall

import android.graphics.Bitmap

data class AppInfo(
    val appName: String,
    val packageName: String,
    val iconBitmap: Bitmap?,
    val isSelected: Boolean = false,
    val isSystem: Boolean = false,
    val isFavorite: Boolean = false
)
