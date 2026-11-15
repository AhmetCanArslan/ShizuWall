package com.arslan.shizuwall.ladb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.DataInputStream
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import kotlin.concurrent.thread
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher

class AdbClientTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private val aliceSeed = ByteArray(64) { (it * 7 + 1).toByte() }

    @Test
    fun spake2MatchesReferenceVector() {
        val alice = Spake2("123456".toByteArray(), aliceSeed)
        assertEquals("16eccf999bbb16fb9667d4b3c8bd35024c54269c40893532dfd3442fa83c98d4", hex(alice.message))
        assertEquals(
            "ad4fe3f3f4b45cda91682b376a432c78663aae65e3cfe4252ee15f22350ab9bdf23abb2743bcdc56ba63185f76d372c82b47eede952565b978e63472e0b2b440",
            hex(alice.key(hex("f2b1e2d6c0503c94ed38c7e6c7a249378705ce276e0bd572c863f678ab89ed7e")))
        )
    }

    @Test
    fun spake2WrongPasswordDiffers() {
        val bobMessage = hex("f2b1e2d6c0503c94ed38c7e6c7a249378705ce276e0bd572c863f678ab89ed7e")
        assertFalse(Spake2("654321".toByteArray(), aliceSeed).key(bobMessage).contentEquals(Spake2("123456".toByteArray(), aliceSeed).key(bobMessage)))
    }

    @Test
    fun pairingCipherRoundTrip() {
        val aes = hkdf(ByteArray(64) { it.toByte() })
        assertEquals("5e234c26fa41fb42e5d493b262c0def1", hex(aes))
        val plain = ByteArray(8192) { it.toByte() }
        assertArrayEquals(plain, gcm(aes, Cipher.DECRYPT_MODE, gcm(aes, Cipher.ENCRYPT_MODE, plain)))
    }

    @Test
    fun selfSignedCertificateVerifies() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cert = selfSignedCertificate(kp.private, kp.public.encoded)
        cert.verify(kp.public)
        assertEquals(kp.public, cert.publicKey)
        assertEquals(4 * 175 + " ShizuWall\u0000".length, adbPublicKey(kp.public as RSAPublicKey).size)
    }

    @Test
    fun legacyAuthSignsThenSendsPublicKeyAndRunsShell() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cert = selfSignedCertificate(kp.private, kp.public.encoded)
        val token = ByteArray(20) { (it + 1).toByte() }
        val seen = mutableListOf<String>()
        ServerSocket(0).use { server ->
            val adbd = thread {
                server.accept().use { s ->
                    val input = DataInputStream(s.getInputStream())
                    fun read(): Triple<Int, Int, ByteArray> {
                        val h = ByteBuffer.wrap(ByteArray(24).also(input::readFully)).order(ByteOrder.LITTLE_ENDIAN)
                        val cmd = h.int; val arg0 = h.int; h.int
                        val len = h.int; val sum = h.int
                        assertEquals(cmd.inv(), h.int)
                        val data = ByteArray(len).also(input::readFully)
                        assertEquals(data.sumOf { it.toInt() and 0xFF }, sum)
                        return Triple(cmd, arg0, data)
                    }
                    fun write(cmd: Int, arg0: Int, arg1: Int, data: ByteArray = ByteArray(0)) = s.getOutputStream().write(
                        ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN).putInt(cmd).putInt(arg0).putInt(arg1)
                            .putInt(data.size).putInt(data.sumOf { it.toInt() and 0xFF }).putInt(cmd.inv()).put(data).array()
                    )
                    assertEquals("host::\u0000", String(read().third))
                    write(0x48545541, 1, 0, token)
                    val (_, sigType, sig) = read()
                    assertEquals(2, sigType)
                    val unpadded = Cipher.getInstance("RSA/ECB/PKCS1Padding").run { init(Cipher.DECRYPT_MODE, kp.public); doFinal(sig) }
                    assertArrayEquals(byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14) + token, unpadded)
                    write(0x48545541, 1, 0, token)
                    val (_, keyType, pub) = read()
                    assertEquals(3, keyType)
                    assertArrayEquals(adbPublicKey(kp.public as RSAPublicKey), pub)
                    write(0x4e584e43, 0x01000000, 4096, "device::\u0000".toByteArray())
                    val (open, local, dest) = read()
                    assertEquals(0x4e45504f, open)
                    assertEquals("shell:id\u0000", String(dest))
                    write(0x59414b4f, 7, local)
                    write(0x45545257, 7, local, "uid=2000".toByteArray())
                    assertEquals(0x59414b4f, read().first)
                    write(0x45534c43, 7, local)
                    assertEquals(0x45534c43, read().first)
                    seen += "done"
                }
            }
            AdbConnection("127.0.0.1", server.localPort, kp.private, cert).use { c ->
                c.connect(5_000)
                assertEquals("uid=2000", c.shell("id", 5_000))
            }
            adbd.join(5_000)
        }
        assertEquals(listOf("done"), seen)
    }
}
