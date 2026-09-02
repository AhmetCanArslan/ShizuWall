package com.arslan.shizuwall.security

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.util.Base64
import com.arslan.shizuwall.ui.MainActivity
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object AppLock {

    const val KEY_ENABLED = "app_lock_enabled"
    const val KEY_PIN = "app_lock_pin"
    const val KEY_BIOMETRIC = "app_lock_biometric"
    const val KEY_PIN_LENGTH = "app_lock_pin_length"

    const val MIN_PIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 8
    const val DEFAULT_PIN_LENGTH = 4

    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    @Volatile private var unlocked = false
    @Volatile private var promptVisible = false
    @Volatile private var suppressRelock = false
    private var startedActivities = 0

    private fun prefs(context: Context) =
        context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean {
        val p = prefs(context)
        return p.getBoolean(KEY_ENABLED, false) && p.getString(KEY_PIN, null) != null
    }

    fun biometricsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BIOMETRIC, false)

    fun setBiometricsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    }

    fun biometricsAvailable(context: Context): Boolean {
        val manager = context.getSystemService(BiometricManager::class.java) ?: return false
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun pinLength(context: Context): Int =
        prefs(context).getInt(KEY_PIN_LENGTH, DEFAULT_PIN_LENGTH)
            .coerceIn(MIN_PIN_LENGTH, MAX_PIN_LENGTH)

    fun setPin(context: Context, pin: String) {
        prefs(context).edit()
            .putString(KEY_PIN, encode(pin))
            .putInt(KEY_PIN_LENGTH, pin.length)
            .putBoolean(KEY_ENABLED, true)
            .apply()
        unlocked = true
    }

    fun verify(context: Context, pin: String): Boolean {
        val stored = prefs(context).getString(KEY_PIN, null) ?: return false
        val parts = stored.split(':')
        if (parts.size != 3) return false
        val iterations = parts[0].toIntOrNull() ?: return false
        val salt = Base64.decode(parts[1], Base64.NO_WRAP)
        val expected = Base64.decode(parts[2], Base64.NO_WRAP)
        return MessageDigest.isEqual(expected, derive(pin, salt, iterations, expected.size * 8))
    }

    fun disable(context: Context) {
        prefs(context).edit()
            .remove(KEY_PIN)
            .remove(KEY_BIOMETRIC)
            .remove(KEY_PIN_LENGTH)
            .putBoolean(KEY_ENABLED, false)
            .apply()
        unlocked = true
    }

    fun requiresUnlock(context: Context): Boolean = isEnabled(context) && !unlocked && !promptVisible

    fun markUnlocked() {
        unlocked = true
    }

    fun markPromptVisible(visible: Boolean) {
        promptVisible = visible
    }

    fun suppressNextRelock() {
        suppressRelock = true
    }

    @Synchronized
    fun onActivityStarted() {
        startedActivities++
        suppressRelock = false
    }

    @Synchronized
    fun onActivityStopped(changingConfigurations: Boolean) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0 && !changingConfigurations && !suppressRelock) {
            unlocked = false
        }
    }

    private fun encode(pin: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt, ITERATIONS, KEY_BITS)
        return "$ITERATIONS:${Base64.encodeToString(salt, Base64.NO_WRAP)}:${Base64.encodeToString(hash, Base64.NO_WRAP)}"
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int, bits: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, bits)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
