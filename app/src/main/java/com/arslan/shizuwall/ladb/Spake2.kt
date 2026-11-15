package com.arslan.shizuwall.ladb

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

private typealias Point = Pair<BigInteger, BigInteger>

class Spake2(
    password: ByteArray,
    seed: ByteArray = ByteArray(64).also { SecureRandom().nextBytes(it) }
) {
    private val passwordHash = MessageDigest.getInstance("SHA-512").digest(password)
    private val passwordScalar = (0..2).fold(fromLe(passwordHash).mod(L)) { s, i -> if (s.testBit(i)) s + L.shiftLeft(i) else s }
    private val privateKey = fromLe(seed).mod(L).shiftLeft(3)
    val message: ByteArray = encode(add(mul(privateKey, BASE), mul(passwordScalar, M)))

    fun key(theirMessage: ByteArray): ByteArray {
        val q = decode(theirMessage) ?: throw IllegalArgumentException("Invalid SPAKE2 point")
        val peerMask = mul(passwordScalar, N)
        val dh = encode(mul(privateKey, add(q, (P - peerMask.first).mod(P) to peerMask.second)))
        val sha = MessageDigest.getInstance("SHA-512")
        listOf(CLIENT, SERVER, message, theirMessage, dh, passwordHash).forEach {
            sha.update(leBytes(BigInteger.valueOf(it.size.toLong()), 8))
            sha.update(it)
        }
        return sha.digest()
    }

    companion object {
        private val CLIENT = "adb pair client\u0000".toByteArray()
        private val SERVER = "adb pair server\u0000".toByteArray()
        private val P = BigInteger.ONE.shiftLeft(255) - BigInteger.valueOf(19)
        private val L = BigInteger.ONE.shiftLeft(252) + BigInteger("27742317777372353535851937790883648493")
        private val D = BigInteger.valueOf(-121665).mod(P) * BigInteger.valueOf(121666).modInverse(P) % P
        private val SQRT_M1 = BigInteger.valueOf(2).modPow((P - BigInteger.ONE) / BigInteger.valueOf(4), P)
        private val BASE = point("5866666666666666666666666666666666666666666666666666666666666666")
        private val M = point("5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e")
        private val N = point("10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778")

        private fun point(hex: String) = decode(ByteArray(32) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() })!!

        private fun add(a: Point, b: Point): Point {
            val t = D * a.first % P * b.first % P * a.second % P * b.second % P
            return (a.first * b.second + a.second * b.first) * (BigInteger.ONE + t).modInverse(P) % P to
                (a.second * b.second + a.first * b.first) * (BigInteger.ONE - t).mod(P).modInverse(P) % P
        }

        private fun mul(k: BigInteger, p: Point): Point =
            (k.bitLength() - 1 downTo 0).fold(BigInteger.ZERO to BigInteger.ONE) { r, i ->
                add(r, r).let { if (k.testBit(i)) add(it, p) else it }
            }

        private fun encode(p: Point) = leBytes(if (p.first.testBit(0)) p.second.setBit(255) else p.second, 32)

        private fun decode(bytes: ByteArray): Point? {
            val v = fromLe(bytes)
            val y = v.clearBit(255)
            if (y >= P) return null
            val y2 = y * y % P
            val x2 = (y2 - BigInteger.ONE).mod(P) * (D * y2 + BigInteger.ONE).modInverse(P) % P
            val r = x2.modPow((P + BigInteger.valueOf(3)) / BigInteger.valueOf(8), P)
            val x = listOf(r, r * SQRT_M1 % P).firstOrNull { it * it % P == x2 } ?: return null
            return (if (x.testBit(0) == v.testBit(255)) x else (P - x).mod(P)) to y
        }
    }
}

internal fun fromLe(bytes: ByteArray) = BigInteger(1, bytes.reversedArray())

internal fun leBytes(n: BigInteger, size: Int) = ByteArray(size) { n.shiftRight(8 * it).toByte() }
