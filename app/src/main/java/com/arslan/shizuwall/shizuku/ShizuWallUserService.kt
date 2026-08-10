package com.arslan.shizuwall.shizuku

import android.os.IBinder
import android.util.Log
import com.arslan.shizuwall.shell.ShellResult
import kotlin.system.exitProcess

class ShizuWallUserService : IShizuWallUserService.Stub {

    constructor()

    override fun destroy() {
        exitProcess(0)
    }

    override fun setUidFirewallRule(chain: Int, uid: Int, rule: Int): String? {
        return try {
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "connectivity") as? IBinder
                ?: return "connectivity service is unavailable"
            val service = Class.forName("android.net.IConnectivityManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)
                ?: return "IConnectivityManager is unavailable"
            service.javaClass.getMethod(
                "setUidFirewallRule",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            ).invoke(service, chain, uid, rule)
            null
        } catch (t: Throwable) {
            val cause = t.cause ?: t
            if (cause.toString().lowercase().contains(ShellResult.UID_OWNER_MAP_MISSING)) {
                return null
            }
            Log.w(TAG, "setUidFirewallRule failed for uid $uid", cause)
            cause.toString()
        }
    }

    private companion object {
        const val TAG = "ShizuWallUserService"
    }
}
