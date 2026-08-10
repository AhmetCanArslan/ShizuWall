package com.arslan.shizuwall.shizuku

import android.os.DeadObjectException
import android.os.IBinder
import android.util.Log
import com.arslan.shizuwall.shell.ShellResult
import java.lang.reflect.Method
import kotlin.system.exitProcess

class ShizuWallUserService : IShizuWallUserService.Stub {

    constructor()

    @Volatile
    private var cachedService: Any? = null

    @Volatile
    private var cachedMethod: Method? = null

    override fun destroy() {
        exitProcess(0)
    }

    override fun setUidFirewallRule(chain: Int, uid: Int, rule: Int): String? =
        applyRule(chain, uid, rule)

    override fun setUidFirewallRules(chain: Int, uids: IntArray, rules: IntArray): Array<String?> {
        val size = minOf(uids.size, rules.size)
        val results = arrayOfNulls<String>(size)
        for (index in 0 until size) {
            results[index] = applyRule(chain, uids[index], rules[index])
        }
        return results
    }

    private fun applyRule(chain: Int, uid: Int, rule: Int): String? {
        return try {
            val service = connectivityService() ?: return "connectivity service is unavailable"
            val method = ruleMethod(service) ?: return "setUidFirewallRule is unavailable"
            method.invoke(service, chain, uid, rule)
            null
        } catch (t: Throwable) {
            val cause = t.cause ?: t
            if (cause.toString().lowercase().contains(ShellResult.UID_OWNER_MAP_MISSING)) {
                return null
            }
            if (cause is DeadObjectException) {
                cachedService = null
                cachedMethod = null
            }
            Log.w(TAG, "setUidFirewallRule failed for uid $uid", cause)
            cause.toString()
        }
    }

    private fun connectivityService(): Any? {
        cachedService?.let { return it }
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, "connectivity") as? IBinder ?: return null
        val service = Class.forName("android.net.IConnectivityManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder) ?: return null
        cachedService = service
        return service
    }

    private fun ruleMethod(service: Any): Method? {
        cachedMethod?.let { return it }
        val method = runCatching {
            service.javaClass.getMethod(
                "setUidFirewallRule",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
        }.getOrNull() ?: return null
        cachedMethod = method
        return method
    }

    private companion object {
        const val TAG = "ShizuWallUserService"
    }
}
