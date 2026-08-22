package com.arslan.shizuwall.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.os.UserManager

object CrossUserAppInfo {

    private val handleCache = HashMap<Int, UserHandle?>()

    private fun launcherApps(context: Context): LauncherApps? = try {
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
    } catch (_: Exception) {
        null
    }

    fun userHandle(context: Context, userId: Int): UserHandle? {
        if (userId == 0) return null
        synchronized(handleCache) {
            if (handleCache.containsKey(userId)) return handleCache[userId]
        }
        val profiles = try {
            launcherApps(context)?.profiles
                ?: (context.getSystemService(Context.USER_SERVICE) as? UserManager)?.userProfiles
        } catch (_: Exception) {
            null
        }
        val handle = profiles?.firstOrNull { identifierOf(it) == userId }
        synchronized(handleCache) { handleCache[userId] = handle }
        return handle
    }

    private fun identifierOf(handle: UserHandle): Int {
        val id = handle.hashCode()
        if (id >= 0) return id
        return handle.toString().filter { it.isDigit() }.toIntOrNull() ?: -1
    }

    fun applicationInfo(context: Context, packageName: String, userId: Int): ApplicationInfo? {
        if (userId != 0) {
            val handle = userHandle(context, userId)
            if (handle != null) {
                try {
                    val info = launcherApps(context)?.getApplicationInfo(packageName, 0, handle)
                    if (info != null) return info
                } catch (_: Exception) {
                }
            }
        }
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
        } catch (_: Exception) {
            null
        }
    }

    fun label(context: Context, packageName: String, userId: Int): String? {
        val info = applicationInfo(context, packageName, userId) ?: return null
        return try {
            context.packageManager.getApplicationLabel(info).toString().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun icon(context: Context, packageName: String, userId: Int): Drawable? {
        val pm = context.packageManager
        val info = applicationInfo(context, packageName, userId) ?: return null
        val icon = try {
            pm.getApplicationIcon(info)
        } catch (_: Exception) {
            return null
        }
        val handle = userHandle(context, userId) ?: return icon
        return try {
            pm.getUserBadgedIcon(icon, handle)
        } catch (_: Exception) {
            icon
        }
    }
}
