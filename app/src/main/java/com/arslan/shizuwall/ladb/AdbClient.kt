package com.arslan.shizuwall.ladb

import android.net.ssl.SSLSockets
import android.os.Build
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.Principal
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

private const val A_CNXN = 0x4e584e43
private const val A_OPEN = 0x4e45504f
private const val A_OKAY = 0x59414b4f
private const val A_CLSE = 0x45534c43
private const val A_WRTE = 0x45545257
private const val A_STLS = 0x534c5453
private const val A_AUTH = 0x48545541
private val AUTH_PADDING = byteArrayOf(0x00, 0x01) + ByteArray(218) { -1 } +
    byteArrayOf(0x00, 0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)
private const val A_VERSION = 0x01000000
private const val DEVICE_NAME = "ShizuWall"

class AdbConnection(
    private val host: String,
    private val port: Int,
    private val key: PrivateKey,
    private val cert: X509Certificate
) : Closeable {
    private var socket: Socket = Socket().also {
        it.tcpNoDelay = true
        runCatching { it.connect(InetSocketAddress(host, port), 10_000) }.onFailure { e -> it.close(); throw e }
    }
    private var nextId = 0

    fun connect(timeoutMs: Int) {
        socket.soTimeout = timeoutMs
        write(A_CNXN, A_VERSION, 4096, "host::\u0000".toByteArray())
        var signed = false
        var tls = false
        while (true) {
            val msg = read()
            when (msg.cmd) {
                A_CNXN -> return
                A_AUTH -> if (!tls && msg.arg0 == 1) {
                    write(A_AUTH, if (signed) 3 else 2, 0, if (signed) adbPublicKey(cert.publicKey as RSAPublicKey) else sign(msg.data))
                    signed = true
                }
                A_STLS -> {
                    write(A_STLS, A_VERSION, 0)
                    socket = (tlsContext(key, cert).socketFactory.createSocket(socket, host, port, true) as SSLSocket)
                        .apply { soTimeout = timeoutMs; startHandshake() }
                    tls = true
                }
            }
        }
    }

    @Synchronized
    fun shell(cmd: String, timeoutMs: Int): String {
        socket.soTimeout = timeoutMs
        val local = ++nextId
        write(A_OPEN, local, 0, "shell:$cmd\u0000".toByteArray())
        val out = ByteArrayOutputStream()
        var opened = false
        while (true) {
            val (command, remote, target, data) = read()
            if (target != local) continue
            when (command) {
                A_OKAY -> opened = true
                A_WRTE -> { out.write(data); write(A_OKAY, local, remote) }
                A_CLSE -> {
                    write(A_CLSE, local, remote)
                    if (opened) return out.toString("UTF-8") else throw IOException("Stream open rejected")
                }
            }
        }
    }

    override fun close() = socket.close()

    private fun sign(token: ByteArray): ByteArray =
        Cipher.getInstance("RSA/ECB/NoPadding").run { init(Cipher.ENCRYPT_MODE, key); doFinal(AUTH_PADDING + token) }

    private data class Msg(val cmd: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    private fun read(): Msg {
        val input = DataInputStream(socket.getInputStream())
        val h = ByteBuffer.wrap(ByteArray(24).also(input::readFully)).order(ByteOrder.LITTLE_ENDIAN)
        val msg = Msg(h.int, h.int, h.int, ByteArray(h.int.also { if (it !in 0..(1 shl 20)) throw IOException("Bad payload $it") }))
        input.readFully(msg.data)
        return msg
    }

    private fun write(cmd: Int, arg0: Int, arg1: Int, data: ByteArray = ByteArray(0)) {
        val out = socket.getOutputStream()
        out.write(
            ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.size)
                .putInt(data.sumOf { it.toInt() and 0xFF }).putInt(cmd.inv()).put(data).array()
        )
        out.flush()
    }
}

