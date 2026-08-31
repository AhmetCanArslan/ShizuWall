package com.arslan.shizuwall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModesTest {

    @Test
    fun workingModeParsesKnownNames() {
        assertEquals(WorkingMode.SHIZUKU, WorkingMode.fromName("SHIZUKU"))
        assertEquals(WorkingMode.LADB, WorkingMode.fromName("LADB"))
        assertEquals(WorkingMode.ROOT, WorkingMode.fromName("ROOT"))
    }

    @Test
    fun workingModeFallsBackToShizuku() {
        assertEquals(WorkingMode.SHIZUKU, WorkingMode.fromName(null))
        assertEquals(WorkingMode.SHIZUKU, WorkingMode.fromName(""))
        assertEquals(WorkingMode.SHIZUKU, WorkingMode.fromName("ADB"))
    }

    @Test
    fun firewallModeParsesKnownNames() {
        assertEquals(FirewallMode.WHITELIST, FirewallMode.fromName("WHITELIST"))
        assertEquals(FirewallMode.SCREEN_LOCK_MODE, FirewallMode.fromName("SCREEN_LOCK_MODE"))
    }

    @Test
    fun firewallModeFallsBackToDefault() {
        assertEquals(FirewallMode.DEFAULT, FirewallMode.fromName(null))
        assertEquals(FirewallMode.DEFAULT, FirewallMode.fromName("ACCESSIBILITY"))
    }

    @Test
    fun onlyDefaultModeForbidsDynamicSelection() {
        val dynamic = FirewallMode.entries.filter { it.allowsDynamicSelection() }
        assertEquals(FirewallMode.entries - FirewallMode.DEFAULT, dynamic)
    }

    @Test
    fun foregroundDetectionModesAreExactlyThree() {
        val foreground = FirewallMode.entries.filter { it.requiresForegroundDetection() }
        assertEquals(
            listOf(FirewallMode.SMART_FOREGROUND, FirewallMode.FOCUS_TRACKER, FirewallMode.HYBRID),
            foreground
        )
    }

    @Test
    fun foregroundDetectionImpliesDynamicSelection() {
        assertTrue(FirewallMode.entries.filter { it.requiresForegroundDetection() }
            .all { it.allowsDynamicSelection() })
        assertFalse(FirewallMode.DEFAULT.requiresForegroundDetection())
    }
}
