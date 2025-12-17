package com.arslan.shizuwall.ladb

import android.content.Context
import com.arslan.shizuwall.shell.ShellExecutor
import com.arslan.shizuwall.shell.ShellResult

/**
 * Shell executor that runs commands via LADB (libadb-android).
 */
class LadbShellExecutor(private val context: Context) : ShellExecutor {
    override suspend fun exec(command: String): ShellResult {
        return LadbManager.getInstance(context).execShell(command)
    }
}
