package com.arslan.shizuwall.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellResultTest {

    @Test
    fun zeroExitCodeIsSuccess() {
        val result = ShellResult(0, "ok", "")
        assertTrue(result.success)
        assertTrue(result.isEffectivelySuccess)
    }

    @Test
    fun nonZeroExitCodeIsFailure() {
        val result = ShellResult(1, "", "permission denied")
        assertFalse(result.success)
        assertFalse(result.isEffectivelySuccess)
    }

    @Test
    fun uidOwnerMapErrorOnStderrIsEffectivelySuccess() {
        val result = ShellResult(1, "", "sUidOwnerMap does not have entry for uid 1010234")
        assertFalse(result.success)
        assertTrue(result.isUidOwnerMapMissing)
        assertTrue(result.isEffectivelySuccess)
    }

    @Test
    fun uidOwnerMapErrorOnStdoutIsEffectivelySuccess() {
        val result = ShellResult(1, "sUidOwnerMap does not have entry for uid 10234", "")
        assertTrue(result.isEffectivelySuccess)
    }

    @Test
    fun uidOwnerMapMatchIsCaseInsensitive() {
        val result = ShellResult(1, "", "SUIDOWNERMAP DOES NOT HAVE ENTRY FOR UID 1")
        assertTrue(result.isEffectivelySuccess)
    }

    @Test
    fun unrelatedErrorIsNotUidOwnerMapMissing() {
        assertFalse(ShellResult(1, "", "no such package").isUidOwnerMapMissing)
    }
}
