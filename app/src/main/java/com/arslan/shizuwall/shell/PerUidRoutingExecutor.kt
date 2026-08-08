package com.arslan.shizuwall.shell

import android.content.Context
import com.arslan.shizuwall.WorkingMode
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

    override suspend fun execBatch(commands: List<String>): List<ShellResult> {
        if (commands.isEmpty()) return emptyList()
        if (commands.any { command ->
                val trimmed = command.trim()
                !BLOCK_COMMAND.matches(trimmed) &&
                    !UNBLOCK_COMMAND.matches(trimmed)
            }) {
            return commands.map { exec(it) }
        }

        val results = arrayOfNulls<ShellResult>(commands.size)
        val primaryIndexes = mutableListOf<Int>()
        val primaryCommands = mutableListOf<String>()
        val uidRules = mutableListOf<Pair<String, Int>>()
        val uidIndexes = mutableListOf<Int>()

        commands.forEachIndexed { index, command ->
            val trimmed = command.trim()
            val blockMatch = BLOCK_COMMAND.matchEntire(trimmed)
            val unblockMatch = UNBLOCK_COMMAND.matchEntire(trimmed)
            val key = blockMatch?.groupValues?.get(1) ?: unblockMatch!!.groupValues[1]
            val rule = if (blockMatch != null) PerUidFirewall.RULE_DENY else PerUidFirewall.RULE_DEFAULT
            // In root mode `cmd connectivity` is very slow per invocation (each spawns a
            // full app_process). Route every rule through the batched per-uid daemon instead,
            // which applies IConnectivityManager.setUidFirewallRule on the same chain.
            if (AppKey.isSecondary(key) || isRootMode()) {
                uidRules += key to rule
                uidIndexes += index
            } else {
                primaryIndexes += index
                primaryCommands += command
            }
        }

        val primaryResults = if (primaryCommands.isNotEmpty()) {
            delegate.execBatch(primaryCommands)
        } else {
            emptyList()
        }
        primaryIndexes.forEachIndexed { index, commandIndex ->
            results[commandIndex] = primaryResults[index]
        }

        val uidResults = PerUidFirewall.setRules(context, uidRules)
        uidIndexes.forEachIndexed { index, commandIndex ->
            results[commandIndex] = if (uidResults[index]) PER_UID_SUCCESS else unresolved(uidRules[index].first)
        }

        val mirroredRules = mutableListOf<Pair<String, Int>>()
        primaryIndexes.forEachIndexed { index, commandIndex ->
            val result = primaryResults[index]
            if (!result.isEffectivelySuccess) return@forEachIndexed
            val command = primaryCommands[index].trim()
            val key = BLOCK_COMMAND.matchEntire(command)?.groupValues?.get(1)
                ?: UNBLOCK_COMMAND.matchEntire(command)!!.groupValues[1]
            val rule = if (BLOCK_COMMAND.matches(command)) PerUidFirewall.RULE_DENY else PerUidFirewall.RULE_DEFAULT
            if (mirroringClones()) {
                mirroredRules += cloneKeysOf(AppKey.packageOf(key)).map { it to rule }
            } else if (rule == PerUidFirewall.RULE_DEFAULT) {
                mirroredRules += activeSecondaryKeys()
                    .filter { AppKey.packageOf(it) == AppKey.packageOf(key) }
                    .map { it to PerUidFirewall.RULE_DENY }
            }
        }
        uidIndexes.forEachIndexed { index, commandIndex ->
            val (key, rule) = uidRules[index]
            if (AppKey.isSecondary(key) || !uidResults[index]) return@forEachIndexed
            val targetRule = if (mirroringClones()) rule else PerUidFirewall.RULE_DENY
            mirroredRules += cloneKeysOf(AppKey.packageOf(key)).map { it to targetRule }
        }
        if (mirroredRules.isNotEmpty()) PerUidFirewall.setRules(context, mirroredRules)

        return results.map { it!! }
    }

    private suspend fun block(key: String, command: String): ShellResult {
        val usePerUid = AppKey.isSecondary(key) || isRootMode()
        val result = if (usePerUid) {
            if (PerUidFirewall.setRule(context, key, PerUidFirewall.RULE_DENY)) {
                PER_UID_SUCCESS
            } else {
                unresolved(key)
            }
        } else {
            delegate.exec(command)
        }
        if (result.isEffectivelySuccess) {
            if (usePerUid) mirrorPrimaryRule(key, PerUidFirewall.RULE_DENY)
            else mirrorToClones(key, PerUidFirewall.RULE_DENY)
        }
        return result
    }

    private suspend fun unblock(key: String, command: String): ShellResult {
        val usePerUid = AppKey.isSecondary(key) || isRootMode()
        if (usePerUid) {
            if (!PerUidFirewall.setRule(context, key, PerUidFirewall.RULE_DEFAULT)) {
                return unresolved(key)
            }
            mirrorPrimaryRule(key, PerUidFirewall.RULE_DEFAULT)
            return PER_UID_SUCCESS
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

    private fun isRootMode(): Boolean {
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        return WorkingMode.fromName(prefs.getString(MainActivity.KEY_WORKING_MODE, null)) == WorkingMode.ROOT
    }


    private suspend fun mirrorToClones(key: String, rule: Int) {
        if (!mirroringClones() || AppKey.isSecondary(key)) return
        for (cloneKey in cloneKeysOf(AppKey.packageOf(key))) {
            PerUidFirewall.setRule(context, cloneKey, rule)
        }
    }

    private suspend fun mirrorPrimaryRule(key: String, rule: Int) {
        if (AppKey.isSecondary(key)) return
        val clones = cloneKeysOf(AppKey.packageOf(key))
        if (clones.isEmpty()) return
        val targetRule = if (mirroringClones()) rule else PerUidFirewall.RULE_DENY
        PerUidFirewall.setRules(context, clones.map { it to targetRule })
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
