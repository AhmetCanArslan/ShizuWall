package com.arslan.shizuwall.ladb

import android.content.Context
import android.content.SharedPreferences
import com.arslan.shizuwall.shell.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import java.io.PrintWriter
import java.io.StringWriter
import android.os.Build
import java.security.PrivateKey
import java.security.Security
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.SSLException

class LadbManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: LadbManager? = null

        fun getInstance(context: Context): LadbManager {
            return instance ?: synchronized(this) {
                instance ?: LadbManager(context.applicationContext).also { instance = it }
            }
        }

        const val PREFS_NAME = "ladb_prefs"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_PAIRING_PORT = "pairing_port"
        const val KEY_IS_PAIRED = "is_paired"
        const val KEY_LAST_ERROR_LOG = "last_error_log"
    }

    enum class State {
        UNCONFIGURED,
        PAIRED,
        CONNECTED,
        ERROR,
        DISCONNECTED
    }

    fun isPaired(): Boolean {
        return getPrefs().getBoolean(KEY_IS_PAIRED, false)
    }

    private val _state = AtomicReference(State.UNCONFIGURED)
    val state: State
        get() = _state.get()

    private val lastErrorLogRef = AtomicReference<String?>(null)
    fun getLastErrorLog(): String? {
        lastErrorLogRef.get()?.let { return it }
        return try {
            getPrefs().getString(KEY_LAST_ERROR_LOG, null)
        } catch (_: Exception) {
            null
        }
    }

    private fun recordError(operation: String, host: String?, port: Int?, e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))

        val providers = try {
            Security.getProviders().joinToString { it.name }
        } catch (_: Exception) {
            "(unavailable)"
        }

        val log = buildString {
            appendLine("ShizuWall LADB error")
            appendLine("operation=$operation")
            appendLine("host=${host ?: "(null)"}")
            appendLine("port=${port?.toString() ?: "(null)"}")
            appendLine("state=${state}")
            appendLine("sdk=${Build.VERSION.SDK_INT}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("providers=$providers")
            appendLine()
            appendLine("exception=${e::class.java.name}: ${e.message}")
            appendLine(sw.toString())
        }

        lastErrorLogRef.set(log)
        try {
            getPrefs().edit().putString(KEY_LAST_ERROR_LOG, log).apply()
        } catch (_: Exception) {
        }

        try {
            LadbLogStore.append(context, "LADB error: $operation failed (${e::class.java.simpleName}: ${e.message})")
        } catch (_: Exception) {
        }
    }

    private val connectionRef = AtomicReference<AdbConnection?>(null)
    private val keyMaterialRef = AtomicReference<Pair<PrivateKey, X509Certificate>?>(null)
    private val connectionMutex = Mutex()

    @Volatile
    private var cachedPrefs: SharedPreferences? = null
    private val prefsLock = Any()

    private fun getPrefs(): SharedPreferences {
        cachedPrefs?.let { return it }

        synchronized(prefsLock) {
            cachedPrefs?.let { return it }
            
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            cachedPrefs = prefs
            return prefs
        }
    }

    fun getSavedHost(): String? {
        return try {
            getPrefs().getString(KEY_HOST, null)
        } catch (_: Exception) {
            null
        }
    }

    fun getSavedConnectPort(): Int {
        return try {
            getPrefs().getInt(KEY_PORT, -1)
        } catch (_: Exception) {
            -1
        }
    }

    fun getSavedPairingPort(): Int {
        return try {
            getPrefs().getInt(KEY_PAIRING_PORT, -1)
        } catch (_: Exception) {
            -1
        }
    }

    private fun keyStoreDir(): File {
        return File(context.filesDir, "ladb").apply { mkdirs() }
    }

    private fun getOrCreateKeyMaterial(): Pair<PrivateKey, X509Certificate> {
        keyMaterialRef.get()?.let { return it }

        val dir = keyStoreDir()
        val privFile = File(dir, "adb_private_key_pkcs8.der")
        val certFile = File(dir, "adb_cert_x509.der")

        val loaded = try {
            if (privFile.exists() && certFile.exists()) {
                val privateKey = loadPrivateKeyPkcs8(privFile.readBytes())
                val certificate = loadCertificate(certFile.readBytes())
                privateKey to certificate
            } else null
        } catch (_: Exception) {
            null
        }

        if (loaded != null) {
            keyMaterialRef.set(loaded)
            return loaded
        }

        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val kp = generator.generateKeyPair()
        val cert = selfSignedCertificate(kp.private, kp.public.encoded)
        val created = kp.private to cert

        try {
            privFile.writeBytes(kp.private.encoded)
            certFile.writeBytes(cert.encoded)
        } catch (_: Exception) {
        }

        keyMaterialRef.set(created)
        return created
    }

    private fun loadPrivateKeyPkcs8(encoded: ByteArray): PrivateKey {
        val spec = PKCS8EncodedKeySpec(encoded)
        val kf = KeyFactory.getInstance("RSA")
        return kf.generatePrivate(spec)
    }

    private fun loadCertificate(encoded: ByteArray): X509Certificate {
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(ByteArrayInputStream(encoded)) as X509Certificate
    }

    suspend fun pair(host: String, port: Int, pairingCode: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            if (host.isBlank() || port <= 0 || port > 65535) {
                val e = IllegalArgumentException("Invalid host/port")
                recordError("pair", host, port, e)
                _state.set(State.UNCONFIGURED)
                return@withContext false
            }

            val code = pairingCode?.trim().orEmpty()
            if (code.isEmpty()) {
                val e = IllegalArgumentException("Pairing code is required")
                recordError("pair", host, port, e)
                _state.set(State.ERROR)
                return@withContext false
            }

            getPrefs().edit()
                .putString(KEY_HOST, host)
                .putInt(KEY_PAIRING_PORT, port)
                .apply()

            val (privateKey, certificate) = getOrCreateKeyMaterial()
            adbPair(host, port, code.toByteArray(Charsets.UTF_8), privateKey, certificate)

            getPrefs().edit().putBoolean(KEY_IS_PAIRED, true).apply()
            _state.set(State.PAIRED)
            return@withContext true
        } catch (e: Exception) {
            recordError("pair", host, port, e)
            _state.set(State.ERROR)
            return@withContext false
        }
    }

    suspend fun savePairingConfig(host: String, pairingPort: Int): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (host.isBlank() || pairingPort <= 0 || pairingPort > 65535) {
                val e = IllegalArgumentException("Invalid pairing host/port")
                recordError("save_pairing_config", host, pairingPort, e)
                false
            } else {
                getPrefs().edit()
                    .putString(KEY_HOST, host)
                    .putInt(KEY_PAIRING_PORT, pairingPort)
                    .apply()
                true
            }
        } catch (e: Exception) {
            recordError("save_pairing_config", host, pairingPort, e)
            false
        }
    }

    suspend fun savePairingPortUsingSavedHost(pairingPort: Int): Boolean = withContext(Dispatchers.IO) {
        val host = getPrefs().getString(KEY_HOST, null)
        return@withContext try {
            if (host.isNullOrBlank() || pairingPort <= 0 || pairingPort > 65535) {
                val e = IllegalArgumentException("Invalid saved host or pairing port")
                recordError("save_pairing_port_using_saved_host", host, pairingPort, e)
                false
            } else {
                getPrefs().edit()
                    .putInt(KEY_PAIRING_PORT, pairingPort)
                    .apply()
                true
            }
        } catch (e: Exception) {
            recordError("save_pairing_port_using_saved_host", host, pairingPort, e)
            false
        }
    }

    suspend fun pairUsingSavedConfig(pairingCode: String): Boolean = withContext(Dispatchers.IO) {
        val host = getPrefs().getString(KEY_HOST, null)
        val pairingPort = getPrefs().getInt(KEY_PAIRING_PORT, -1)
        if (host.isNullOrBlank() || pairingPort <= 0) {
            val e = IllegalStateException("Pairing host/port is not configured")
            recordError("pair_using_saved", host, pairingPort, e)
            _state.set(State.UNCONFIGURED)
            return@withContext false
        }
        return@withContext pair(host, pairingPort, pairingCode)
    }

    suspend fun saveConnectConfig(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (host.isBlank() || port <= 0 || port > 65535) {
                val e = IllegalArgumentException("Invalid host/port")
                recordError("save_connect_config", host, port, e)
                false
            } else {
                getPrefs().edit()
                    .putString(KEY_HOST, host)
                    .putInt(KEY_PORT, port)
                    .apply()
                true
            }
        } catch (e: Exception) {
            recordError("save_connect_config", host, port, e)
            false
        }
    }

    suspend fun clearPairingPort(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            getPrefs().edit().remove(KEY_PAIRING_PORT).remove(KEY_IS_PAIRED).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clearConnectPort(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            getPrefs().edit().remove(KEY_PORT).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun connectLocked(host: String? = null, port: Int? = null): Boolean {
        if (state == State.CONNECTED && connectionRef.get() != null) {
            return true
        }

        val targetHost = host ?: getPrefs().getString(KEY_HOST, null)
        val targetPort = port ?: getPrefs().getInt(KEY_PORT, -1)

        if (targetHost == null || targetHost.isBlank()) {
            val e = IllegalStateException("Host is not configured")
            recordError("connect", targetHost, targetPort, e)
            _state.set(State.UNCONFIGURED)
            return false
        }

        if (targetPort <= 0) {
            val e = IllegalStateException("Port is not configured")
            recordError("connect", targetHost, targetPort, e)
            _state.set(State.UNCONFIGURED)
            return false
        }

        try {
            connectionRef.getAndSet(null)?.close()
        } catch (_: Exception) {
        }

        val startMs = System.currentTimeMillis()

        return try {
            val (privateKey, certificate) = getOrCreateKeyMaterial()
            val c = AdbConnection(targetHost, targetPort, privateKey, certificate)
            try {
                c.connect(60_000)
            } catch (e: Exception) {
                runCatching { c.close() }
                throw e
            }
            connectionRef.set(c)
            _state.set(State.CONNECTED)
            true
        } catch (e: SSLException) {
            recordError("connect", targetHost, targetPort, e)
            logHandshakeDiagnostic("pairing-required", startMs)
            _state.set(State.PAIRED)
            false
        } catch (e: SocketTimeoutException) {
            recordError("connect", targetHost, targetPort, e)
            logHandshakeDiagnostic("connect-timeout", startMs)
            _state.set(State.ERROR)
            false
        } catch (e: Exception) {
            recordError("connect", targetHost, targetPort, e)
            logHandshakeDiagnostic("connect-exception", startMs)
            _state.set(State.ERROR)
            false
        }
    }

    private fun logHandshakeDiagnostic(outcome: String, startMs: Long) {
        try {
            LadbLogStore.append(context, "LADB handshake: $outcome after ${System.currentTimeMillis() - startMs}ms")
        } catch (_: Exception) {
        }
    }

    suspend fun connect(host: String? = null, port: Int? = null): Boolean = withContext(Dispatchers.IO) {
        connectionMutex.lock()
        try {
            connectLocked(host, port)
        } finally {
            connectionMutex.unlock()
        }
    }

    suspend fun clearAllConfig(): Boolean = withContext(Dispatchers.IO) {
        connectionMutex.lock()
        try {
            try {
                connectionRef.getAndSet(null)?.close()
            } catch (_: Exception) {
            }

            lastErrorLogRef.set(null)

            getPrefs().edit()
                .remove(KEY_HOST)
                .remove(KEY_PORT)
                .remove(KEY_PAIRING_PORT)
                .remove(KEY_IS_PAIRED)
                .remove(KEY_LAST_ERROR_LOG)
                .apply()

            _state.set(State.UNCONFIGURED)
            true
        } catch (e: Exception) {
            recordError("clear_all_config", null, null, e)
            _state.set(State.ERROR)
            false
        } finally {
            connectionMutex.unlock()
        }
    }

    suspend fun execShell(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        for (attempt in 0..1) {
            val conn = connectionMutex.withLock {
                if (connectionRef.get() == null || state != State.CONNECTED) {
                    if (!connectLocked()) return@withContext ShellResult(-1, "", "Not connected")
                }
                connectionRef.get()
            } ?: return@withContext ShellResult(-1, "", "Failed to establish connection")

            try {
                return@withContext ShellResult(0, conn.shell(cmd, 15_000), "")
            } catch (e: SocketTimeoutException) {
                dropConnection(conn)
                return@withContext ShellResult(-1, "", "timeout")
            } catch (e: Exception) {
                dropConnection(conn)
                if (attempt == 1) return@withContext ShellResult(-1, "", e.message ?: "Error executing command")
            }
        }
        ShellResult(-1, "", "Failed to execute command")
    }

    private suspend fun dropConnection(conn: AdbConnection) {
        connectionMutex.withLock {
            if (connectionRef.compareAndSet(conn, null)) {
                _state.set(State.DISCONNECTED)
                runCatching { conn.close() }
            }
        }
    }

    fun isConnected(): Boolean {
        return state == State.CONNECTED
    }
}
