package com.arslan.shizuwall.ladb

import android.content.Context
import com.arslan.shizuwall.shell.ShellResult

class LadbManager private constructor(private val context: Context) {

    enum class State {
        UNCONFIGURED,
        PAIRED,
        CONNECTED,
        ERROR,
        DISCONNECTED
    }

    companion object {
        const val PREFS_NAME = "ladb_prefs"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_PAIRING_PORT = "pairing_port"
        const val KEY_IS_PAIRED = "is_paired"
        const val KEY_LAST_ERROR_LOG = "last_error_log"

        @Volatile
        private var instance: LadbManager? = null

        fun getInstance(context: Context): LadbManager {
            return instance ?: synchronized(this) {
                instance ?: LadbManager(context.applicationContext).also { instance = it }
            }
        }
    }

    val state: State = State.UNCONFIGURED

    fun isPaired(): Boolean = false
    fun getLastErrorLog(): String? = null
    fun getSavedHost(): String? = null
    fun getSavedConnectPort(): Int = -1
    fun getSavedPairingPort(): Int = -1

    suspend fun pair(host: String, port: Int, pairingCode: String?): Boolean = false
    suspend fun savePairingConfig(host: String, pairingPort: Int): Boolean = false
    suspend fun savePairingPortUsingSavedHost(pairingPort: Int): Boolean = false
    suspend fun pairUsingSavedConfig(pairingCode: String): Boolean = false
    suspend fun saveConnectConfig(host: String, port: Int): Boolean = false
    suspend fun saveHost(host: String): Boolean = false
    suspend fun clearPairingPort(): Boolean = false
    suspend fun clearConnectPort(): Boolean = false
    suspend fun connect(host: String? = null, port: Int? = null): Boolean = false
    suspend fun clearAllConfig(): Boolean = false
    suspend fun execShell(cmd: String): ShellResult = ShellResult(-1, "", "Not supported in F-Droid version")
    fun isConnected(): Boolean = false
}
