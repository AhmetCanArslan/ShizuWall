package com.arslan.shizuwall.shell

import android.content.Context
import com.arslan.shizuwall.firewall.PerUidFirewall

/**
 * Reroutes "block this package" commands to [PerUidFirewall], so a block does not follow the
 * package's appId into work profiles and Secure Folder (#126). Doing it here covers every caller,
 * and falls back to the original command whenever the per-uid path is unavailable.
 *
 * Unblock commands pass through: their fan-out clears leftover deny bits in the other profiles.
 */
class PerUidRoutingExecutor(
    private val context: Context,
    private val delegate: ShellExecutor
) : ShellExecutor {

    override suspend fun exec(command: String): ShellResult {
        val packageName = BLOCK_COMMAND.matchEntire(command.trim())?.groupValues?.get(1)
            ?: return delegate.exec(command)

        return if (PerUidFirewall.blockPackage(context, packageName)) {
            PER_UID_SUCCESS
        } else {
            delegate.exec(command)
        }
    }

    private companion object {
        val BLOCK_COMMAND =
            Regex("""cmd connectivity set-package-networking-enabled false (\S+)""")

        val PER_UID_SUCCESS = ShellResult(exitCode = 0, stdout = "per-uid rule applied", stderr = "")
    }
}
