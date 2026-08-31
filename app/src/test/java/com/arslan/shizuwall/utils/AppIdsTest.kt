package com.arslan.shizuwall.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIdsTest {

    @Test
    fun appIdOf_stripsUserRange() {
        assertEquals(10234, AppIds.appIdOf(10234))
        assertEquals(10234, AppIds.appIdOf(1010234))
        assertEquals(10234, AppIds.appIdOf(15010234))
    }

    @Test
    fun appIdOf_keepsSystemIdsIntact() {
        assertEquals(1000, AppIds.appIdOf(1000))
        assertEquals(1000, AppIds.appIdOf(1001000))
    }

    @Test
    fun isBlockable_acceptsApplicationUids() {
        assertTrue(AppIds.isBlockable(10000))
        assertTrue(AppIds.isBlockable(10234))
        assertTrue(AppIds.isBlockable(1010234))
        assertTrue(AppIds.isBlockable(15010234))
    }

    @Test
    fun isBlockable_rejectsSharedSystemUids() {
        assertFalse(AppIds.isBlockable(1000))
        assertFalse(AppIds.isBlockable(1001))
        assertFalse(AppIds.isBlockable(9999))
        assertFalse(AppIds.isBlockable(1001000))
    }

    @Test
    fun isBlockable_failsOpenForUnknownUid() {
        assertTrue(AppIds.isBlockable(-1))
    }
}
