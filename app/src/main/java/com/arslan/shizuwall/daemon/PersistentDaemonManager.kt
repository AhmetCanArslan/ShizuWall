package com.arslan.shizuwall.daemon

import android.content.Context
import android.util.Log
import com.arslan.shizuwall.ladb.LadbManager
import com.arslan.shizuwall.shell.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket

class PersistentDaemonManager(private val context: Context) {

    private val daemonPort = 18521
    private val TAG = "PersistentDaemonManager"

    suspend fun installDaemon(onProgress: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress("Checking assets...")
            val assets = context.assets.list("") ?: emptyArray()
            Log.d(TAG, "Available assets: ${assets.joinToString()}")
            
            onProgress("Copying assets...")
            val scriptPath = copyAssetToCache("daemon.sh")
            val dexPath = copyAssetToCache("daemon.bin", "daemon.dex")
            // Note: adb_daemon binary is optional if we use the Java implementation
            // but we'll copy it if it exists in assets.
            val binaryPath = try { copyAssetToCache("adb_daemon") } catch (e: Exception) { null }

            onProgress("Connecting to LADB...")
            val ladb = LadbManager.getInstance(context)
            if (!ladb.isConnected()) {
                onProgress("LADB not connected. Please pair first.")
                return@withContext false
            }

            onProgress("Moving files to /data/local/tmp/...")
            ladb.execShell("cat $dexPath > /data/local/tmp/daemon.dex")
            ladb.execShell("cat $scriptPath > /data/local/tmp/daemon.sh")
            binaryPath?.let { path -> ladb.execShell("cat $path > /data/local/tmp/adb_daemon") }
            
            ladb.execShell("chmod 777 /data/local/tmp/daemon.sh")
            ladb.execShell("chmod 777 /data/local/tmp/daemon.dex")
            
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
            } else {
                onProgress("Daemon failed to start.")
                if (scriptOutput.isEmpty()) {
                    onProgress("No output from script. Check if LADB is connected.")
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
        // Use a short timeout and run on a background thread to avoid NetworkOnMainThreadException
        // although for a simple localhost connection it's usually fast.
        return try {
            val future = java.util.concurrent.Executors.newSingleThreadExecutor().submit(java.util.concurrent.Callable {
                try {
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress("127.0.0.1", daemonPort), 500)
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

    suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress("127.0.0.1", daemonPort), 2000)
            
            val output = socket.getOutputStream()
            val input = socket.getInputStream()
            
            // Send command
            output.write("$command\n".toByteArray())
            output.flush()
            
            // Read result
            val result = input.readBytes().decodeToString()
            Log.d(TAG, "Received from daemon: $result")
            return@withContext result
            
        } catch (e: Exception) {
            return@withContext "Error: Daemon not responding - ${e.message}"
        } finally {
            try {
                socket.close()
            } catch (ignored: Exception) {}
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
