package com.arslan.shizuwall.daemon

import android.content.Context
import com.arslan.shizuwall.shell.ShellExecutor
import com.arslan.shizuwall.shell.ShellResult

/**
 * Shell executor that runs commands via the persistent background daemon.
 */
class DaemonShellExecutor(private val context: Context) : ShellExecutor {
    private val daemonManager = PersistentDaemonManager(context)

    override suspend fun exec(command: String): ShellResult {
        val result = daemonManager.executeCommand(command).trim()
        return if (result.contains("Error")) {
            // Try to extract exit code if present: "Error (code 1): ..."
            val exitCode = if (result.contains("(code ")) {
                result.substringAfter("(code ").substringBefore(")").toIntOrNull() ?: 1
            } else {
                1
            }
            ShellResult(stdout = "", stderr = result, exitCode = exitCode)
        } else {
            ShellResult(stdout = result, stderr = "", exitCode = 0)
        }
    }
}
