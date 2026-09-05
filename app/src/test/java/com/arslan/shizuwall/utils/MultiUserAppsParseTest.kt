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

    private val enabledOutput = """
        Package [com.enabled]
        appId=10234
        pkgFlags=[ HAS_CODE ]
        privateFlags=[ ]
        User 150:
        installed=true
        enabled=0
        Package [com.forced]
        appId=10235
        pkgFlags=[ HAS_CODE ]
        privateFlags=[ ]
        User 150:
        installed=true
        enabled=1
        Package [com.disabled]
        appId=10236
        pkgFlags=[ HAS_CODE ]
        privateFlags=[ ]
        User 150:
        installed=true
        enabled=2
        Package [com.disabledbyuser]
        appId=10237
        pkgFlags=[ HAS_CODE ]
        privateFlags=[ ]
        User 150:
        installed=true
        enabled=3
        Package [com.disableduntilused]
        appId=10238
        pkgFlags=[ HAS_CODE ]
        privateFlags=[ ]
        User 150:
        installed=true
        enabled=4
        Package [com.mixed]
        appId=10239
        pkgFlags=[ HAS_CODE ]
        privateFlags=[ ]
        User 0:
        installed=true
        enabled=0
        User 150:
        installed=true
        enabled=3
        Package [com.last]
        appId=10240
        pkgFlags=[ HAS_CODE ]
        privateFlags=[ ]
        User 150:
        installed=true
        enabled=0
    """.trimIndent()

    private fun parse(userIds: Set<Int>, exclude: (String) -> Boolean = noExclusions) =
        MultiUserApps.parseDumpsysPackages(output, userIds, exclude)

    private fun parseEnabled(userIds: Set<Int>) =
        MultiUserApps.parseDumpsysPackages(enabledOutput, userIds, noExclusions)

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

    @Test
    fun keepsEnabledPackages() {
        val names = parseEnabled(setOf(150)).getValue(150).map { it.packageName }
        assertEquals(listOf("com.enabled", "com.forced", "com.last"), names)
    }

    @Test
    fun dropsDisabledPackages() {
        val names = parseEnabled(setOf(150)).getValue(150).map { it.packageName }
        assertTrue("com.disabled" !in names)
    }

    @Test
    fun dropsUserDisabledPackages() {
        val names = parseEnabled(setOf(150)).getValue(150).map { it.packageName }
        assertTrue("com.disabledbyuser" !in names)
    }

    @Test
    fun dropsDisabledUntilUsedPackages() {
        val names = parseEnabled(setOf(150)).getValue(150).map { it.packageName }
        assertTrue("com.disableduntilused" !in names)
    }

    @Test
    fun dropsDisabledUserWhileKeepingEnabledUser() {
        val names = parseEnabled(setOf(0, 150)).getValue(0).map { it.packageName }
        assertEquals(listOf("com.mixed"), names)
    }
}
