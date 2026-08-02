package com.arslan.shizuwall.firewall

import android.content.Context
import android.util.Log
import com.arslan.shizuwall.WorkingMode
import com.arslan.shizuwall.daemon.PersistentDaemonManager
import com.arslan.shizuwall.shell.RootShellExecutor
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
        val uid = resolveUid(context, key) ?: return@withContext false
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        when (WorkingMode.fromName(prefs.getString(MainActivity.KEY_WORKING_MODE, null))) {
            WorkingMode.SHIZUKU -> applyViaShizuku(uid, rule)
            WorkingMode.LADB -> applyViaDaemon(context, uid, rule)
            WorkingMode.ROOT -> applyViaRoot(context, uid, rule)
        }
    }

    suspend fun blockPackage(context: Context, packageName: String): Boolean =
        setRule(context, packageName, RULE_DENY)


    private fun resolveUid(context: Context, key: String): Int? {
        if (AppKey.isSecondary(key)) return MultiUserApps.cachedUid(context, key)
        return try {
            context.packageManager.getApplicationInfo(key, 0).uid
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun applyViaShizuku(uid: Int, rule: Int): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            val service = ShizukuUserServiceManager.obtain() ?: return false
            service.setUidFirewallRule(CHAIN_OEM_DENY_3, uid, rule)
        } catch (t: Throwable) {
            Log.w(TAG, "Per-uid rule $rule via Shizuku failed for uid $uid", t)
            false
        }
    }

    private suspend fun applyViaDaemon(context: Context, uid: Int, rule: Int): Boolean {
        return try {
            val response = PersistentDaemonManager(context)
                .executeCommand("$DAEMON_COMMAND $uid $rule")
                .trim()
            if (!response.startsWith("OK")) {
                Log.w(TAG, "Daemon rejected per-uid rule $rule for $uid: $response")
                return false
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Per-uid rule $rule via daemon failed for uid $uid", t)
            false
        }
    }

    // Root has no privileged process to talk to, so run the daemon class for this one rule.
    private suspend fun applyViaRoot(context: Context, uid: Int, rule: Int): Boolean {
        return try {
            val dex = File(context.cacheDir, DAEMON_DEX_NAME)
            if (!dex.exists()) {
                context.assets.open(DAEMON_ASSET_NAME).use { input ->
                    dex.outputStream().use { output -> input.copyTo(output) }
                }
            }
            val result = RootShellExecutor().exec(
                "CLASSPATH=${dex.absolutePath} app_process / " +
                    "com.arslan.shizuwall.daemon.SystemDaemon $DAEMON_COMMAND $uid $rule"
            )
            val ok = result.success && result.stdout.trim().startsWith("OK")
            if (!ok) {
                Log.w(TAG, "Root rejected per-uid rule $rule for $uid: ${result.stdout}${result.stderr}")
            }
            ok
        } catch (t: Throwable) {
            Log.w(TAG, "Per-uid rule $rule via root failed for uid $uid", t)
            false
        }
    }
}
