package com.arslan.shizuwall.shell

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Shell executor that runs commands through root via a reused `su` session.

class RootShellExecutor : ShellExecutor {

    companion object {
        private const val COMMAND_TIMEOUT_MS = 20_000L

        private val lock = Mutex()

        @Volatile
        private var session: Session? = null

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

        fun shutdown() {
            session?.close()
            session = null
            RootUidFirewallSession.shutdown()
        }

        private fun execOneShot(command: String): ShellResult {
            return try {
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

    override suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        lock.withLock {
            val active = session?.takeIf { it.isAlive } ?: Session.open()?.also { session = it }
            if (active == null) return@withLock execOneShot(command)

            val result = active.run(command)
            if (result != null) return@withLock result

            active.close()
            session = null
            execOneShot(command)
        }
    }

    private class Session(
        private val process: Process,
        private val stdin: BufferedWriter,
        private val stdout: LinkedBlockingQueue<String>,
        private val stderr: LinkedBlockingQueue<String>,
        private val token: String
    ) {

        private var sequence = 0L

        val isAlive: Boolean get() = process.isAlive

        fun run(command: String): ShellResult? {
            val marker = "$token-${sequence++}"
            stdout.clear()
            stderr.clear()
            try {
                stdin.write(command)
                stdin.write("\n__sw_rc=\$?\n")
                stdin.write("echo $marker \$__sw_rc\n")
                stdin.write("echo $marker 1>&2\n")
                stdin.flush()
            } catch (_: Exception) {
                return null
            }

            val deadline = System.currentTimeMillis() + COMMAND_TIMEOUT_MS
            val out = StringBuilder()
            var exitCode = -1
            while (true) {
                val line = poll(stdout, deadline) ?: return null
                if (line.startsWith(marker)) {
                    exitCode = line.removePrefix(marker).trim().toIntOrNull() ?: -1
                    break
                }
                out.append(line).append('\n')
            }

            val err = StringBuilder()
            while (true) {
                val line = poll(stderr, deadline) ?: return null
                if (line.startsWith(marker)) break
                err.append(line).append('\n')
            }

            return ShellResult(exitCode, out.toString(), err.toString())
        }

        private fun poll(queue: LinkedBlockingQueue<String>, deadline: Long): String? {
            while (true) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return null
                val line = queue.poll(remaining.coerceAtMost(500L), TimeUnit.MILLISECONDS)
                if (line != null) return line
                if (!process.isAlive) return null
            }
        }

        fun close() {
            try {
                stdin.write("exit\n")
                stdin.flush()
                stdin.close()
            } catch (_: Exception) {
            }
            process.destroy()
        }

        companion object {
            fun open(): Session? {
                return try {
                    val process = ProcessBuilder("su").start()
                    val stdout = LinkedBlockingQueue<String>()
                    val stderr = LinkedBlockingQueue<String>()
                    pump(process.inputStream.bufferedReader(), stdout)
                    pump(process.errorStream.bufferedReader(), stderr)
                    val session = Session(
                        process,
                        BufferedWriter(OutputStreamWriter(process.outputStream)),
                        stdout,
                        stderr,
                        "__SW_" + UUID.randomUUID().toString().replace("-", "")
                    )
                    val probe = session.run("id")
                    if (probe == null || !probe.success || !probe.stdout.contains("uid=0")) {
                        session.close()
                        null
                    } else {
                        session
                    }
                } catch (_: Exception) {
                    null
                }
            }

            private fun pump(reader: BufferedReader, queue: LinkedBlockingQueue<String>) {
                Thread {
                    try {
                        reader.forEachLine { queue.put(it) }
                    } catch (_: Exception) {
                    }
                }.apply {
                    isDaemon = true
                    start()
                }
            }
        }
    }
}
