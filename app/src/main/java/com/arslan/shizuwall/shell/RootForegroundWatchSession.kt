package com.arslan.shizuwall.shell

import android.util.Log
import java.io.BufferedWriter
import java.io.OutputStreamWriter

object RootForegroundWatchSession {

    private const val TAG = "RootForegroundWatch"
    private const val WATCH_COMMAND = "fg-watch"

    @Volatile
    private var process: Process? = null

    fun start(dexPath: String, onLine: (String) -> Unit): Boolean {
        stop()
        return try {
            val started = ProcessBuilder("su").start()
            process = started
            BufferedWriter(OutputStreamWriter(started.outputStream)).apply {
                write(
                    "CLASSPATH=$dexPath exec app_process / " +
                        "com.arslan.shizuwall.daemon.SystemDaemon $WATCH_COMMAND\n"
                )
                flush()
            }
            Thread {
                try {
                    started.inputStream.bufferedReader().forEachLine(onLine)
                } catch (_: Exception) {
                } finally {
                    if (process === started) process = null
                }
            }.apply {
                isDaemon = true
                start()
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to start root foreground watch", t)
            process = null
            false
        }
    }

    fun stop() {
        process?.destroy()
        process = null
    }
}
