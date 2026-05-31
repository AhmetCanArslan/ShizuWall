package com.arslan.shizuwall.ladb

import android.content.Context
import android.content.SharedPreferences
import com.arslan.shizuwall.shell.ShellResult
import io.github.muntashirakon.adb.AdbConnection
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.adb.PairingConnectionCtx
import io.github.muntashirakon.adb.AdbPairingRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import org.conscrypt.Conscrypt
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.PrintWriter
import java.io.StringWriter
import android.os.Build
import java.security.PrivateKey
import java.security.Security
import java.security.cert.Certificate
import java.math.BigInteger
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

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
            // ignore
        }
    }

    private val connectionRef = AtomicReference<AdbConnection?>(null)
    private val keyMaterialRef = AtomicReference<Pair<PrivateKey, Certificate>?>(null)
    private val connectionMutex = Mutex()

    init {
        // Prefer Conscrypt for modern TLS on older devices.
        if (Security.getProvider("Conscrypt") == null) {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }

        // Provide RSA/ECB/NoPadding, cert utilities, etc.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    // Cache for SharedPreferences to avoid repeated initialization.
    @Volatile
    private var cachedPrefs: SharedPreferences? = null
    private val prefsLock = Any()

    private fun getPrefs(): SharedPreferences {
        cachedPrefs?.let { return it }

        synchronized(prefsLock) {
            cachedPrefs?.let { return it }
            
            // Use regular SharedPreferences - LADB config (host/port) is not highly sensitive
            // and Android already protects app private data from other apps
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

    private fun getOrCreateKeyMaterial(): Pair<PrivateKey, Certificate> {
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
        val cert = createSelfSignedCertificate(kp.private, kp.public)
        val created = kp.private to cert

        // Best-effort persistence; failure shouldn't crash the app.
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

    private fun loadCertificate(encoded: ByteArray): Certificate {
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(ByteArrayInputStream(encoded))
    }

    private fun createSelfSignedCertificate(privateKey: PrivateKey, publicKey: PublicKey): X509Certificate {
        val now = Date()
        val until = Date(now.time + TimeUnit.DAYS.toMillis(3650))
        val subject = X500Name("CN=ShizuWall")
        val serial = BigInteger.valueOf(now.time.coerceAtLeast(1L))

        val builder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            now,
            until,
            subject,
            publicKey
        )

        // Do not force provider "BC" here.
        // On Android, the built-in "BC" provider is a stripped-down fork and may not expose
        // SHA256withRSA; letting the platform pick a provider is the most compatible.
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(privateKey)

        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
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

            // Save pairing configuration (connect host/port is configured separately).
            getPrefs().edit()
                .putString(KEY_HOST, host)
                .putInt(KEY_PAIRING_PORT, port)
                .apply()

            // Perform Wireless Debugging pairing so the device trusts our key/cert.
            val (privateKey, certificate) = getOrCreateKeyMaterial()
            val ctx = PairingConnectionCtx(
                host,
                port,
                code.toByteArray(Charsets.UTF_8),
                privateKey,
                certificate,
                "ShizuWall"
            )
            ctx.use { it.start() }

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

    suspend fun saveHost(host: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (host.isBlank()) {
                val e = IllegalArgumentException("Host is blank")
                recordError("save_host", host, null, e)
                false
            } else {
                getPrefs().edit()
                    .putString(KEY_HOST, host)
                    .apply()
                true
            }
        } catch (e: Exception) {
            recordError("save_host", host, null, e)
            false
        }
    }

    suspend fun clearPairingPort(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            getPrefs().edit().remove(KEY_PAIRING_PORT).apply()
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
        // Check if already connected to avoid redundant connection attempts
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

        // Close any existing connection before creating a new one
        try {
            connectionRef.getAndSet(null)?.close()
        } catch (_: Exception) {
            // ignore
        }

        return try {
            val (privateKey, certificate) = getOrCreateKeyMaterial()
            val conn = AdbConnection.Builder()
                .setHost(targetHost)
                .setPort(targetPort)
                .setPrivateKey(privateKey)
                .setCertificate(certificate)
                .build()

            withTimeout(10_000L) { // 10 seconds timeout for LADB connection
                conn.connect()
            }
            connectionRef.set(conn)
            _state.set(State.CONNECTED)
            true
        } catch (e: io.github.muntashirakon.adb.AdbPairingRequiredException) {
            recordError("connect", targetHost, targetPort, e)
            _state.set(State.PAIRED)
            false
        } catch (e: Exception) {
            recordError("connect", targetHost, targetPort, e)
            _state.set(State.ERROR)
            false
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
                // ignore
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
        val maxRetries = 1
        val timeoutMs = 15_000L // 15 seconds

        for (attempt in 0..maxRetries) {
            connectionMutex.lock()
            val conn = try {
                var currentConn = connectionRef.get()
                if (currentConn == null || state != State.CONNECTED) {
                    val ok = connectLocked()
                    if (!ok) {
                        return@withContext ShellResult(-1, "", "Not connected")
                    }
                    currentConn = connectionRef.get()
                }
                currentConn
            } finally {
                connectionMutex.unlock()
            }

            if (conn == null) return@withContext ShellResult(-1, "", "Failed to establish connection")

            try {
                val stream: AdbStream = conn.open("shell:$cmd")
                val input = stream.openInputStream()

                val buf = ByteArray(8 * 1024)
                val out = StringBuilder()

                try {
                    withTimeout(timeoutMs) {
                        while (true) {
                            val n = try {
                                input.read(buf)
                            } catch (e: Exception) {
                                val msg = e.message?.lowercase().orEmpty()
                                if (msg.contains("stream closed") || msg.contains("socket closed")) {
                                    if (stream.isClosed()) break
                                }
                                throw e
                            }
                            if (n <= 0) break
                            out.append(String(buf, 0, n, Charsets.UTF_8))
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.TimeoutCancellationException) {
                        try {
                            stream.close()
                        } catch (_: Exception) {
                        }
                        return@withContext ShellResult(-1, out.toString(), "timeout")
                    }
                    throw e
                } finally {
                    try {
                        stream.close()
                    } catch (_: Exception) {
                    }
                }

                return@withContext ShellResult(0, out.toString(), "")
            } catch (e: Exception) {
                if (attempt < maxRetries) {
                    connectionMutex.lock()
                    try {
                        _state.set(State.DISCONNECTED)
                        connectionRef.set(null)
                    } finally {
                        connectionMutex.unlock()
                    }
                    continue
                } else {
                    return@withContext ShellResult(-1, "", e.message ?: "Error executing command")
                }
            }
        }
        return@withContext ShellResult(-1, "", "Failed to execute command")
    }

    fun isConnected(): Boolean {
        return state == State.CONNECTED
    }
}
