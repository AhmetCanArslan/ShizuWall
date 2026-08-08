package com.arslan.shizuwall.daemon

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AdbDaemonBootstrap(private val context: Context) {

    companion object {
        const val SCRIPT_NAME = "shizuwall_adb_setup.sh"
        private const val DEX_NAME = "daemon.dex"
    }

    suspend fun stage(): String = withContext(Dispatchers.IO) {
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        val dexFile = File(cacheDir, DEX_NAME)
        context.assets.open("daemon.bin").use { input ->
            dexFile.outputStream().use { output -> input.copyTo(output) }
        }
        dexFile.setReadable(true, false)

        val token = PersistentDaemonManager(context).currentToken()
        val scriptFile = File(cacheDir, SCRIPT_NAME)
        scriptFile.writeText(buildScript(dexFile.absolutePath, token))
        scriptFile.setReadable(true, false)
        scriptFile.setExecutable(true, false)

        buildCommand(scriptFile.absolutePath)
    }

    fun buildCommand(scriptPath: String): String = "adb shell sh $scriptPath"

    fun cachedCommand(): String {
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        return buildCommand(File(cacheDir, SCRIPT_NAME).absolutePath)
    }

    private fun buildScript(dexSource: String, token: String): String = """
        #!/system/bin/sh
        DEX_SRC="$dexSource"
        DEX="/data/local/tmp/daemon.dex"
        TOKEN_FILE="/data/local/tmp/shizuwall.token"
        PID_FILE="/data/local/tmp/daemon.pid"
        LOG_FILE="/data/local/tmp/daemon.log"

        if [ "${'$'}(id -u)" = "0" ]; then
            echo "Run this as the shell user (plain 'adb shell'), not as root."
            exit 1
        fi

        if [ ! -s "${'$'}DEX_SRC" ]; then
            echo "ERROR: ${'$'}DEX_SRC not found. Open ShizuWall LADB setup once, then retry."
            exit 1
        fi

        cat "${'$'}DEX_SRC" > "${'$'}DEX" || exit 1
        printf '%s' '$token' > "${'$'}TOKEN_FILE" || exit 1
        chmod 600 "${'$'}TOKEN_FILE"
        chmod 700 "${'$'}DEX"

        if [ -f "${'$'}PID_FILE" ]; then
            OLD_PID=${'$'}(cat "${'$'}PID_FILE")
            if [ -d "/proc/${'$'}OLD_PID" ]; then
                kill -TERM "${'$'}OLD_PID" 2>/dev/null
                sleep 1
                [ -d "/proc/${'$'}OLD_PID" ] && kill -9 "${'$'}OLD_PID" 2>/dev/null
            fi
            rm -f "${'$'}PID_FILE"
        fi
        pkill -f 'com.arslan.shizuwall.daemon.SystemDaemon' 2>/dev/null
        sleep 1

        : > "${'$'}LOG_FILE"
        nohup env CLASSPATH="${'$'}DEX" /system/bin/app_process /system/bin \
            com.arslan.shizuwall.daemon.SystemDaemon >> "${'$'}LOG_FILE" 2>&1 &
        PID=${'$'}!
        echo "${'$'}PID" > "${'$'}PID_FILE"
        sleep 2

        if [ -d "/proc/${'$'}PID" ]; then
            echo "ShizuWall daemon started (pid ${'$'}PID). You can close this shell."
            exit 0
        else
            echo "ShizuWall daemon failed to start:"
            cat "${'$'}LOG_FILE"
            exit 1
        fi
    """.trimIndent()
}
