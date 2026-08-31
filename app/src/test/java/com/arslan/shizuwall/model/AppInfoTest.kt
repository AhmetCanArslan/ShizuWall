package com.arslan.shizuwall.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppInfoTest {

    @Test
    fun keyIsBarePackageForPrimaryUser() {
        assertEquals("com.foo", AppInfo("Foo", "com.foo").key)
    }

    @Test
    fun keyCarriesUserIdForClones() {
        assertEquals("10:com.foo", AppInfo("Foo", "com.foo", userId = 10).key)
    }

    @Test
    fun uidDefaultsToUnknown() {
        assertEquals(-1, AppInfo("Foo", "com.foo").uid)
    }
}
