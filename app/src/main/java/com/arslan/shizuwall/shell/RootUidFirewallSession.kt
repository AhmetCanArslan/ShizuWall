package com.arslan.shizuwall.shell

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object RootUidFirewallSession {

    private const val COMMAND_TIMEOUT_MS = 20_000L
    private const val SERVER_COMMAND = "fw-uid-server"

    private val lock = Mutex()

    @Volatile
    private var session: Session? = null

    suspend fun execute(dexPath: String, command: String): String? = withContext(Dispatchers.IO) {
        lock.withLock {
            val active = session?.takeIf { it.isAlive } ?: Session.open(dexPath)?.also { session = it }
            if (active == null) return@withLock null

            val response = active.execute(command)
            if (response != null) return@withLock response

            active.close()
            session = null
            val replacement = Session.open(dexPath) ?: return@withLock null
            session = replacement
            replacement.execute(command)
        }
    }

    fun shutdown() {
        session?.close()
        session = null
    }

    private class Session(
        private val process: Process,
        private val stdin: BufferedWriter,
        private val output: LinkedBlockingQueue<String>
    ) {
        val isAlive: Boolean get() = process.isAlive

        fun execute(command: String): String? {
            try {
                stdin.write(command)
                stdin.write('\n'.code)
                stdin.flush()
            } catch (_: Exception) {
                return null
            }
            return poll()
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

        private fun poll(): String? {
            val deadline = System.currentTimeMillis() + COMMAND_TIMEOUT_MS
            while (true) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return null
                output.poll(remaining.coerceAtMost(500L), TimeUnit.MILLISECONDS)?.let { return it }
                if (!process.isAlive) return null
            }
        }

        companion object {
            fun open(dexPath: String): Session? {
                return try {
                    val process = ProcessBuilder("su").start()
                    val output = LinkedBlockingQueue<String>()
                    val errors = LinkedBlockingQueue<String>()
                    pump(process.inputStream.bufferedReader(), output)
                    pump(process.errorStream.bufferedReader(), errors)
                    val session = Session(
                        process,
                        BufferedWriter(OutputStreamWriter(process.outputStream)),
                        output
                    )
                    session.stdin.write(
                        "CLASSPATH=$dexPath app_process / " +
                            "com.arslan.shizuwall.daemon.SystemDaemon $SERVER_COMMAND\n"
                    )
                    session.stdin.flush()
                    if (session.poll() != "READY") {
                        session.close()
                        null
                    } else {
                        session
                    }
                } catch (_: Exception) {
                    null
                }
            }

            private fun pump(reader: BufferedReader, output: LinkedBlockingQueue<String>) {
                Thread {
                    try {
                        reader.forEachLine { output.put(it) }
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
