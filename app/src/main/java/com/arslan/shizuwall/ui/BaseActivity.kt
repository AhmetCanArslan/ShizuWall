package com.arslan.shizuwall.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import com.arslan.shizuwall.R
import com.arslan.shizuwall.security.AppLock
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors

open class BaseActivity : AppCompatActivity() {
    protected open val bypassAppLock = false
    private var currentFont: String? = null
    private var currentDynamicColor: Boolean = true
    private var currentAmoledBlack: Boolean = false

    companion object {
        private var shouldAnimateFadeIn = false

        fun requestFadeInAnimation() {
            shouldAnimateFadeIn = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Disable system transition if we're doing our own fade animation
        if (shouldAnimateFadeIn) {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
        
        val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        currentFont = prefs.getString(MainActivity.KEY_SELECTED_FONT, "default") ?: "default"
        currentDynamicColor = prefs.getBoolean(MainActivity.KEY_USE_DYNAMIC_COLOR, true)
        currentAmoledBlack = prefs.getBoolean(MainActivity.KEY_USE_AMOLED_BLACK, false)

        val themeRes = when {
            currentAmoledBlack && currentFont == "ndot" -> R.style.Theme_ShizuWall_Amoled_Ndot
            currentAmoledBlack -> R.style.Theme_ShizuWall_Amoled
            currentFont == "ndot" -> R.style.Theme_ShizuWall_Ndot
            else -> R.style.Theme_ShizuWall
        }
        setTheme(themeRes)
        
        super.onCreate(savedInstanceState)

        if (currentDynamicColor) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        applyRecentsPrivacy()

        applyRecentsAppearance()
    }

    protected fun applyRecentsPrivacy() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            setRecentsScreenshotEnabled(!AppLock.isEnabled(this))
        }
    }

    private fun applyRecentsAppearance() {
        val surface = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurface,
            Color.BLACK
        )
        val opaque = ColorUtils.setAlphaComponent(surface, 255)
        val description = if (android.os.Build.VERSION.SDK_INT >= 33) {
            ActivityManager.TaskDescription.Builder()
                .setBackgroundColor(opaque)
                .setStatusBarColor(opaque)
                .setNavigationBarColor(opaque)
                .build()
        } else {
            @Suppress("DEPRECATION")
            ActivityManager.TaskDescription(null, null as Bitmap?, opaque)
        }
        setTaskDescription(description)
    }

    override fun onStart() {
        super.onStart()
        applyRecentsPrivacy()
        AppLock.onActivityStarted()
        if (!bypassAppLock && AppLock.requiresUnlock(this)) {
            startActivity(
                Intent(this, AppLockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }

    override fun onStop() {
        super.onStop()
        AppLock.onActivityStopped(isChangingConfigurations)
    }

    override fun onContentChanged() {
        super.onContentChanged()
        if (shouldAnimateFadeIn) {
            shouldAnimateFadeIn = false
            val rootView = findViewById<View>(android.R.id.content)
            rootView.alpha = 0f
            rootView.animate()
                .alpha(1f)
                .setDuration(350)
                .setStartDelay(50)
                .start()
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val savedFont = prefs.getString(MainActivity.KEY_SELECTED_FONT, "default") ?: "default"
        val savedDynamicColor = prefs.getBoolean(MainActivity.KEY_USE_DYNAMIC_COLOR, true)
        val savedAmoledBlack = prefs.getBoolean(MainActivity.KEY_USE_AMOLED_BLACK, false)

        if (savedFont != currentFont || savedDynamicColor != currentDynamicColor || savedAmoledBlack != currentAmoledBlack) {
            recreateWithAnimation()
        }
    }

    protected fun recreateWithAnimation() {
        val rootView = findViewById<View>(android.R.id.content)
        if (rootView != null) {
            rootView.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction {
                    shouldAnimateFadeIn = true
                    AppLock.suppressNextRelock()
                    recreate()
                }
                .start()
        } else {
            recreate()
        }
    }
}
