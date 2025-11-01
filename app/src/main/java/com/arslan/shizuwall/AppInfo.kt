package com.arslan.shizuwall

import android.graphics.Bitmap

data class AppInfo(
    val appName: String,
    val packageName: String,
    val iconBitmap: Bitmap?,    // cached bitmap for icon to prevnt repeated drawable resolution
    var isSelected: Boolean = false,
    val isSystem: Boolean = false // mark system apps
)
