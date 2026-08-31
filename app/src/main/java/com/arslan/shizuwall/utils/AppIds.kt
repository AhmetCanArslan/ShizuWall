package com.arslan.shizuwall.utils

object AppIds {

    const val PER_USER_RANGE = 100_000
    private const val FIRST_APPLICATION_UID = 10_000
    private const val UNKNOWN_UID = -1

    fun appIdOf(uid: Int): Int = uid % PER_USER_RANGE

    fun isBlockable(uid: Int): Boolean =
        uid == UNKNOWN_UID || appIdOf(uid) >= FIRST_APPLICATION_UID
}
