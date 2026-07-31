package com.arslan.shizuwall.utils

object AppKey {

    fun of(userId: Int, packageName: String): String =
        if (userId == 0) packageName else "$userId:$packageName"

    fun userIdOf(key: String): Int {
        val sep = key.indexOf(':')
        if (sep <= 0) return 0
        return key.substring(0, sep).toIntOrNull() ?: 0
    }

    fun packageOf(key: String): String {
        val sep = key.indexOf(':')
        if (sep <= 0) return key
        return if (key.substring(0, sep).toIntOrNull() == null) key else key.substring(sep + 1)
    }

    fun isSecondary(key: String): Boolean = userIdOf(key) != 0
}
