package com.arslan.shizuwall.daemon

import android.content.Context
import android.util.Log
import com.arslan.shizuwall.ladb.LadbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

class PersistentDaemonManager(private val context: Context) {

    companion object {
        private const val DAEMON_PORT = 18522
        private const val TAG = "PersistentDaemonManager"
        private const val PREFS_NAME = "daemon_prefs"
        private const val KEY_TOKEN = "daemon_token"
        private const val SOCKET_TIMEOUT_MS = 5000
        private const val CONNECT_TIMEOUT_MS = 2000
        
        private val connectionMutex = Mutex()
    }
    
    fun token(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var token = prefs.getString(KEY_TOKEN, null)
        if (token == null) {
            token = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_TOKEN, token).apply()
        }
        return token
    }
    
    fun regenerateToken(): String {
        val token = UUID.randomUUID().toString()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
        return token
    }

    suspend fun installDaemon(onProgress: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress("Checking assets...")
            val assets = context.assets.list("") ?: emptyArray()
            Log.d(TAG, "Available assets: ${assets.joinToString()}")
            
            onProgress("Copying assets...")
            val scriptPath = copyAssetToCache("daemon.sh")
            val dexPath = copyAssetToCache("daemon.bin", "daemon.dex")

            onProgress("Connecting to LADB...")
            val ladb = LadbManager.getInstance(context)
            if (!ladb.connect()) {
                onProgress("LADB not connected. Please pair first.")
                return@withContext false
            }

            val tokenFile = File(context.externalCacheDir ?: context.cacheDir, "token")
            FileOutputStream(tokenFile).use { it.write(regenerateToken().toByteArray()) }

            onProgress("Stopping existing daemon...")
            ladb.execShell("pkill -f 'com.arslan.shizuwall.daemon.[S]ystemDaemon' 2>/dev/null || true")
            delay(500)

            onProgress("Moving files to /data/local/tmp/...")
            val steps = listOf(
                "cat $dexPath > /data/local/tmp/daemon.dex",
                "cat $scriptPath > /data/local/tmp/daemon.sh",
                "cat ${tokenFile.absolutePath} > /data/local/tmp/shizuwall.token",
                "chmod 700 /data/local/tmp/daemon.sh /data/local/tmp/daemon.dex",
                "chmod 600 /data/local/tmp/shizuwall.token"
            )
            val failed = steps.firstNotNullOfOrNull { cmd -> ladb.execShell(cmd).takeIf { it.exitCode != 0 }?.let { "$cmd: ${it.stderr}" } }
            tokenFile.delete()
            if (failed != null) {
                onProgress("Failed: $failed")
                return@withContext false
            }

            onProgress("Starting daemon...")
            val result = ladb.execShell("/system/bin/sh /data/local/tmp/daemon.sh 2>&1")
            val scriptOutput = result.stdout
            Log.d(TAG, "Daemon script output:\n$scriptOutput")
            
            if (scriptOutput.isNotEmpty()) {
                onProgress("Script Output:\n$scriptOutput")
            }

            onProgress("Waiting for daemon to initialize...")
            delay(2000)

            val running = isDaemonRunning()
            if (running) {
                onProgress("Daemon is running!")
                val pingResult = executeCommand("ping")
                if (pingResult.trim() == "pong") {
                    onProgress("Daemon verified and responding!")
                } else {
                    onProgress("Warning: Daemon running but ping failed: $pingResult")
                }
            } else {
                onProgress("Daemon failed to start.")
                val logs = ladb.execShell("tail -20 /data/local/tmp/daemon.log 2>/dev/null").stdout
                if (logs.isNotEmpty()) {
                    onProgress("Daemon logs:\n$logs")
                }
            }
            return@withContext running

        } catch (e: Exception) {
            Log.e(TAG, "Installation failed", e)
            onProgress("Error: ${e.message}")
            return@withContext false
        }
    }

    fun isDaemonRunning(): Boolean {
        return try {
            val future = java.util.concurrent.Executors.newSingleThreadExecutor().submit(java.util.concurrent.Callable {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress("127.0.0.1", DAEMON_PORT), 500)
                    socket.close()
                    true
                } catch (e: Exception) {
                    false
                }
            })
            future.get(1000, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            false
        }
    }

    fun openStreamingCommand(command: String): Socket {
        val socket = Socket()
        socket.connect(InetSocketAddress("127.0.0.1", DAEMON_PORT), CONNECT_TIMEOUT_MS)
        socket.soTimeout = 0
        val output = socket.getOutputStream().bufferedWriter()
        output.write("${token()}\n")
        output.write("$command\n")
        output.flush()
        return socket
    }

    suspend fun executeCommand(command: String): String = connectionMutex.withLock {
        withContext(Dispatchers.IO) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress("127.0.0.1", DAEMON_PORT), CONNECT_TIMEOUT_MS)
                socket.soTimeout = SOCKET_TIMEOUT_MS
                
                val output = socket.getOutputStream().bufferedWriter()
                val input = socket.getInputStream().bufferedReader()
                
                val token = token()
                output.write("$token\n")
                output.flush()

                output.write("$command\n")
                output.flush()
                
                socket.shutdownOutput()
                
                val result = input.readText()
                Log.d(TAG, "Received from daemon: $result")
                return@withContext result
                
            } catch (e: java.net.SocketTimeoutException) {
                Log.w(TAG, "Socket timeout for command: $command")
                return@withContext "Error: Daemon not responding - timeout"
            } catch (e: java.net.ConnectException) {
                Log.w(TAG, "Connection refused - daemon not running")
                return@withContext "Error: Daemon not responding - connection refused"
            } catch (e: Exception) {
                Log.e(TAG, "Error executing command", e)
                return@withContext "Error: Daemon not responding - ${e.message}"
            } finally {
                try {
                    socket.close()
                } catch (ignored: Exception) {}
            }
        }
    }
    
    suspend fun readRecentDaemonLogs(maxLines: Int = 20): String? = withContext(Dispatchers.IO) {
        val ladb = LadbManager.getInstance(context)
        if (!ladb.isConnected()) {
            return@withContext null
        }

        val result = ladb.execShell("tail -$maxLines /data/local/tmp/daemon.log 2>/dev/null")
        val logs = result.stdout.ifBlank { result.stderr }.trim()
        return@withContext logs.ifBlank { null }
    }

    private fun copyAssetToCache(assetName: String, targetName: String = assetName): String {
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        val outFile = File(cacheDir, targetName)
        context.assets.open(assetName).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        outFile.setReadable(true, false)
        outFile.setExecutable(true, false)
        return outFile.absolutePath
    }
}
