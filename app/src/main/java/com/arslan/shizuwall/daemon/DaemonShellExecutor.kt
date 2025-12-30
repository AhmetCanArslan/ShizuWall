package com.arslan.shizuwall.daemon

import android.content.Context
import android.util.Log
import com.arslan.shizuwall.shell.ShellExecutor
import com.arslan.shizuwall.shell.ShellResult
import kotlinx.coroutines.delay

/**
 * Shell executor that runs commands via the persistent background daemon.
 * Features:
 * - Automatic retry with exponential backoff
 * - Proper error code extraction
 * - Connection health checking
 */
class DaemonShellExecutor(private val context: Context) : ShellExecutor {
    private val daemonManager = PersistentDaemonManager(context)
    
    companion object {
        private const val TAG = "DaemonShellExecutor"
        private const val MAX_RETRIES = 3
        private const val INITIAL_DELAY_MS = 100L
    }

    override suspend fun exec(command: String): ShellResult {
        var lastError: String? = null
        var delayMs = INITIAL_DELAY_MS
        
        repeat(MAX_RETRIES) { attempt ->
            val result = daemonManager.executeCommand(command).trim()
            
            // Check for connection errors (should retry)
            if (result.startsWith("Error: Daemon not responding")) {
                lastError = result
                Log.w(TAG, "Attempt ${attempt + 1}/$MAX_RETRIES failed: $result")
                if (attempt < MAX_RETRIES - 1) {
                    delay(delayMs)
                    delayMs *= 2 // Exponential backoff
                }
                return@repeat
            }
            
            // Check for authorization errors (don't retry)
            if (result == "Error: Unauthorized") {
                Log.e(TAG, "Unauthorized - token mismatch")
                return ShellResult(exitCode = 126, stdout = "", stderr = result)
            }
            
            // Parse command result
            return parseResult(result)
        }
        
        // All retries failed
        return ShellResult(
            exitCode = 255,
            stdout = "",
            stderr = lastError ?: "Unknown error"
        )
    }
    
    private fun parseResult(result: String): ShellResult {
        // Check for explicit error patterns from daemon
        val errorPattern = Regex("""Error \(code (\d+)\): (.*)""")
        val match = errorPattern.find(result)
        if (match != null) {
            val code = match.groupValues[1].toIntOrNull() ?: 1
            val message = match.groupValues[2]
            return ShellResult(exitCode = code, stdout = "", stderr = message)
        }
        
        // Check for generic "Error:" prefix
        if (result.startsWith("Error:")) {
            return ShellResult(exitCode = 1, stdout = "", stderr = result.removePrefix("Error:").trim())
        }
        
        // Check for "Command finished with exit code X" pattern
        val exitCodePattern = Regex("""Command finished with exit code (\d+)""")
        val exitMatch = exitCodePattern.find(result)
        if (exitMatch != null) {
            val code = exitMatch.groupValues[1].toIntOrNull() ?: 0
            val output = result.replace(exitCodePattern, "").trim()
            return if (code == 0) {
                ShellResult(exitCode = 0, stdout = output, stderr = "")
            } else {
                ShellResult(exitCode = code, stdout = "", stderr = output.ifEmpty { "Command failed" })
            }
        }
        
        // Success case
        return ShellResult(exitCode = 0, stdout = result, stderr = "")
    }
}
