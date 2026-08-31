package com.arslan.shizuwall.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileTest {

    private val profile = Profile(
        id = "abc",
        name = "Office",
        packages = setOf("com.foo", "10:com.bar"),
        firewallMode = "WHITELIST",
        appModesJson = """{"com.foo":2}""",
        showSystemApps = true,
        createdAt = 1700000000000L,
        icon = "work",
        tileSlot = 1
    )

    @Test
    fun jsonRoundTripPreservesEveryField() {
        assertEquals(profile, Profile.fromJson(profile.toJson()))
    }

    @Test
    fun fromJsonAppliesDefaultsForMissingFields() {
        val parsed = Profile.fromJson(JSONObject().put("id", "x").put("name", "y"))
        assertEquals(emptySet<String>(), parsed.packages)
        assertEquals("DEFAULT", parsed.firewallMode)
        assertEquals("{}", parsed.appModesJson)
        assertEquals(false, parsed.showSystemApps)
        assertEquals(0L, parsed.createdAt)
        assertEquals("", parsed.icon)
        assertEquals(-1, parsed.tileSlot)
    }

    @Test
    fun fromJsonDropsBlankPackageEntries() {
        val json = profile.toJson().put("packages", org.json.JSONArray(listOf("com.foo", "", "  ")))
        assertEquals(setOf("com.foo"), Profile.fromJson(json).packages)
    }
}
