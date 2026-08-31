package com.arslan.shizuwall.firewall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirewallCommandsTest {

    @Test
    fun chain3EnableMatchesLegacyWireFormat() {
        assertEquals("cmd connectivity set-chain3-enabled true", FirewallCommands.chain3(true))
    }

    @Test
    fun chain3DisableMatchesLegacyWireFormat() {
        assertEquals("cmd connectivity set-chain3-enabled false", FirewallCommands.chain3(false))
    }

    @Test
    fun blockMatchesLegacyWireFormat() {
        assertEquals(
            "cmd connectivity set-package-networking-enabled false com.foo",
            FirewallCommands.block("com.foo")
        )
    }

    @Test
    fun unblockMatchesLegacyWireFormat() {
        assertEquals(
            "cmd connectivity set-package-networking-enabled true com.foo",
            FirewallCommands.unblock("com.foo")
        )
    }

    @Test
    fun networkingKeepsSecondaryUserKeyIntact() {
        assertEquals(
            "cmd connectivity set-package-networking-enabled false 10:com.foo",
            FirewallCommands.block("10:com.foo")
        )
    }

    @Test
    fun blockAllMapsEveryKeyInOrder() {
        assertEquals(
            listOf(
                "cmd connectivity set-package-networking-enabled false com.a",
                "cmd connectivity set-package-networking-enabled false com.b"
            ),
            FirewallCommands.blockAll(listOf("com.a", "com.b"))
        )
    }

    @Test
    fun unblockAllOfEmptyListIsEmpty() {
        assertEquals(emptyList<String>(), FirewallCommands.unblockAll(emptyList()))
    }

    @Test
    fun networkingAllUsesFlagForEveryKey() {
        assertEquals(
            listOf(
                "cmd connectivity set-package-networking-enabled true com.a",
                "cmd connectivity set-package-networking-enabled true com.b"
            ),
            FirewallCommands.networkingAll(listOf("com.a", "com.b"), true)
        )
    }

    @Test
    fun parseRoundTripsBlockCommand() {
        val parsed = FirewallCommands.parseNetworking(FirewallCommands.block("com.foo"))
        assertEquals(FirewallCommands.Networking("com.foo", false), parsed)
    }

    @Test
    fun parseRoundTripsUnblockCommandForClone() {
        val parsed = FirewallCommands.parseNetworking(FirewallCommands.unblock("10:com.foo"))
        assertEquals(FirewallCommands.Networking("10:com.foo", true), parsed)
    }

    @Test
    fun parseRejectsChain3Command() {
        assertNull(FirewallCommands.parseNetworking(FirewallCommands.CHAIN3_ENABLE))
    }

    @Test
    fun parseRejectsCommandWithTrailingArgument() {
        assertNull(
            FirewallCommands.parseNetworking(
                "cmd connectivity set-package-networking-enabled true com.foo extra"
            )
        )
    }

    @Test
    fun parseRejectsNonBooleanFlag() {
        assertNull(
            FirewallCommands.parseNetworking(
                "cmd connectivity set-package-networking-enabled yes com.foo"
            )
        )
    }

    @Test
    fun isNetworkingCommandAcceptsGeneratedBlock() {
        assertTrue(FirewallCommands.isNetworkingCommand(FirewallCommands.block("com.foo")))
    }

    @Test
    fun isNetworkingCommandRejectsChain3Disable() {
        assertFalse(FirewallCommands.isNetworkingCommand(FirewallCommands.CHAIN3_DISABLE))
    }
}
