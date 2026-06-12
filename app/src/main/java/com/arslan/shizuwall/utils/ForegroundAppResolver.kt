package com.arslan.shizuwall.utils

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.arslan.shizuwall.WorkingMode
import com.arslan.shizuwall.ui.MainActivity
import java.lang.reflect.Method
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Resolves the current foreground app package.
 *
 * Primary path (SHIZUKU working mode): IActivityTaskManager.getTasks() through a
 * ShizukuBinderWrapper, so the call executes with shell identity. The hidden
 * interface is reached via reflection; the getTasks overload is picked by arity
 * to stay version-aware:
 *  - API 33+:   getTasks(int, boolean, boolean, int)
 *  - API 30-32: getTasks(int, boolean, boolean)
 *  - API < 30:  getTasks(int, boolean)  (defensive; minSdk is 30)
 *
 * Fallback path (LADB/ROOT modes, or when the privileged call fails):
 * UsageStatsManager.queryEvents over the last minute, taking the latest
 * ACTIVITY_RESUMED event. Requires usage access (self-granted via the
 * privileged shell by ForegroundDetectionService on start).
 */
object ForegroundAppResolver {
    private const val TAG = "ForegroundAppResolver"
    private const val USAGE_EVENTS_LOOKBACK_MS = 60_000L

    @Volatile private var atmProxy: Any? = null
    @Volatile private var getTasksMethod: Method? = null
    @Volatile private var exemptionsApplied = false

    fun isShizukuPathAvailable(context: Context): Boolean {
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val mode = WorkingMode.fromName(prefs.getString(MainActivity.KEY_WORKING_MODE, WorkingMode.SHIZUKU.name))
        if (mode != WorkingMode.SHIZUKU) return false
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun hasUsageAccess(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) {
            false
        }
    }

    /** Returns the current foreground package, or null if it cannot be determined. */
    fun getForegroundPackage(context: Context): String? {
        if (isShizukuPathAvailable(context)) {
            getViaActivityTaskManager()?.let { return it }
        }
        return getViaUsageStats(context)
    }

    private fun getViaActivityTaskManager(): String? {
        return try {
            ensureHiddenApiExemptions()
            val proxy = atmProxy ?: buildAtmProxy()?.also { atmProxy = it } ?: return null
            val method = getTasksMethod ?: resolveGetTasks(proxy)?.also { getTasksMethod = it } ?: return null

            val args: Array<Any?> = when (method.parameterTypes.size) {
                4 -> arrayOf(1, false, false, 0)
                3 -> arrayOf(1, false, false)
                else -> arrayOf(1, false)
            }
            val tasks = method.invoke(proxy, *args) as? List<*> ?: return null
            val task = tasks.firstOrNull() ?: return null
            val topActivity = task.javaClass.getField("topActivity").get(task) as? android.content.ComponentName
            topActivity?.packageName
        } catch (t: Throwable) {
            Log.w(TAG, "getTasks via ActivityTaskManager failed, falling back", t)
            // Drop cached handles so the next attempt rebuilds them (Shizuku may have died).
            atmProxy = null
            getTasksMethod = null
            null
        }
    }

    private fun buildAtmProxy(): Any? {
        val raw: IBinder = SystemServiceHelper.getSystemService("activity_task") ?: return null
        val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
        val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
        return asInterface.invoke(null, ShizukuBinderWrapper(raw))
    }

    private fun resolveGetTasks(proxy: Any): Method? {
        val candidates = proxy.javaClass.methods.filter { it.name == "getTasks" }
        val wantedArity = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> 4
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> 3
            else -> 2
        }
        return candidates.firstOrNull { it.parameterTypes.size == wantedArity }
            ?: candidates.maxByOrNull { it.parameterTypes.size }
    }

    private fun ensureHiddenApiExemptions() {
        if (exemptionsApplied) return
        exemptionsApplied = true
        try {
            HiddenApiBypass.addHiddenApiExemptions("")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to add hidden API exemptions", t)
        }
    }

    private fun getViaUsageStats(context: Context): String? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null
            val end = System.currentTimeMillis()
            val events = usm.queryEvents(end - USAGE_EVENTS_LOOKBACK_MS, end)
            var lastPackage: String? = null
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastPackage = event.packageName
                }
            }
            lastPackage
        } catch (t: Throwable) {
            Log.w(TAG, "UsageStats fallback failed", t)
            null
        }
    }
}
