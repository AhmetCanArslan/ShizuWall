package com.arslan.shizuwall.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.view.animation.OvershootInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.arslan.shizuwall.R
import com.arslan.shizuwall.security.AppLock
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.MaterialColors
import android.widget.ImageView

class AppLockActivity : BaseActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_UNLOCK = "unlock"
        const val MODE_VERIFY = "verify"
        const val MODE_SETUP = "setup"

        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_MS = 30_000L
        private const val AUTO_SUBMIT_DELAY_MS = 180L

        private val SHAPES = intArrayOf(
            R.drawable.shape_pin_clover,
            R.drawable.shape_pin_cookie6,
            R.drawable.shape_pin_cookie7,
            R.drawable.shape_pin_cookie9,
            R.drawable.shape_pin_flower,
            R.drawable.shape_pin_burst,
            R.drawable.shape_pin_triangle,
            R.drawable.shape_pin_pentagon,
            R.drawable.shape_pin_pill,
            R.drawable.shape_pin_square
        )
    }

    override val bypassAppLock = true

    private lateinit var mode: String
    private lateinit var pinDots: LinearLayout
    private lateinit var tvTitle: TextView
    private lateinit var tvMessage: TextView
    private lateinit var keypad: ViewGroup
    private lateinit var btnBiometric: MaterialButton
    private lateinit var pinLengthGroup: MaterialButtonToggleGroup
    private lateinit var tvInfo: TextView
    private lateinit var tvPinLengthLabel: TextView

    private val entered = StringBuilder()
    private var targetLength = AppLock.DEFAULT_PIN_LENGTH
    private var shapes = IntArray(AppLock.MAX_PIN_LENGTH)
    private var firstPin: String? = null
    private var attempts = 0
    private var biometricCancel: CancellationSignal? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_UNLOCK
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        enableEdgeToEdge()
        setContentView(R.layout.activity_app_lock)

        val root = findViewById<View>(R.id.appLockRoot)
        if (getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(MainActivity.KEY_USE_AMOLED_BLACK, false)
        ) {
            root.setBackgroundColor(android.graphics.Color.BLACK)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }

        pinDots = findViewById(R.id.pinDots)
        tvTitle = findViewById(R.id.tvLockTitle)
        tvMessage = findViewById(R.id.tvLockMessage)
        keypad = findViewById(R.id.keypad)
        btnBiometric = findViewById(R.id.btnBiometric)
        pinLengthGroup = findViewById(R.id.pinLengthGroup)
        tvInfo = findViewById(R.id.tvLockInfo)
        tvPinLengthLabel = findViewById(R.id.tvPinLengthLabel)

        targetLength = if (mode == MODE_SETUP) AppLock.DEFAULT_PIN_LENGTH else AppLock.pinLength(this)
        tvInfo.visibility = if (mode == MODE_SETUP) View.VISIBLE else View.GONE
        buildLengthChooser()
        buildIndicators()
        wireKeypad()
        updateTitle()

        val biometricUsable = mode != MODE_SETUP && AppLock.biometricsEnabled(this) && AppLock.biometricsAvailable(this)
        btnBiometric.visibility = if (biometricUsable) View.VISIBLE else View.GONE
        btnBiometric.setOnClickListener { promptBiometric() }

        if (mode == MODE_UNLOCK) AppLock.markPromptVisible(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mode == MODE_UNLOCK) finishAffinity() else finish()
            }
        })

        if (biometricUsable && savedInstanceState == null) promptBiometric()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        biometricCancel?.cancel()
        if (mode == MODE_UNLOCK) AppLock.markPromptVisible(false)
    }

    private fun buildLengthChooser() {
        if (mode != MODE_SETUP) {
            pinLengthGroup.visibility = View.GONE
            tvPinLengthLabel.visibility = View.GONE
            return
        }
        setChooserVisible(true)
        for (length in AppLock.MIN_PIN_LENGTH..AppLock.MAX_PIN_LENGTH) {
            val button = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            button.id = View.generateViewId()
            button.tag = length
            button.text = length.toString()
            button.minWidth = 0
            button.minimumWidth = 0
            button.insetTop = 0
            button.insetBottom = 0
            pinLengthGroup.addView(
                button,
                LinearLayout.LayoutParams((52 * resources.displayMetrics.density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            )
            if (length == targetLength) pinLengthGroup.check(button.id)
        }
        pinLengthGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val length = pinLengthGroup.findViewById<View>(checkedId)?.tag as? Int ?: return@addOnButtonCheckedListener
            if (length == targetLength) return@addOnButtonCheckedListener
            targetLength = length
            firstPin = null
            tvMessage.text = ""
            updateTitle()
            clearEntry()
            buildIndicators()
        }
    }

    private fun setChooserVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        pinLengthGroup.visibility = visibility
        tvPinLengthLabel.visibility = visibility
    }

    private fun buildIndicators() {
        pinDots.removeAllViews()
        val size = (22 * resources.displayMetrics.density).toInt()
        val margin = (6 * resources.displayMetrics.density).toInt()
        shuffleShapes()
        repeat(targetLength) {
            val shape = ImageView(this)
            val params = LinearLayout.LayoutParams(size, size)
            params.marginStart = margin
            params.marginEnd = margin
            shape.layoutParams = params
            pinDots.addView(shape)
        }
        renderDots(false)
    }

    private fun shuffleShapes() {
        var previous = -1
        for (i in shapes.indices) {
            var pick = SHAPES.random()
            while (pick == previous) pick = SHAPES.random()
            shapes[i] = pick
            previous = pick
        }
    }

    private fun wireKeypad() {
        for (i in 0 until keypad.childCount) {
            val key = keypad.getChildAt(i)
            val digit = key.tag as? String
            key.setOnClickListener {
                pop(key)
                when {
                    digit != null -> append(digit)
                    key.id == R.id.btnDelete -> delete()
                    key.id == R.id.btnSubmit -> submit()
                }
            }
        }
    }

    private fun pop(view: View) {
        view.animate().cancel()
        view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(60).withEndAction {
            view.animate().scaleX(1f).scaleY(1f)
                .setInterpolator(OvershootInterpolator(4f)).setDuration(220).start()
        }.start()
    }

    private fun append(digit: String) {
        if (entered.length >= targetLength) return
        entered.append(digit)
        tvMessage.text = ""
        renderDots(true)
        if (entered.length == targetLength) {
            setKeypadEnabled(false)
            handler.postDelayed({
                setKeypadEnabled(true)
                if (entered.length == targetLength) submit()
            }, AUTO_SUBMIT_DELAY_MS)
        }
    }

    private fun delete() {
        if (entered.isEmpty()) return
        entered.deleteCharAt(entered.length - 1)
        renderDots(false)
    }

    private fun submit() {
        val pin = entered.toString()
        if (pin.length != targetLength) {
            fail(getString(R.string.app_lock_pin_length_required, targetLength))
            return
        }
        if (mode == MODE_SETUP) {
            val first = firstPin
            when {
                first == null -> {
                    firstPin = pin
                    setChooserVisible(false)
                    clearEntry()
                    updateTitle()
                }
                first == pin -> {
                    AppLock.setPin(this, pin)
                    setResult(RESULT_OK)
                    finish()
                }
                else -> {
                    firstPin = null
                    setChooserVisible(true)
                    updateTitle()
                    fail(getString(R.string.app_lock_pin_mismatch))
                }
            }
            return
        }
        if (AppLock.verify(this, pin)) {
            AppLock.markUnlocked()
            setResult(RESULT_OK)
            finish()
        } else {
            attempts++
            fail(getString(R.string.app_lock_wrong_pin))
            if (attempts >= MAX_ATTEMPTS) startLockout(LOCKOUT_MS)
        }
    }

    private fun fail(message: String) {
        clearEntry()
        tvMessage.text = message
        pinDots.performHapticFeedback(HapticFeedbackConstants.REJECT)
        ObjectAnimator.ofFloat(pinDots, "translationX", 0f, -24f, 22f, -16f, 12f, -6f, 0f)
            .setDuration(420).start()
    }

    private fun clearEntry() {
        entered.setLength(0)
        shuffleShapes()
        renderDots(false)
    }

    private fun startLockout(remaining: Long) {
        val unlockedAt = remaining <= 0
        setKeypadEnabled(unlockedAt)
        if (unlockedAt) {
            attempts = 0
            tvMessage.text = ""
            return
        }
        tvMessage.text = getString(R.string.app_lock_locked_out, (remaining / 1000).toInt())
        handler.postDelayed({ startLockout(remaining - 1000) }, 1000)
    }

    private fun setKeypadEnabled(enabled: Boolean) {
        for (i in 0 until keypad.childCount) keypad.getChildAt(i).isEnabled = enabled
        btnBiometric.isEnabled = enabled
        for (i in 0 until pinLengthGroup.childCount) pinLengthGroup.getChildAt(i).isEnabled = enabled
    }

    private fun updateTitle() {
        tvTitle.setText(
            when {
                mode != MODE_SETUP -> R.string.app_lock_enter_pin
                firstPin == null -> R.string.app_lock_set_pin
                else -> R.string.app_lock_confirm_pin
            }
        )
    }

    private fun renderDots(pop: Boolean) {
        val filledColor = MaterialColors.getColor(pinDots, androidx.appcompat.R.attr.colorPrimary)
        val emptyColor = MaterialColors.getColor(pinDots, com.google.android.material.R.attr.colorOutlineVariant)
        for (i in 0 until pinDots.childCount) {
            val shape = pinDots.getChildAt(i) as ImageView
            val filled = i < entered.length
            shape.setImageResource(if (filled) shapes[i] else R.drawable.shape_pin_circle)
            shape.imageTintList = ColorStateList.valueOf(if (filled) filledColor else emptyColor)
            if (pop && i == entered.length - 1) {
                shape.animate().cancel()
                shape.scaleX = 0.2f
                shape.scaleY = 0.2f
                shape.rotation = -36f
                shape.animate().scaleX(1f).scaleY(1f).rotation(0f)
                    .setInterpolator(OvershootInterpolator(3f)).setDuration(280).start()
            } else {
                shape.animate().cancel()
                shape.rotation = 0f
                val target = if (filled) 1f else 0.42f
                shape.scaleX = target
                shape.scaleY = target
            }
        }
    }

    private fun promptBiometric() {
        biometricCancel?.cancel()
        val cancel = CancellationSignal()
        biometricCancel = cancel
        BiometricPrompt.Builder(this)
            .setTitle(getString(R.string.app_lock_biometric_title))
            .setNegativeButton(getString(R.string.app_lock_use_pin), mainExecutor) { _, _ -> }
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
            .authenticate(cancel, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    AppLock.markUnlocked()
                    setResult(RESULT_OK)
                    finish()
                }
            })
    }
}
