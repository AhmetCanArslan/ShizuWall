package com.arslan.shizuwall.shell

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Shell executor that runs commands through root via `su -c`.

class RootShellExecutor : ShellExecutor {

    companion object {
        //Execute root command to verify root access is granted or not
         
        fun hasRootAccess(): Boolean {
            return try {
                val process = ProcessBuilder("su", "-c", "id")
                    .redirectErrorStream(true)
                    .start()
                process.inputStream.bufferedReader().use { it.readText() }
                val exitCode = process.waitFor()
                process.destroy()
                exitCode == 0
            } catch (_: Exception) {
                false
            }
        }
    }

    override suspend fun exec(command: String): ShellResult {
        return withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder("su", "-c", command).start()
                val stdout = process.inputStream.bufferedReader().use { it.readText() }
                val stderr = process.errorStream.bufferedReader().use { it.readText() }
                val exitCode = process.waitFor()
                process.destroy()
                ShellResult(exitCode, stdout, stderr)
            } catch (e: Exception) {
                ShellResult(-1, "", e.message ?: "")
            }
        }
    }
}
