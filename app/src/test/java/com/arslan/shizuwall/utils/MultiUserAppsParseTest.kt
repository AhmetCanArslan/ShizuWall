package com.arslan.shizuwall.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiUserAppsParseTest {

    private val noExclusions: (String) -> Boolean = { false }

    private val output = """
        Package [com.foo]
        appId=10234
        pkgFlags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]
        privateFlags=[ PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_RESIZEABLE ]
        User 0:
        installed=true
        User 10:
        installed=true
        Package [com.sys]
        appId=10500
        pkgFlags=[ SYSTEM HAS_CODE ]
        privateFlags=[ PRIVATE_FLAG_PRIVILEGED ]
        User 0:
        installed=true
        User 10:
        installed=true
        Package [com.notinstalled]
        appId=10600
        pkgFlags=[ HAS_CODE ]
        privateFlags=[ ]
        User 10:
        installed=false
        Package [com.lib]
        appId=10700
        pkgFlags=[ HAS_CODE ]
        privateFlags=[ PRIVATE_FLAG_STATIC_SHARED_LIBRARY ]
        User 10:
        installed=true
        Shared users:
        userId=10800
        User 10:
        installed=true
    """.trimIndent()

    private fun parse(userIds: Set<Int>, exclude: (String) -> Boolean = noExclusions) =
        MultiUserApps.parseDumpsysPackages(output, userIds, exclude)

    @Test
    fun keepsOnlyRequestedUsers() {
        val byUser = parse(setOf(10))
        assertEquals(setOf(10), byUser.keys)
    }

    @Test
    fun computesPerUserUid() {
        val app = parse(setOf(10)).getValue(10).first { it.packageName == "com.foo" }
        assertEquals(1010234, app.uid)
        assertEquals(10, app.userId)
        assertEquals("10:com.foo", app.key)
    }

    @Test
    fun marksSystemPackages() {
        val apps = parse(setOf(10)).getValue(10).associateBy { it.packageName }
        assertEquals(false, apps.getValue("com.foo").isSystem)
        assertEquals(true, apps.getValue("com.sys").isSystem)
    }

    @Test
    fun dropsNotInstalledPackages() {
        val names = parse(setOf(10)).getValue(10).map { it.packageName }
        assertTrue("com.notinstalled" !in names)
    }

    @Test
    fun dropsSharedLibraryPackages() {
        val names = parse(setOf(10)).getValue(10).map { it.packageName }
        assertTrue("com.lib" !in names)
    }

    @Test
    fun stopsAtSharedUsersSection() {
        val uids = parse(setOf(10)).getValue(10).map { it.uid }
        assertTrue(1010800 !in uids)
    }

    @Test
    fun honoursExcludePredicate() {
        val names = parse(setOf(10)) { it == "com.foo" }.getValue(10).map { it.packageName }
        assertEquals(listOf("com.sys"), names)
    }

    @Test
    fun returnsEmptyWhenNoUsersRequested() {
        assertTrue(parse(emptySet()).isEmpty())
    }
}
