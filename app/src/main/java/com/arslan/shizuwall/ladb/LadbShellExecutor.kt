package com.arslan.shizuwall.ladb

import com.arslan.shizuwall.shell.ShellExecutor
import com.arslan.shizuwall.shell.ShellResult

/**
 * Shell executor that runs commands via LADB (libadb-android).
 * Currently delegates to `LadbManager` stub; will be implemented fully in next steps.
 */
class LadbShellExecutor : ShellExecutor {
    override suspend fun exec(command: String): ShellResult {
        return LadbManager.exec(command)
    }
}
