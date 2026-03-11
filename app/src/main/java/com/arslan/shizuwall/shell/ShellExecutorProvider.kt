package com.arslan.shizuwall.shell

import android.content.Context
import com.arslan.shizuwall.WorkingMode
import com.arslan.shizuwall.ui.MainActivity
import com.arslan.shizuwall.shizuku.ShizukuShellExecutor
import com.arslan.shizuwall.daemon.DaemonShellExecutor

object ShellExecutorProvider {
    fun forContext(context: Context): ShellExecutor {
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val modeName = prefs.getString(MainActivity.KEY_WORKING_MODE, WorkingMode.SHIZUKU.name)
        return when (WorkingMode.fromName(modeName)) {
            WorkingMode.LADB -> DaemonShellExecutor(context)
            WorkingMode.ROOT -> RootShellExecutor()
            else -> ShizukuShellExecutor()
        }
    }
}