fun adbPair(host: String, port: Int, code: ByteArray, key: PrivateKey, cert: X509Certificate) {
    (tlsContext(key, cert).socketFactory.createSocket(host, port) as SSLSocket).use { s ->
        s.tcpNoDelay = true
        s.soTimeout = 30_000
        s.startHandshake()
        val spake = Spake2(code + exportKeyingMaterial(s))
        val input = DataInputStream(s.inputStream)
        fun send(type: Int, data: ByteArray) =
            s.outputStream.write(ByteBuffer.allocate(6 + data.size).put(1).put(type.toByte()).putInt(data.size).put(data).array())
        fun receive(type: Int): ByteArray {
            val h = ByteBuffer.wrap(ByteArray(6).also(input::readFully))
            if (h.get() != 1.toByte() || h.get() != type.toByte()) throw IOException("Unexpected pairing packet")
            return ByteArray(h.int.also { if (it !in 1..16384) throw IOException("Bad payload $it") }).also(input::readFully)
        }
        send(0, spake.message)
        val aes = hkdf(spake.key(receive(0)))
        send(1, gcm(aes, Cipher.ENCRYPT_MODE, ByteArray(8192).also { adbPublicKey(cert.publicKey as RSAPublicKey).copyInto(it, 1) }))
        gcm(aes, Cipher.DECRYPT_MODE, receive(1))
    }
}

fun selfSignedCertificate(key: PrivateKey, publicKey: ByteArray): X509Certificate {
    val alg = der(0x30, byteArrayOf(0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x0b, 0x05, 0x00))
    val name = der(0x30, der(0x31, der(0x30, byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03), der(0x0c, DEVICE_NAME.toByteArray()))))
    val validity = der(0x30, der(0x17, "200101000000Z".toByteArray()), der(0x17, "491231235959Z".toByteArray()))
    val tbs = der(0x30, der(0xa0, der(0x02, byteArrayOf(2))), der(0x02, byteArrayOf(1)), alg, name, validity, name, publicKey)
    val sig = Signature.getInstance("SHA256withRSA").run { initSign(key); update(tbs); sign() }
    val encoded = der(0x30, tbs, alg, der(0x03, byteArrayOf(0), sig))
    return CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(encoded)) as X509Certificate
}

private fun der(tag: Int, vararg parts: ByteArray): ByteArray {
    val body = parts.fold(ByteArray(0), ByteArray::plus)
    val n = body.size
    val len = when {
        n < 0x80 -> byteArrayOf(n.toByte())
        n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
        else -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
    }
    return byteArrayOf(tag.toByte()) + len + body
}

internal fun adbPublicKey(pub: RSAPublicKey): ByteArray {
    val n = pub.modulus
    val r32 = BigInteger.ONE.shiftLeft(32)
    val raw = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
        .putInt(64)
        .putInt(r32.subtract(n.mod(r32).modInverse(r32)).toInt())
        .put(leBytes(n, 256))
        .put(leBytes(BigInteger.ONE.shiftLeft(2048).modPow(BigInteger.valueOf(2), n), 256))
        .putInt(pub.publicExponent.toInt())
        .array()
    return Base64.getEncoder().encode(raw) + " $DEVICE_NAME\u0000".toByteArray()
}

internal fun hkdf(ikm: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
    init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
    init(SecretKeySpec(doFinal(ikm), "HmacSHA256"))
    doFinal("adb pairing_auth aes-128-gcm key".toByteArray() + 1.toByte()).copyOf(16)
}

internal fun gcm(key: ByteArray, mode: Int, data: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
    init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, ByteArray(12)))
    doFinal(data)
}

private fun exportKeyingMaterial(s: SSLSocket): ByteArray =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SSLSockets.exportKeyingMaterial(s, "adb-label\u0000", null, 64) ?: throw IOException("No TLS keying material")
    } else {
        Class.forName("com.android.org.conscrypt.Conscrypt")
            .getMethod("exportKeyingMaterial", SSLSocket::class.java, String::class.java, ByteArray::class.java, Int::class.javaPrimitiveType)
            .invoke(null, s, "adb-label\u0000", null, 64) as ByteArray
    }

private fun tlsContext(key: PrivateKey, cert: X509Certificate): SSLContext = SSLContext.getInstance("TLSv1.3").apply {
    init(arrayOf(object : X509ExtendedKeyManager() {
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf("key")
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = "key"
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = null
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?) = null
        override fun getCertificateChain(alias: String?) = arrayOf(cert)
        override fun getPrivateKey(alias: String?) = key
    }), arrayOf(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
    }), null)
}
