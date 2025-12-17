package com.arslan.shizuwall.shell

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result of a shell execution.
 */
data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val success: Boolean get() = exitCode == 0
}

/**
 * Abstraction for running shell commands. Implementations should perform I/O on IO dispatcher.
 */
interface ShellExecutor {
    suspend fun exec(command: String): ShellResult
}
