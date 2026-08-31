package com.arslan.shizuwall.firewall

import com.arslan.shizuwall.FirewallMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirewallTargetsTest {

    private val candidates = listOf("com.a", "com.b", "10:com.a")
    private val noModes = emptyMap<String, Int>()

    private fun block(mode: FirewallMode, locked: Boolean, modes: Map<String, Int> = noModes) =
        FirewallTargets.effectiveBlockList(mode, candidates, locked, modes)

    @Test
    fun defaultModeBlocksEverySelectedAppRegardlessOfLockState() {
        assertEquals(candidates, block(FirewallMode.DEFAULT, locked = false))
        assertEquals(candidates, block(FirewallMode.DEFAULT, locked = true))
    }

    @Test
    fun adaptiveModeBlocksEverySelectedApp() {
        assertEquals(candidates, block(FirewallMode.ADAPTIVE, locked = false))
    }

    @Test
    fun whitelistModeBlocksEveryCandidateItIsGiven() {
        assertEquals(candidates, block(FirewallMode.WHITELIST, locked = false))
    }

    @Test
    fun screenLockModeBlocksOnlyWhileLocked() {
        assertEquals(candidates, block(FirewallMode.SCREEN_LOCK_MODE, locked = true))
        assertEquals(emptyList<String>(), block(FirewallMode.SCREEN_LOCK_MODE, locked = false))
    }

    @Test
    fun smartForegroundBlocksNothingAtEnableTime() {
        assertEquals(emptyList<String>(), block(FirewallMode.SMART_FOREGROUND, locked = true))
        assertEquals(emptyList<String>(), block(FirewallMode.SMART_FOREGROUND, locked = false))
    }

    @Test
    fun focusTrackerBlocksNothingAtEnableTime() {
        assertEquals(emptyList<String>(), block(FirewallMode.FOCUS_TRACKER, locked = true))
        assertEquals(emptyList<String>(), block(FirewallMode.FOCUS_TRACKER, locked = false))
    }

    @Test
    fun hybridBlocksEverythingWithoutPerAppOverrides() {
        assertEquals(candidates, block(FirewallMode.HYBRID, locked = false))
    }

    @Test
    fun hybridHonoursNeverBlockOverride() {
        val modes = mapOf("com.a" to FirewallTargets.APP_MODE_NEVER_BLOCK)
        assertEquals(listOf("com.b", "10:com.a"), block(FirewallMode.HYBRID, false, modes))
    }

    @Test
    fun hybridHonoursScreenLockOverride() {
        val modes = mapOf("com.b" to FirewallTargets.APP_MODE_SCREEN_LOCK)
        assertEquals(listOf("com.a", "10:com.a"), block(FirewallMode.HYBRID, false, modes))
        assertEquals(candidates, block(FirewallMode.HYBRID, true, modes))
    }

    @Test
    fun hybridOverridesAreKeyedPerClone() {
        val modes = mapOf("10:com.a" to FirewallTargets.APP_MODE_NEVER_BLOCK)
        assertEquals(listOf("com.a", "com.b"), block(FirewallMode.HYBRID, false, modes))
    }

    @Test
    fun hybridBlocksForUnknownOverrideValue() {
        assertTrue(FirewallTargets.hybridBlocks(FirewallTargets.APP_MODE_INHERIT, false))
        assertTrue(FirewallTargets.hybridBlocks(99, false))
        assertFalse(FirewallTargets.hybridBlocks(FirewallTargets.APP_MODE_NEVER_BLOCK, true))
    }

    @Test
    fun emptyCandidatesStayEmptyInEveryMode() {
        val empty = FirewallMode.entries.map {
            FirewallTargets.effectiveBlockList(it, emptyList(), true, noModes)
        }
        assertEquals(List(FirewallMode.entries.size) { emptyList<String>() }, empty)
    }

    @Test
    fun onlyForegroundModesSkipBlockingAtEnable() {
        val skipping = FirewallMode.entries.filter { FirewallTargets.skipsBlockingAtEnable(it) }
        assertEquals(listOf(FirewallMode.SMART_FOREGROUND, FirewallMode.FOCUS_TRACKER), skipping)
    }

    @Test
    fun whitelistInvertsTheToggleMeaning() {
        assertTrue(FirewallTargets.shouldBlockOnToggle(FirewallMode.WHITELIST, isSelected = false))
        assertFalse(FirewallTargets.shouldBlockOnToggle(FirewallMode.WHITELIST, isSelected = true))
    }

    @Test
    fun everyNonWhitelistModeBlocksTheSelectedApp() {
        val blocking = FirewallMode.entries
            .filterNot { it == FirewallMode.WHITELIST }
            .filter { FirewallTargets.shouldBlockOnToggle(it, isSelected = true) }
        assertEquals(FirewallMode.entries - FirewallMode.WHITELIST, blocking)
    }

    @Test
    fun parseAppModesDropsInheritEntries() {
        val parsed = FirewallTargets.parseAppModes("""{"com.a":0,"com.b":1,"10:com.a":2}""")
        assertEquals(mapOf("com.b" to 1, "10:com.a" to 2), parsed)
    }

    @Test
    fun parseAppModesToleratesMissingOrBrokenJson() {
        assertEquals(noModes, FirewallTargets.parseAppModes(null))
        assertEquals(noModes, FirewallTargets.parseAppModes(""))
        assertEquals(noModes, FirewallTargets.parseAppModes("not json"))
        assertEquals(noModes, FirewallTargets.parseAppModes("{}"))
    }
}
