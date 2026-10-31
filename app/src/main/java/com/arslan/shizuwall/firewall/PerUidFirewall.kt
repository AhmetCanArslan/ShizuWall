package com.arslan.shizuwall.firewall

import android.content.Context
import android.os.Build
import android.util.Log
import com.arslan.shizuwall.BuildConfig
import com.arslan.shizuwall.WorkingMode
import com.arslan.shizuwall.daemon.PersistentDaemonManager
import com.arslan.shizuwall.shell.RootUidFirewallSession
import com.arslan.shizuwall.shizuku.ShizukuUserServiceManager
import com.arslan.shizuwall.ui.MainActivity
import com.arslan.shizuwall.utils.AppIds
import com.arslan.shizuwall.utils.AppKey
import com.arslan.shizuwall.utils.MultiUserApps
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

object PerUidFirewall {

    private const val TAG = "PerUidFirewall"
    private const val CHAIN_OEM_DENY_3 = 9
    private const val CHAIN_METERED_DENY_ADMIN = 12
    const val RULE_DEFAULT = 0
    const val RULE_DENY = 2
    // Keep in sync with SystemDaemon.FW_UID_RULES_COMMAND.
    private const val DAEMON_BATCH_COMMAND = "fw-uid-rules"
    private const val DAEMON_CHAIN_COMMAND = "fw-chain"
    // Keep in sync with SystemDaemon.MAX_COMMAND_LENGTH.
    private const val MAX_BATCH_COMMAND_LENGTH = 4096
    private const val DAEMON_ASSET_NAME = "daemon.bin"
    private const val DAEMON_DEX_NAME = "daemon.dex"
    private const val DAEMON_STAMP_NAME = "daemon.dex.version"

    const val UNRESOLVED_UID_ERROR = "unresolved-uid"
    private const val NO_RESULT_ERROR = "helper returned no result"
    private const val OUTDATED_HELPER_ERROR = "invalid uid rule"

    data class RuleOutcome(val success: Boolean, val error: String)

    private val OK = RuleOutcome(true, "")
    private val UNRESOLVED = RuleOutcome(false, UNRESOLVED_UID_ERROR)

    suspend fun setRules(context: Context, rules: List<Pair<String, Int>>): List<RuleOutcome> = withContext(Dispatchers.IO) {
        if (rules.isEmpty()) return@withContext emptyList()
        val mobileOnly = mobileOnlyKeys(context)
        val clearMetered = supportsMobileOnly() && context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
            .contains(MainActivity.KEY_MOBILE_ONLY_APPS)
        val perCopy = MultiUserApps.isEnabled(context)
        val prepared = rules.map { (key, rule) -> prepare(context, key, rule, chainsFor(mobileOnly, perCopy, clearMetered, key, rule)) }
        val batch = prepared.filterIsInstance<Prepared.Applicable>()
            .flatMap { it.rules }
            .associateBy { it.uid to it.chain }
            .values.toList()
        if (batch.isEmpty()) return@withContext prepared.map { outcomeOf(it, emptyMap()) }

        val applied = dispatch(context, batch)
        val resultPerTarget = batch.mapIndexed { index, rule ->
            (rule.uid to rule.chain) to applied.getOrElse(index) { RuleOutcome(false, NO_RESULT_ERROR) }
        }.toMap()
        prepared.map { outcomeOf(it, resultPerTarget) }
    }

    suspend fun setChainEnabled(context: Context, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val command = "$DAEMON_CHAIN_COMMAND ${if (enabled) 1 else 0}"
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        runCatching {
            when (WorkingMode.fromName(prefs.getString(MainActivity.KEY_WORKING_MODE, null))) {
                WorkingMode.SHIZUKU -> ShizukuUserServiceManager.obtain()?.setFirewallChainEnabled(enabled)
                WorkingMode.LADB -> PersistentDaemonManager(context).executeCommand(command)
                WorkingMode.ROOT -> RootUidFirewallSession.execute(extractHelperDex(context).absolutePath, command)
            }
        }.onFailure { Log.w(TAG, "Chain fallback failed", it) }.getOrNull()?.trim() == "OK"
    }

    fun requiresUidPath(context: Context, key: String): Boolean {
        val uid = resolveUid(context, key) ?: return false
        return !AppIds.isBlockable(uid)
    }

