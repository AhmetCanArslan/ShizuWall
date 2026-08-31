package com.arslan.shizuwall.profiles

import com.arslan.shizuwall.model.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesStoreLogicTest {

    private val home = Profile(
        id = "id-home",
        name = "Home",
        packages = setOf("com.a", "10:com.b"),
        firewallMode = "DEFAULT",
        appModesJson = """{"com.a":2}""",
        showSystemApps = false,
        createdAt = 1L
    )

    private val office = home.copy(
        id = "id-office",
        name = "Office",
        packages = setOf("com.a", "com.sys"),
        firewallMode = "WHITELIST",
        showSystemApps = true,
        createdAt = 2L
    )

    private val config = ProfilesStore.CapturedConfig(
        packages = home.packages,
        firewallMode = home.firewallMode,
        appModesJson = home.appModesJson,
        showSystemApps = home.showSystemApps
    )

    @Test
    fun encodeDecodeRoundTripsEveryProfile() {
        assertEquals(listOf(home, office), ProfilesStore.decode(ProfilesStore.encode(listOf(home, office))))
    }

    @Test
    fun decodeReturnsEmptyForMissingOrBrokenJson() {
        assertEquals(emptyList<Profile>(), ProfilesStore.decode(null))
        assertEquals(emptyList<Profile>(), ProfilesStore.decode(""))
        assertEquals(emptyList<Profile>(), ProfilesStore.decode("not json"))
        assertEquals(emptyList<Profile>(), ProfilesStore.decode("[]"))
    }

    @Test
    fun matchesAcceptsIdenticalConfig() {
        assertTrue(ProfilesStore.matches(home, config))
    }

    @Test
    fun matchesIgnoresAppModeFormattingAndInheritEntries() {
        val padded = config.copy(appModesJson = """{"com.b":0,  "com.a":2}""")
        assertTrue(ProfilesStore.matches(home, padded))
    }

    @Test
    fun matchesRejectsDifferentPackageSet() {
        assertFalse(ProfilesStore.matches(home, config.copy(packages = setOf("com.a"))))
    }

    @Test
    fun matchesRejectsDifferentFirewallMode() {
        assertFalse(ProfilesStore.matches(home, config.copy(firewallMode = "WHITELIST")))
    }

    @Test
    fun matchesRejectsDifferentShowSystemAppsFlag() {
        assertFalse(ProfilesStore.matches(home, config.copy(showSystemApps = true)))
    }

    @Test
    fun matchesRejectsDifferentAppModes() {
        assertFalse(ProfilesStore.matches(home, config.copy(appModesJson = """{"com.a":1}""")))
    }

    @Test
    fun withoutPackagesScrubsKeysFromEveryProfile() {
        val scrubbed = ProfilesStore.withoutPackages(listOf(home, office), setOf("com.a"))
        assertEquals(setOf("10:com.b"), scrubbed[0].packages)
        assertEquals(setOf("com.sys"), scrubbed[1].packages)
    }

    @Test
    fun withoutPackagesKeepsEverythingElseIntact() {
        val scrubbed = ProfilesStore.withoutPackages(listOf(home), setOf("com.a")).first()
        assertEquals(home.copy(packages = setOf("10:com.b")), scrubbed)
    }

    @Test
    fun withoutPackagesIsIdentityForEmptyOrUnknownKeys() {
        val profiles = listOf(home, office)
        assertEquals(profiles, ProfilesStore.withoutPackages(profiles, emptySet()))
        assertEquals(profiles, ProfilesStore.withoutPackages(profiles, setOf("com.absent")))
    }

    @Test
    fun scrubbedProfileStillMatchesTheScrubbedSelection() {
        val scrubbed = ProfilesStore.withoutPackages(listOf(home), setOf("com.a")).first()
        assertTrue(ProfilesStore.matches(scrubbed, config.copy(packages = setOf("10:com.b"))))
    }
}
