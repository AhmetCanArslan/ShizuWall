package com.arslan.shizuwall.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhitelistFilterTest {

    private val userUid = 10234
    private val systemSharedUid = 1000

    private fun manageable(
        uid: Int = userUid,
        enabled: Boolean = true,
        hasInternet: Boolean = true,
        isSystem: Boolean = false,
        showSystemApps: Boolean = false
    ) = WhitelistFilter.isManageable(uid, enabled, hasInternet, isSystem, showSystemApps)

    @Test
    fun ordinaryUserAppIsManageable() {
        assertTrue(manageable())
    }

    @Test
    fun disabledAppIsNotManageable() {
        assertFalse(manageable(enabled = false))
    }

    @Test
    fun appWithoutInternetPermissionIsNotManageable() {
        assertFalse(manageable(hasInternet = false))
    }

    @Test
    fun systemAppIsHiddenUntilShowSystemAppsIsOn() {
        assertFalse(manageable(isSystem = true))
        assertTrue(manageable(isSystem = true, showSystemApps = true))
    }

    @Test
    fun sharedSystemUidStaysUnmanageableEvenWithShowSystemAppsOn() {
        assertFalse(manageable(uid = systemSharedUid, isSystem = true, showSystemApps = true))
        assertFalse(manageable(uid = systemSharedUid, isSystem = false, showSystemApps = true))
    }

    @Test
    fun clonedSystemSharedUidIsAlsoUnmanageable() {
        assertFalse(manageable(uid = 1001000, isSystem = true, showSystemApps = true))
    }

    @Test
    fun cloneOfUserAppIsManageable() {
        assertTrue(manageable(uid = 1010234))
    }

    @Test
    fun unknownUidFailsOpenSoCachedRowsStayUsable() {
        assertTrue(manageable(uid = -1))
    }

    @Test
    fun perProfileSelectionMatchesOnTheFullKey() {
        val clone = MultiUserApps.SecondaryApp(10, "com.a", 1010234)
        assertTrue(WhitelistFilter.isSelected(listOf("10:com.a"), clone, perProfileSelection = true))
        assertFalse(WhitelistFilter.isSelected(listOf("com.a"), clone, perProfileSelection = true))
    }

    @Test
    fun mirroredSelectionMatchesOnThePackageName() {
        val clone = MultiUserApps.SecondaryApp(10, "com.a", 1010234)
        assertTrue(WhitelistFilter.isSelected(listOf("com.a"), clone, perProfileSelection = false))
        assertFalse(WhitelistFilter.isSelected(listOf("10:com.a"), clone, perProfileSelection = false))
    }

    @Test
    fun showSystemAppsIsTheOnlyToggleThatChangesSystemAppVisibility() {
        val hidden = listOf(true, false).map { manageable(isSystem = true, showSystemApps = it) }
        assertEquals(listOf(true, false), hidden)
    }
}
