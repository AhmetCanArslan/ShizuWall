package com.arslan.shizuwall.shell

import android.content.Context
import com.arslan.shizuwall.ui.MainActivity
import com.arslan.shizuwall.ladb.LadbShellExecutor
import com.arslan.shizuwall.shizuku.ShizukuShellExecutor
import com.arslan.shizuwall.daemon.PersistentDaemonManager
import com.arslan.shizuwall.daemon.DaemonShellExecutor

object ShellExecutorProvider {
    fun forContext(context: Context): ShellExecutor {
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val mode = prefs.getString(MainActivity.KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"
        
        if (mode == "LADB") {
            val daemonManager = PersistentDaemonManager(context)
            if (daemonManager.isDaemonRunning()) {
                return DaemonShellExecutor(context)
            }
            return LadbShellExecutor(context)
        }
        return ShizukuShellExecutor()
    }
}
