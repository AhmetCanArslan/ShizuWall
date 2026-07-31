package com.arslan.shizuwall.shell

import android.content.Context
import com.arslan.shizuwall.firewall.PerUidFirewall
import com.arslan.shizuwall.ui.MainActivity
import com.arslan.shizuwall.utils.AppKey
import com.arslan.shizuwall.utils.FirewallUtils
import com.arslan.shizuwall.utils.MultiUserApps
class PerUidRoutingExecutor(
    private val context: Context,
    private val delegate: ShellExecutor
) : ShellExecutor {

    override suspend fun exec(command: String): ShellResult {
        val trimmed = command.trim()

        BLOCK_COMMAND.matchEntire(trimmed)?.let { match ->
            return block(match.groupValues[1], command)
        }
        UNBLOCK_COMMAND.matchEntire(trimmed)?.let { match ->
            return unblock(match.groupValues[1], command)
        }
        if (trimmed == CHAIN3_DISABLE) {
            val result = delegate.exec(command)
            clearSecondaryRules()
            return result
        }
        return delegate.exec(command)
    }

    private suspend fun block(key: String, command: String): ShellResult {
        val result = when {
            PerUidFirewall.setRule(context, key, PerUidFirewall.RULE_DENY) -> PER_UID_SUCCESS
            AppKey.isSecondary(key) -> unresolved(key)
            else -> delegate.exec(command)
        }
        if (result.isEffectivelySuccess) mirrorToClones(key, PerUidFirewall.RULE_DENY)
        return result
    }

    private suspend fun unblock(key: String, command: String): ShellResult {
        if (AppKey.isSecondary(key)) {
            return if (PerUidFirewall.setRule(context, key, PerUidFirewall.RULE_DEFAULT)) {
                PER_UID_SUCCESS
            } else {
                unresolved(key)
            }
        }

        val result = delegate.exec(command)
        if (result.isEffectivelySuccess) {
            if (mirroringClones()) {
                mirrorToClones(key, PerUidFirewall.RULE_DEFAULT)
            } else {
                restoreSecondaryRules(AppKey.packageOf(key))
            }
        }
        return result
    }


    private suspend fun mirrorToClones(key: String, rule: Int) {
        if (!mirroringClones() || AppKey.isSecondary(key)) return
        for (cloneKey in cloneKeysOf(AppKey.packageOf(key))) {
            PerUidFirewall.setRule(context, cloneKey, rule)
        }
    }

    private fun mirroringClones(): Boolean = !MultiUserApps.isEnabled(context)

    private fun cloneKeysOf(packageName: String): List<String> =
        MultiUserApps.cachedSnapshot(context).apps
            .filter { it.packageName == packageName }
            .map { it.key }

    private suspend fun restoreSecondaryRules(packageName: String) {
        for (key in activeSecondaryKeys()) {
            if (AppKey.packageOf(key) == packageName) {
                PerUidFirewall.setRule(context, key, PerUidFirewall.RULE_DENY)
            }
        }
    }

    private suspend fun clearSecondaryRules() {
        for (key in activeSecondaryKeys()) {
            PerUidFirewall.setRule(context, key, PerUidFirewall.RULE_DEFAULT)
        }
        if (!mirroringClones()) return
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        for (key in FirewallUtils.loadActivePackages(prefs).filterNot { AppKey.isSecondary(it) }) {
            for (cloneKey in cloneKeysOf(key)) {
                PerUidFirewall.setRule(context, cloneKey, PerUidFirewall.RULE_DEFAULT)
            }
        }
    }

    private fun activeSecondaryKeys(): List<String> {
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        return FirewallUtils.loadActivePackages(prefs).filter { AppKey.isSecondary(it) }
    }

    private fun unresolved(key: String) = ShellResult(
        exitCode = 1,
        stdout = "",
        stderr = "No uid known for $key; refresh the app list while the backend is connected"
    )

    private companion object {
        val BLOCK_COMMAND =
            Regex("""cmd connectivity set-package-networking-enabled false (\S+)""")
        val UNBLOCK_COMMAND =
            Regex("""cmd connectivity set-package-networking-enabled true (\S+)""")

        const val CHAIN3_DISABLE = "cmd connectivity set-chain3-enabled false"

        val PER_UID_SUCCESS = ShellResult(exitCode = 0, stdout = "per-uid rule applied", stderr = "")
    }
}
