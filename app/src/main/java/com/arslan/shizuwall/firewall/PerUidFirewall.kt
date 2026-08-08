package com.arslan.shizuwall.firewall

import android.content.Context
import android.util.Log
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
    // Keep in sync with SystemDaemon.FW_UID_RULE_COMMAND.
    private const val DAEMON_COMMAND = "fw-uid-rule"
    private const val DAEMON_ASSET_NAME = "daemon.bin"
    private const val DAEMON_DEX_NAME = "daemon.dex"

    suspend fun setRule(context: Context, key: String, rule: Int): Boolean = withContext(Dispatchers.IO) {
        setRules(context, listOf(key to rule)).first()
    }

    suspend fun setRules(context: Context, rules: List<Pair<String, Int>>): List<Boolean> = withContext(Dispatchers.IO) {
        if (rules.isEmpty()) return@withContext emptyList()
        val resolved = rules.map { (key, rule) -> resolveUid(context, key)?.let { UidRule(it, rule) } }
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val applied = when (WorkingMode.fromName(prefs.getString(MainActivity.KEY_WORKING_MODE, null))) {
            WorkingMode.SHIZUKU -> applyViaShizuku(resolved)
            WorkingMode.LADB -> applyViaDaemon(context, resolved)
            WorkingMode.ROOT -> applyViaRoot(context, resolved)
        }
        var appliedIndex = 0
        resolved.map { rule ->
            if (rule == null) false else applied[appliedIndex++]
        }
    }

    private fun resolveUid(context: Context, key: String): Int? {
        if (AppKey.isSecondary(key)) return MultiUserApps.cachedUid(context, key)
        return try {
            context.packageManager.getApplicationInfo(key, 0).uid
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun applyViaShizuku(rules: List<UidRule?>): List<Boolean> {
        return try {
            if (!Shizuku.pingBinder()) return rules.map { false }
            val service = ShizukuUserServiceManager.obtain() ?: return rules.map { false }
            rules.map { rule ->
                rule?.let { service.setUidFirewallRule(CHAIN_OEM_DENY_3, it.uid, it.rule) } ?: false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Per-uid rules via Shizuku failed", t)
            rules.map { false }
        }
    }

    private suspend fun applyViaDaemon(context: Context, rules: List<UidRule?>): List<Boolean> {
        return try {
            rules.map { rule ->
                if (rule == null) {
                    false
                } else {
                    val response = PersistentDaemonManager(context)
                        .executeCommand("$DAEMON_COMMAND ${rule.uid} ${rule.rule}")
                        .trim()
                    if (!response.startsWith("OK")) {
                        Log.w(TAG, "Daemon rejected per-uid rule ${rule.rule} for ${rule.uid}: $response")
                    }
                    response.startsWith("OK")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Per-uid rules via daemon failed", t)
            rules.map { false }
        }
    }

    private suspend fun applyViaRoot(context: Context, rules: List<UidRule?>): List<Boolean> {
        val validRules = rules.filterNotNull()
        if (validRules.isEmpty()) return rules.map { false }
        return try {
            val dex = File(context.cacheDir, DAEMON_DEX_NAME)
            if (!dex.exists()) {
                context.assets.open(DAEMON_ASSET_NAME).use { input ->
                    dex.outputStream().use { output -> input.copyTo(output) }
                }
            }
            val encodedRules = validRules.joinToString(",") { "${it.uid}:${it.rule}" }
            val response = RootUidFirewallSession.execute(
                dex.absolutePath,
                "fw-uid-rules $encodedRules"
            )
            val responses = response?.split(';')?.filter { it.trim().startsWith("OK") }.orEmpty()
            val validResults = validRules.mapIndexed { index, rule ->
                responses.getOrNull(index)?.trim() == "OK ${rule.uid} ${rule.rule}"
            }
            if (validResults.any { !it }) {
                Log.w(TAG, "Root rejected per-uid rules: ${response ?: "helper unavailable"}")
            }
            var validIndex = 0
            rules.map { rule -> if (rule == null) false else validResults[validIndex++] }
        } catch (t: Throwable) {
            Log.w(TAG, "Per-uid rules via root failed", t)
            rules.map { false }
        }
    }

    private data class UidRule(val uid: Int, val rule: Int)
}
