package com.arslan.shizuwall.ladb

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.arslan.shizuwall.shell.ShellResult
import io.github.muntashirakon.adb.AdbConnection
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.conscrypt.Conscrypt
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
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

        private const val PREFS_NAME = "ladb_prefs"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
    }

    enum class State {
        UNCONFIGURED,
        PAIRED,
        CONNECTED,
        ERROR,
        DISCONNECTED
    }

    private val _state = AtomicReference(State.UNCONFIGURED)
    val state: State
        get() = _state.get()

    private val connectionRef = AtomicReference<AdbConnection?>(null)
    private val keyMaterialRef = AtomicReference<Pair<PrivateKey, Certificate>?>(null)

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

    private fun getPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
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
            // Save configuration
            getPrefs().edit()
                .putString(KEY_HOST, host)
                .putInt(KEY_PORT, port)
                .apply()

            if (pairingCode != null) {
                // TODO: Implement pairing with code using AdbPairing if available
            }

            _state.set(State.PAIRED)
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            _state.set(State.ERROR)
            return@withContext false
        }
    }

    suspend fun connect(host: String? = null, port: Int? = null, tls: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetHost = host ?: getPrefs().getString(KEY_HOST, null)
            val targetPort = port ?: getPrefs().getInt(KEY_PORT, 5555)

            if (targetHost == null) return@withContext false

            // Note: Proper ADB pairing/TLS is not fully wired yet. This supports direct ADB-over-TCP.
            val (privateKey, certificate) = getOrCreateKeyMaterial()
            val conn = AdbConnection.Builder()
                .setHost(targetHost)
                .setPort(targetPort)
                .setPrivateKey(privateKey)
                .setCertificate(certificate)
                .build()

            conn.connect()
            connectionRef.set(conn)

            _state.set(State.CONNECTED)
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            _state.set(State.ERROR)
            return@withContext false
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            connectionRef.getAndSet(null)?.close()
            _state.set(State.DISCONNECTED)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun execShell(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        var conn = connectionRef.get()
        if (state != State.CONNECTED || conn == null) {
            val ok = try {
                connect()
            } catch (_: Exception) {
                false
            }
            if (!ok) {
                return@withContext ShellResult(-1, "", "Not connected")
            }
            conn = connectionRef.get()
            if (conn == null) {
                return@withContext ShellResult(-1, "", "Not connected")
            }
        }

        try {
            val stream: AdbStream = conn.open("shell:$cmd")
            val stdout = stream.openInputStream().bufferedReader().use { it.readText() }
            try {
                stream.close()
            } catch (_: Exception) {
            }
            return@withContext ShellResult(0, stdout, "")
        } catch (e: Exception) {
            return@withContext ShellResult(-1, "", e.message ?: "Error executing command")
        }
    }

    fun isConnected(): Boolean {
        return state == State.CONNECTED
    }
}