    fun supportsMobileOnly(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun mobileOnlyKeys(context: Context): Set<String> {
        if (!supportsMobileOnly()) return emptySet()
        return context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
            .getStringSet(MainActivity.KEY_MOBILE_ONLY_APPS, emptySet()).orEmpty()
    }

    private fun chainsFor(mobileOnly: Set<String>, perCopy: Boolean, clearMetered: Boolean, key: String, rule: Int): List<Int> = when {
        rule == RULE_DEFAULT && clearMetered -> listOf(CHAIN_OEM_DENY_3, CHAIN_METERED_DENY_ADMIN)
        FirewallTargets.isMobileOnly(mobileOnly, key, perCopy) -> listOf(CHAIN_METERED_DENY_ADMIN)
        else -> listOf(CHAIN_OEM_DENY_3)
    }

    fun isBlockableKey(context: Context, key: String): Boolean {
        val uid = resolveUid(context, key) ?: return true
        return AppIds.isBlockable(uid)
    }

    private fun prepare(context: Context, key: String, rule: Int, chains: List<Int>): Prepared {
        val uid = resolveUid(context, key) ?: return Prepared.Skipped(UNRESOLVED)
        if (AppIds.isBlockable(uid)) return Prepared.Applicable(chains.map { UidRule(uid, rule, it) })
        if (rule == RULE_DEFAULT) return Prepared.Skipped(OK)
        return Prepared.Skipped(RuleOutcome(false, systemAppError(key, uid)))
    }

    private fun outcomeOf(prepared: Prepared, results: Map<Pair<Int, Int>, RuleOutcome>): RuleOutcome = when (prepared) {
        is Prepared.Skipped -> prepared.outcome
        is Prepared.Applicable -> prepared.rules
            .map { results[it.uid to it.chain] ?: RuleOutcome(false, NO_RESULT_ERROR) }
            .firstOrNull { !it.success } ?: OK
    }

    private fun systemAppError(key: String, uid: Int): String =
        "Can't set firewall rule for system app $key with appId ${AppIds.appIdOf(uid)}"

    private suspend fun dispatch(context: Context, batch: List<UidRule>): List<RuleOutcome> {
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        return when (WorkingMode.fromName(prefs.getString(MainActivity.KEY_WORKING_MODE, null))) {
            WorkingMode.SHIZUKU -> applyViaShizuku(batch)
            WorkingMode.LADB -> applyViaDaemon(context, batch)
            WorkingMode.ROOT -> applyViaRoot(context, batch)
        }
    }

    private fun resolveUid(context: Context, key: String): Int? {
        if (!AppKey.isSecondary(key)) return uidOf(context, key)
        MultiUserApps.cachedUid(context, key)?.let { return it }
        val uid = uidOf(context, AppKey.packageOf(key)) ?: return null
        return AppKey.userIdOf(key) * AppIds.PER_USER_RANGE + AppIds.appIdOf(uid)
    }

    private fun uidOf(context: Context, packageName: String): Int? = try {
        context.packageManager.getApplicationInfo(
            packageName,
            android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS or
                android.content.pm.PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
        ).uid
    } catch (_: Exception) {
        null
    }

    suspend fun shizukuBatchReady(): Boolean = withContext(Dispatchers.IO) {
        runCatching { Shizuku.pingBinder() && ShizukuUserServiceManager.obtain() != null }
            .getOrDefault(false)
    }

    private suspend fun applyViaShizuku(rules: List<UidRule>): List<RuleOutcome> {
        return try {
            if (!Shizuku.pingBinder()) return rules.map { RuleOutcome(false, "Shizuku is not running") }
            val service = ShizukuUserServiceManager.obtain()
                ?: return rules.map { RuleOutcome(false, "Shizuku user service is unavailable") }
            val outcomes = HashMap<UidRule, RuleOutcome>(rules.size)
            rules.groupBy { it.chain }.forEach { (chain, group) ->
                val uids = IntArray(group.size) { group[it].uid }
                val values = IntArray(group.size) { group[it].rule }
                val errors = service.setUidFirewallRules(chain, uids, values)
                group.forEachIndexed { index, rule ->
                    outcomes[rule] = when {
                        index >= errors.size -> RuleOutcome(false, NO_RESULT_ERROR)
                        errors[index].isNullOrBlank() -> OK
                        else -> RuleOutcome(false, errors[index])
                    }
                }
            }
            rules.map { outcomes.getValue(it) }
        } catch (t: Throwable) {
            Log.w(TAG, "Per-uid rules via Shizuku failed", t)
            rules.map { RuleOutcome(false, failureText(t)) }
        }
    }

    private suspend fun applyViaDaemon(context: Context, rules: List<UidRule>): List<RuleOutcome> {
        return try {
            val manager = PersistentDaemonManager(context)
            applyBatched(rules, "Daemon") { manager.executeCommand(it) }
        } catch (t: Throwable) {
            Log.w(TAG, "Per-uid rules via daemon failed", t)
            rules.map { RuleOutcome(false, failureText(t)) }
        }
    }

    private suspend fun applyViaRoot(context: Context, rules: List<UidRule>): List<RuleOutcome> {
        return try {
            val dex = extractHelperDex(context)
            applyBatched(rules, "Root") { RootUidFirewallSession.execute(dex.absolutePath, it) }
        } catch (t: Throwable) {
            Log.w(TAG, "Per-uid rules via root failed", t)
            rules.map { RuleOutcome(false, failureText(t)) }
        }
    }

    private fun failureText(t: Throwable): String = t.message?.takeIf { it.isNotBlank() } ?: t.toString()

    fun extractHelperDex(context: Context): File {
        val dex = File(context.filesDir, DAEMON_DEX_NAME)
        val stamp = File(context.filesDir, DAEMON_STAMP_NAME)
        val version = BuildConfig.VERSION_CODE.toString()
        if (dex.length() > 0 && runCatching { stamp.readText() }.getOrNull() == version) return dex

        stamp.delete()
        context.assets.open(DAEMON_ASSET_NAME).use { input ->
            dex.outputStream().use { output -> input.copyTo(output) }
        }
        stamp.writeText(version)
        File(context.cacheDir, DAEMON_DEX_NAME).delete()
        return dex
    }

    private suspend fun applyBatched(
        rules: List<UidRule>,
        backend: String,
        send: suspend (String) -> String?
    ): List<RuleOutcome> {
        val results = ArrayList<RuleOutcome>(rules.size)
        for (chunk in chunkRules(rules)) {
            val response = send("$DAEMON_BATCH_COMMAND ${encode(chunk)}")
            results += parseBatchResponse(response, chunk, backend)
        }
        return results
    }

    private fun chunkRules(rules: List<UidRule>): List<List<UidRule>> {
        val budget = MAX_BATCH_COMMAND_LENGTH - DAEMON_BATCH_COMMAND.length - 1
        val chunks = mutableListOf<List<UidRule>>()
        var current = mutableListOf<UidRule>()
        var length = 0
        for (rule in rules) {
            val encoded = encode(rule).length
            val added = if (current.isEmpty()) encoded else encoded + 1
            if (current.isNotEmpty() && length + added > budget) {
                chunks += current
                current = mutableListOf()
                length = encoded
            } else {
                length += added
            }
            current += rule
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }

    private fun encode(rule: UidRule): String =
        if (rule.chain == CHAIN_OEM_DENY_3) "${rule.uid}:${rule.rule}" else "${rule.uid}:${rule.rule}:${rule.chain}"

    private fun encode(rules: List<UidRule>): String = rules.joinToString(",") { encode(it) }

    private fun parseBatchResponse(
        response: String?,
        rules: List<UidRule>,
        backend: String
    ): List<RuleOutcome> {
        val entries = response?.trim()?.split(';').orEmpty()
        val results = rules.mapIndexed { index, rule ->
            val entry = entries.getOrNull(index)?.trim()
            when {
                entry == "OK ${rule.uid} ${rule.rule}" -> OK
                entry.isNullOrEmpty() -> RuleOutcome(false, "$backend helper returned no result")
                entry.contains(OUTDATED_HELPER_ERROR) -> RuleOutcome(false, "$backend helper is outdated, restart it to apply mobile data rules")
                else -> RuleOutcome(false, entry)
            }
        }
        if (results.any { !it.success }) {
            Log.w(TAG, "$backend rejected per-uid rules: ${response ?: "helper unavailable"}")
        }
        return results
    }

    private data class UidRule(val uid: Int, val rule: Int, val chain: Int)

    private sealed interface Prepared {
        data class Applicable(val rules: List<UidRule>) : Prepared
        data class Skipped(val outcome: RuleOutcome) : Prepared
    }
}
