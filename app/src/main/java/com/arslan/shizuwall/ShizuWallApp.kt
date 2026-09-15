package com.arslan.shizuwall

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import com.arslan.shizuwall.receivers.FirewallControlReceiver
import com.arslan.shizuwall.shizuku.ShizukuShellExecutor
import com.arslan.shizuwall.ui.MainActivity
import com.arslan.shizuwall.utils.FirewallUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class ShizuWallApp : Application() {

    @Volatile private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { startedActivities++ }
            override fun onActivityStopped(activity: Activity) { startedActivities-- }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        try {
            Shizuku.addBinderReceivedListenerSticky {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { enableIfShizukuStarted() }.onFailure { Log.w(TAG, "Shizuku start check failed", it) }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register Shizuku binder listener", t)
        }
    }

    private suspend fun enableIfShizukuStarted() {
        val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(MainActivity.KEY_WORKING_MODE, WorkingMode.SHIZUKU.name) != WorkingMode.SHIZUKU.name) return
        if (!prefs.getBoolean(MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START, false)) return
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return
        val serverPid = ShizukuShellExecutor().exec("echo \$PPID").stdout.trim()
        if (serverPid.isEmpty()) return
        val serverId = "${Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0)}:$serverPid"
        if (prefs.getString(MainActivity.KEY_SHIZUKU_SERVER_ID, null) == serverId) return
        prefs.edit().putString(MainActivity.KEY_SHIZUKU_SERVER_ID, serverId).apply()
        if (startedActivities > 0 || FirewallUtils.loadFirewallEnabled(prefs)) return

        Log.d(TAG, "Shizuku server $serverId started, enabling firewall")
        sendBroadcast(
            Intent(this, FirewallControlReceiver::class.java).apply {
                action = MainActivity.ACTION_FIREWALL_CONTROL
                putExtra(MainActivity.EXTRA_FIREWALL_ENABLED, true)
                putExtra(FirewallControlReceiver.EXTRA_AUTOMATION_EVENT, true)
            }
        )
    }

    companion object {
        private const val TAG = "ShizuWallApp"
    }
}
