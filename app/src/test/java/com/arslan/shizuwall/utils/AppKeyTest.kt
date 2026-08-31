package com.arslan.shizuwall.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppKeyTest {

    @Test
    fun of_returnsBarePackageForPrimaryUser() {
        assertEquals("com.foo", AppKey.of(0, "com.foo"))
    }

    @Test
    fun of_prefixesUserIdForSecondaryUsers() {
        assertEquals("10:com.foo", AppKey.of(10, "com.foo"))
        assertEquals("150:com.foo", AppKey.of(150, "com.foo"))
    }

    @Test
    fun userIdOf_readsPrefix() {
        assertEquals(0, AppKey.userIdOf("com.foo"))
        assertEquals(10, AppKey.userIdOf("10:com.foo"))
        assertEquals(150, AppKey.userIdOf("150:com.foo"))
    }

    @Test
    fun userIdOf_ignoresNonNumericPrefix() {
        assertEquals(0, AppKey.userIdOf("work:com.foo"))
        assertEquals(0, AppKey.userIdOf(":com.foo"))
    }

    @Test
    fun packageOf_stripsNumericPrefixOnly() {
        assertEquals("com.foo", AppKey.packageOf("com.foo"))
        assertEquals("com.foo", AppKey.packageOf("10:com.foo"))
        assertEquals("work:com.foo", AppKey.packageOf("work:com.foo"))
        assertEquals(":com.foo", AppKey.packageOf(":com.foo"))
    }

    @Test
    fun isSecondary_matchesUserIdPrefix() {
        assertFalse(AppKey.isSecondary("com.foo"))
        assertFalse(AppKey.isSecondary("work:com.foo"))
        assertTrue(AppKey.isSecondary("10:com.foo"))
        assertTrue(AppKey.isSecondary("150:com.foo"))
    }

    @Test
    fun roundTrip_preservesUserAndPackage() {
        val key = AppKey.of(10, "com.foo")
        assertEquals(10, AppKey.userIdOf(key))
        assertEquals("com.foo", AppKey.packageOf(key))
    }
}
