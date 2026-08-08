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
        if (trimmed == CHAIN3_DISABLE) {
            val result = delegate.exec(command)
            clearSecondaryRules()
            return result
        }
        if (!isFirewallCommand(trimmed)) return delegate.exec(command)
        return execBatch(listOf(command)).first()
    }

    override suspend fun execBatch(commands: List<String>): List<ShellResult> {
        if (commands.isEmpty()) return emptyList()
        if (commands.any { !isFirewallCommand(it.trim()) }) return commands.map { exec(it) }

        val requested = commands.map { parse(it.trim()) }
        val mirroring = mirroringClones()
        val rootMode = isRootMode()
        val activeSecondary = if (mirroring) emptySet() else activeSecondaryKeys().toSet()

        val effective = LinkedHashMap<String, Int>()
        requested.forEach { (key, rule) ->
            derivedCloneRules(key, rule, mirroring, rootMode, activeSecondary)
                .forEach { (cloneKey, cloneRule) -> effective.putIfAbsent(cloneKey, cloneRule) }
        }
        requested.forEach { (key, rule) -> effective[key] = rule }

        val (shellRules, uidRules) = effective.toList()
            .partition { !AppKey.isSecondary(it.first) && !rootMode }

        val shellResults = if (shellRules.isEmpty()) {
            emptyList()
        } else {
            delegate.execBatch(shellRules.map { (key, rule) -> commandFor(key, rule) })
        }
        val uidResults = PerUidFirewall.setRules(context, uidRules)

        val resultsByKey = HashMap<String, ShellResult>(effective.size)
        shellRules.forEachIndexed { index, (key, _) -> resultsByKey[key] = shellResults[index] }
        uidRules.forEachIndexed { index, (key, _) ->
            resultsByKey[key] = if (uidResults[index]) PER_UID_SUCCESS else unresolved(key)
        }
        return requested.map { resultsByKey.getValue(it.first) }
    }

    private fun derivedCloneRules(
        key: String,
        rule: Int,
        mirroring: Boolean,
        rootMode: Boolean,
        activeSecondary: Set<String>
    ): List<Pair<String, Int>> {
        if (AppKey.isSecondary(key)) return emptyList()
        val clones = cloneKeysOf(AppKey.packageOf(key))
        if (clones.isEmpty()) return emptyList()
        if (mirroring) return clones.map { it to rule }
        if (rootMode) return emptyList()
        return if (rule == PerUidFirewall.RULE_DEFAULT) {
            clones.filter { it in activeSecondary }.map { it to PerUidFirewall.RULE_DENY }
        } else {
            clones.filterNot { it in activeSecondary }.map { it to PerUidFirewall.RULE_DEFAULT }
        }
    }

    private fun isRootMode(): Boolean {
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        return WorkingMode.fromName(prefs.getString(MainActivity.KEY_WORKING_MODE, null)) == WorkingMode.ROOT
    }

    private fun mirroringClones(): Boolean = !MultiUserApps.isEnabled(context)

    private fun cloneKeysOf(packageName: String): List<String> =
        MultiUserApps.cachedSnapshot(context).apps
            .filter { it.packageName == packageName }
            .map { it.key }

    private suspend fun clearSecondaryRules() {
        val keys = LinkedHashSet<String>(activeSecondaryKeys())
        if (mirroringClones()) {
            val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
            FirewallUtils.loadActivePackages(prefs)
                .filterNot { AppKey.isSecondary(it) }
                .forEach { keys.addAll(cloneKeysOf(it)) }
        }
        if (keys.isEmpty()) return
        PerUidFirewall.setRules(context, keys.map { it to PerUidFirewall.RULE_DEFAULT })
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

        fun isFirewallCommand(trimmed: String): Boolean =
            BLOCK_COMMAND.matches(trimmed) || UNBLOCK_COMMAND.matches(trimmed)

        fun parse(trimmed: String): Pair<String, Int> {
            BLOCK_COMMAND.matchEntire(trimmed)?.let { return it.groupValues[1] to PerUidFirewall.RULE_DENY }
            return UNBLOCK_COMMAND.matchEntire(trimmed)!!.groupValues[1] to PerUidFirewall.RULE_DEFAULT
        }

        fun commandFor(key: String, rule: Int): String {
            val enabled = rule == PerUidFirewall.RULE_DEFAULT
            return "cmd connectivity set-package-networking-enabled $enabled $key"
        }
    }
}
