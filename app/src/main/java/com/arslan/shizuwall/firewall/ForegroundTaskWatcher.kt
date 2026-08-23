package com.arslan.shizuwall.firewall

import android.content.Context
import android.util.Log
import com.arslan.shizuwall.WorkingMode
import com.arslan.shizuwall.daemon.PersistentDaemonManager
import com.arslan.shizuwall.shell.RootForegroundWatchSession
import com.arslan.shizuwall.shizuku.IShizuWallForegroundListener
import com.arslan.shizuwall.shizuku.ShizukuUserServiceManager
import com.arslan.shizuwall.ui.MainActivity
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

object ForegroundTaskWatcher {

    private const val TAG = "ForegroundTaskWatcher"
    private const val WATCH_COMMAND = "fg-watch"
    private const val KEEPALIVE = "."

    @Volatile
    var isActive = false
        private set

    private var mode: WorkingMode? = null
    private var socket: Socket? = null
    private var reader: Thread? = null
    private var shizukuListener: IShizuWallForegroundListener? = null

    suspend fun start(context: Context, onTask: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        stop()
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val working = WorkingMode.fromName(prefs.getString(MainActivity.KEY_WORKING_MODE, null))
        val started = try {
            when (working) {
                WorkingMode.SHIZUKU -> startShizuku(onTask)
                WorkingMode.LADB -> startDaemon(context, onTask)
                WorkingMode.ROOT -> RootForegroundWatchSession.start(
                    PerUidFirewall.extractHelperDex(context).absolutePath
                ) { line -> dispatch(line, onTask) }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Foreground watch failed to start", t)
            false
        }
        mode = working.takeIf { started }
        isActive = started
        started
    }

    fun stop() {
        isActive = false
        when (mode) {
            WorkingMode.SHIZUKU -> runCatching { ShizukuUserServiceManager.peek()?.stopForegroundWatch() }
            WorkingMode.LADB -> {
                runCatching { socket?.close() }
                socket = null
                reader = null
            }
            WorkingMode.ROOT -> RootForegroundWatchSession.stop()
            null -> Unit
        }
        shizukuListener = null
        mode = null
    }

    private suspend fun startShizuku(onTask: (String) -> Unit): Boolean {
        if (!Shizuku.pingBinder()) return false
        val service = ShizukuUserServiceManager.obtain() ?: return false
        val listener = object : IShizuWallForegroundListener.Stub() {
            override fun onForegroundTask(task: String?) {
                dispatch(task, onTask)
            }
        }
        shizukuListener = listener
        service.startForegroundWatch(listener)
        return true
    }

    private fun startDaemon(context: Context, onTask: (String) -> Unit): Boolean {
        val opened = PersistentDaemonManager(context).openStreamingCommand(WATCH_COMMAND)
        socket = opened
        reader = Thread {
            try {
                opened.getInputStream().bufferedReader().forEachLine { dispatch(it, onTask) }
            } catch (_: Exception) {
            } finally {
                if (socket === opened) {
                    isActive = false
                    runCatching { opened.close() }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
        return true
    }

    private fun dispatch(line: String?, onTask: (String) -> Unit) {
        val value = line?.trim().orEmpty()
        if (value.isEmpty() || value == KEEPALIVE) return
        if (value.startsWith("Error", ignoreCase = true)) {
            Log.w(TAG, "Foreground watch helper stopped: $value")
            isActive = false
            return
        }
        ForegroundTaskProbe.parse(value)?.let(onTask)
    }
}
