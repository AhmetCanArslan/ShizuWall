package com.arslan.shizuwall.daemon

import android.content.Context
import android.util.Log
import com.arslan.shizuwall.ladb.LadbManager
import com.arslan.shizuwall.shell.ShellResult
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
import java.util.concurrent.atomic.AtomicReference

class PersistentDaemonManager(private val context: Context) {

    companion object {
        private const val DAEMON_PORT = 18522
        private const val TAG = "PersistentDaemonManager"
        private const val PREFS_NAME = "daemon_prefs"
        private const val KEY_TOKEN = "daemon_token"
        private const val SOCKET_TIMEOUT_MS = 5000
        private const val CONNECT_TIMEOUT_MS = 2000
        
        // Shared socket pool for connection reuse
        private val connectionMutex = Mutex()
    }
    
    private val daemonPort = DAEMON_PORT

    private fun getOrGenerateToken(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var token = prefs.getString(KEY_TOKEN, null)
        if (token == null) {
            token = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_TOKEN, token).apply()
        }
        return token
    }
    
    /**
     * Force regenerate the authentication token.
     * Call this when reinstalling the daemon.
     */
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
            if (!ladb.isConnected()) {
                onProgress("LADB not connected. Please pair first.")
                return@withContext false
            }

            // Regenerate token on reinstall to ensure sync
            val token = regenerateToken()
            val tokenFile = File(context.externalCacheDir ?: context.cacheDir, "token")
            FileOutputStream(tokenFile).use { it.write(token.toByteArray()) }
            val tokenPath = tokenFile.absolutePath
            
            onProgress("Stopping existing daemon...")
            // Kill any existing daemon first
            ladb.execShell("pkill -f 'com.arslan.shizuwall.daemon.SystemDaemon' 2>/dev/null || true")
            delay(500)
            
            onProgress("Moving files to /data/local/tmp/...")
            ladb.execShell("cat $dexPath > /data/local/tmp/daemon.dex")
            ladb.execShell("cat $scriptPath > /data/local/tmp/daemon.sh")
            ladb.execShell("cat $tokenPath > /data/local/tmp/shizuwall.token")
            
            ladb.execShell("chmod 700 /data/local/tmp/daemon.sh")
            ladb.execShell("chmod 700 /data/local/tmp/daemon.dex")
            ladb.execShell("chmod 600 /data/local/tmp/shizuwall.token")
            
            // Cleanup temporary token file to reduce exposure
            if (tokenFile.exists()) {
                tokenFile.delete()
            }

            // Verify files exist
            val checkFiles = ladb.execShell("ls -l /data/local/tmp/daemon.*").stdout
            Log.d(TAG, "Files in /data/local/tmp/:\n$checkFiles")
            onProgress("Files verified: ${checkFiles.contains("daemon.sh")}")

            onProgress("Starting daemon...")
            // Capture all output from the script
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
                // Verify connection works with a ping
                val pingResult = executeCommand("ping")
                if (pingResult.trim() == "pong") {
                    onProgress("Daemon verified and responding!")
                } else {
                    onProgress("Warning: Daemon running but ping failed: $pingResult")
                }
            } else {
                onProgress("Daemon failed to start.")
                // Try to get logs for debugging
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
                    socket.connect(InetSocketAddress("127.0.0.1", daemonPort), 500)
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

    suspend fun executeCommand(command: String): String = connectionMutex.withLock {
        withContext(Dispatchers.IO) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress("127.0.0.1", daemonPort), CONNECT_TIMEOUT_MS)
                socket.soTimeout = SOCKET_TIMEOUT_MS
                
                val output = socket.getOutputStream().bufferedWriter()
                val input = socket.getInputStream().bufferedReader()
                
                // Send token
                val token = getOrGenerateToken()
                output.write("$token\n")
                output.flush()

                // Send command
                output.write("$command\n")
                output.flush()
                
                // Shutdown output to signal we're done sending
                socket.shutdownOutput()
                
                // Read result
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
    
    /**
     * Check if daemon is healthy and responding to commands.
     */
    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        if (!isDaemonRunning()) return@withContext false
        try {
            val result = executeCommand("ping")
            return@withContext result.trim() == "pong"
        } catch (e: Exception) {
            Log.w(TAG, "Health check failed", e)
            return@withContext false
        }
    }

    private fun copyAssetToCache(assetName: String, targetName: String = assetName): String {
        // Use externalCacheDir so the 'shell' user can access it via /sdcard/Android/data/...
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
