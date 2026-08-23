package com.arslan.shizuwall.firewall

import android.content.Context
import android.util.Log
import com.arslan.shizuwall.BuildConfig
import com.arslan.shizuwall.WorkingMode
import com.arslan.shizuwall.daemon.PersistentDaemonManager
import com.arslan.shizuwall.shell.RootUidFirewallSession
import com.arslan.shizuwall.shizuku.ShizukuUserServiceManager
import com.arslan.shizuwall.ui.MainActivity
import com.arslan.shizuwall.utils.AppKey
import com.arslan.shizuwall.utils.MultiUserApps
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

object PerUidFirewall {

    private const val TAG = "PerUidFirewall"
    private const val CHAIN_OEM_DENY_3 = 9
    const val RULE_DEFAULT = 0
    const val RULE_DENY = 2
    // Keep in sync with SystemDaemon.FW_UID_RULES_COMMAND.
    private const val DAEMON_BATCH_COMMAND = "fw-uid-rules"
    // Keep in sync with SystemDaemon.MAX_COMMAND_LENGTH.
    private const val MAX_BATCH_COMMAND_LENGTH = 4096
    private const val DAEMON_ASSET_NAME = "daemon.bin"
    private const val DAEMON_DEX_NAME = "daemon.dex"
    private const val DAEMON_STAMP_NAME = "daemon.dex.version"
    private const val PER_USER_RANGE = 100_000

    const val UNRESOLVED_UID_ERROR = "unresolved-uid"
    private const val NO_RESULT_ERROR = "helper returned no result"

    data class RuleOutcome(val success: Boolean, val error: String)

    private val OK = RuleOutcome(true, "")
    private val UNRESOLVED = RuleOutcome(false, UNRESOLVED_UID_ERROR)

    suspend fun setRules(context: Context, rules: List<Pair<String, Int>>): List<RuleOutcome> = withContext(Dispatchers.IO) {
        if (rules.isEmpty()) return@withContext emptyList()
        val resolved = rules.map { (key, rule) -> resolveUid(context, key)?.let { UidRule(it, rule) } }

        val lastRulePerUid = LinkedHashMap<Int, Int>()
        resolved.forEach { if (it != null) lastRulePerUid[it.uid] = it.rule }
        if (lastRulePerUid.isEmpty()) return@withContext resolved.map { UNRESOLVED }
        val batch = lastRulePerUid.map { (uid, rule) -> UidRule(uid, rule) }

        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val applied = when (WorkingMode.fromName(prefs.getString(MainActivity.KEY_WORKING_MODE, null))) {
            WorkingMode.SHIZUKU -> applyViaShizuku(batch)
            WorkingMode.LADB -> applyViaDaemon(context, batch)
            WorkingMode.ROOT -> applyViaRoot(context, batch)
        }

        val resultPerUid = HashMap<Int, RuleOutcome>(batch.size)
        batch.forEachIndexed { index, rule ->
            resultPerUid[rule.uid] = applied.getOrElse(index) { RuleOutcome(false, NO_RESULT_ERROR) }
        }
        resolved.map { rule ->
            if (rule == null) UNRESOLVED
            else resultPerUid[rule.uid] ?: RuleOutcome(false, NO_RESULT_ERROR)
        }
    }

    private fun resolveUid(context: Context, key: String): Int? {
        if (!AppKey.isSecondary(key)) return appIdOf(context, key)
        MultiUserApps.cachedUid(context, key)?.let { return it }
        val appId = appIdOf(context, AppKey.packageOf(key)) ?: return null
        return AppKey.userIdOf(key) * PER_USER_RANGE + appId % PER_USER_RANGE
    }

    private fun appIdOf(context: Context, packageName: String): Int? = try {
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
            val uids = IntArray(rules.size) { rules[it].uid }
            val values = IntArray(rules.size) { rules[it].rule }
            val errors = service.setUidFirewallRules(CHAIN_OEM_DENY_3, uids, values)
            rules.mapIndexed { index, _ ->
                when {
                    index >= errors.size -> RuleOutcome(false, NO_RESULT_ERROR)
                    errors[index].isNullOrBlank() -> OK
                    else -> RuleOutcome(false, errors[index])
                }
            }
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

    private fun encode(rule: UidRule): String = "${rule.uid}:${rule.rule}"

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
                else -> RuleOutcome(false, entry)
            }
        }
        if (results.any { !it.success }) {
            Log.w(TAG, "$backend rejected per-uid rules: ${response ?: "helper unavailable"}")
        }
        return results
    }

    private data class UidRule(val uid: Int, val rule: Int)
}
