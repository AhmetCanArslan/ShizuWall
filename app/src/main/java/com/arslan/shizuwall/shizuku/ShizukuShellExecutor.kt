package com.arslan.shizuwall.shizuku

import com.arslan.shizuwall.shell.ShellExecutor
import com.arslan.shizuwall.shell.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

/**
 * Shell executor that runs commands via Shizuku privileged process creation.
 */
class ShizukuShellExecutor : ShellExecutor {
    companion object {
        private val SHIZUKU_NEW_PROCESS_METHOD by lazy {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
        }
    }

    override suspend fun exec(command: String): ShellResult {
        return withContext(Dispatchers.IO) {
            try {
                val process = SHIZUKU_NEW_PROCESS_METHOD.invoke(
                    null,
                    arrayOf("/system/bin/sh", "-c", command),
                    null,
                    null
                ) as? ShizukuRemoteProcess ?: return@withContext ShellResult(-1, "", "no-process")

                val stdout = process.inputStream.bufferedReader().use { it.readText() }
                val stderr = process.errorStream.bufferedReader().use { it.readText() }
                process.outputStream.close()
                val exitCode = process.waitFor()
                process.destroy()
                ShellResult(exitCode, stdout, stderr)
            } catch (e: Exception) {
                ShellResult(-1, "", e.message ?: "")
            }
        }
    }
}
