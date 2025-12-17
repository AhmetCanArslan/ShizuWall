package com.arslan.shizuwall.shell

import android.content.Context
import com.arslan.shizuwall.ui.MainActivity
import com.arslan.shizuwall.ladb.LadbShellExecutor
import com.arslan.shizuwall.shizuku.ShizukuShellExecutor

object ShellExecutorProvider {
    fun forContext(context: Context): ShellExecutor {
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val mode = prefs.getString(MainActivity.KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"
        return if (mode == "LADB") LadbShellExecutor(context) else ShizukuShellExecutor()
    }
}
