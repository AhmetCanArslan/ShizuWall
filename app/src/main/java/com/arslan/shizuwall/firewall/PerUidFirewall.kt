package com.arslan.shizuwall.firewall

import android.content.Context
import android.os.IBinder
import android.util.Log
import com.arslan.shizuwall.WorkingMode
import com.arslan.shizuwall.daemon.PersistentDaemonManager
import com.arslan.shizuwall.shell.RootShellExecutor
import com.arslan.shizuwall.ui.MainActivity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Denies networking for a single uid instead of a whole appId.
 *
 * `cmd connectivity set-package-networking-enabled false <pkg>` writes the deny bit for the
 * package's appId in every Android user, so it also blocks work profile / Secure Folder clones
 * (#126). The shell has no per-uid command for this chain, so the rule goes through
 * IConnectivityManager.setUidFirewallRule instead, from a process holding the permission it
 * requires: Shizuku, the LADB daemon, or app_process under root.
 *
 * Only blocking goes through here — unblocking stays on the shell command, whose fan-out clears
 * leftovers in the other profiles.
 */
object PerUidFirewall {

    private const val TAG = "PerUidFirewall"
    private const val CHAIN_OEM_DENY_3 = 9
    private const val RULE_DENY = 2
    // Keep in sync with SystemDaemon.FW_UID_RULE_COMMAND.
    private const val DAEMON_COMMAND = "fw-uid-rule"
    private const val DAEMON_ASSET_NAME = "daemon.bin"
    private const val DAEMON_DEX_NAME = "daemon.dex"

    @Volatile
    private var hiddenApisUnlocked = false

    /** Returns false when the call is not possible, so callers can fall back to the shell command. */
    suspend fun blockPackage(context: Context, packageName: String): Boolean = withContext(Dispatchers.IO) {
        val uid = try {
            context.packageManager.getApplicationInfo(packageName, 0).uid
        } catch (_: Exception) {
            return@withContext false
        }
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        when (WorkingMode.fromName(prefs.getString(MainActivity.KEY_WORKING_MODE, null))) {
            WorkingMode.SHIZUKU -> blockViaShizuku(uid)
            WorkingMode.LADB -> blockViaDaemon(context, uid)
            WorkingMode.ROOT -> blockViaRoot(context, uid)
        }
    }

    private fun blockViaShizuku(uid: Int): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            unlockHiddenApis()
            val binder: IBinder = SystemServiceHelper.getSystemService("connectivity") ?: return false
            val service = Class.forName("android.net.IConnectivityManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, ShizukuBinderWrapper(binder))
                ?: return false
            service.javaClass.getMethod(
                "setUidFirewallRule",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            ).invoke(service, CHAIN_OEM_DENY_3, uid, RULE_DENY)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Per-uid block via Shizuku failed for uid $uid", t)
            false
        }
    }

    private suspend fun blockViaDaemon(context: Context, uid: Int): Boolean {
        return try {
            val response = PersistentDaemonManager(context)
                .executeCommand("$DAEMON_COMMAND $uid $RULE_DENY")
                .trim()
            if (!response.startsWith("OK")) {
                Log.w(TAG, "Daemon rejected per-uid block for $uid: $response")
                return false
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Per-uid block via daemon failed for uid $uid", t)
            false
        }
    }

    // Root has no privileged process to talk to, so run the daemon class for this one rule.
    private suspend fun blockViaRoot(context: Context, uid: Int): Boolean {
        return try {
            val dex = File(context.cacheDir, DAEMON_DEX_NAME)
            if (!dex.exists()) {
                context.assets.open(DAEMON_ASSET_NAME).use { input ->
                    dex.outputStream().use { output -> input.copyTo(output) }
                }
            }
            val result = RootShellExecutor().exec(
                "CLASSPATH=${dex.absolutePath} app_process / " +
                    "com.arslan.shizuwall.daemon.SystemDaemon $DAEMON_COMMAND $uid $RULE_DENY"
            )
            val ok = result.success && result.stdout.trim().startsWith("OK")
            if (!ok) {
                Log.w(TAG, "Root rejected per-uid block for $uid: ${result.stdout}${result.stderr}")
            }
            ok
        } catch (t: Throwable) {
            Log.w(TAG, "Per-uid block via root failed for uid $uid", t)
            false
        }
    }

    // IConnectivityManager is non-SDK, unreachable by reflection without this.
    private fun unlockHiddenApis() {
        if (hiddenApisUnlocked) return
        try {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/net/")
        } catch (t: Throwable) {
            Log.w(TAG, "Could not lift hidden API restrictions", t)
        }
        hiddenApisUnlocked = true
    }
}
