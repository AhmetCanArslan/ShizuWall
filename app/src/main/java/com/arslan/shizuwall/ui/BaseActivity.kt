package com.arslan.shizuwall.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.arslan.shizuwall.R
import com.google.android.material.color.DynamicColors

open class BaseActivity : AppCompatActivity() {
    private var currentFont: String? = null
    private var currentDynamicColor: Boolean = true

    companion object {
        private var shouldAnimateFadeIn = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        currentFont = prefs.getString(MainActivity.KEY_SELECTED_FONT, "default") ?: "default"
        currentDynamicColor = prefs.getBoolean(MainActivity.KEY_USE_DYNAMIC_COLOR, true)
        
        if (currentFont == "ndot") {
            setTheme(R.style.Theme_ShizuWall_Ndot)
        } else {
            setTheme(R.style.Theme_ShizuWall)
        }
        
        super.onCreate(savedInstanceState)

        if (currentDynamicColor) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
    }

    override fun onContentChanged() {
        super.onContentChanged()
        if (shouldAnimateFadeIn) {
            shouldAnimateFadeIn = false
            val rootView = findViewById<View>(android.R.id.content)
            rootView.alpha = 0f
            rootView.animate()
                .alpha(1f)
                .setDuration(400)
                .start()
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val savedFont = prefs.getString(MainActivity.KEY_SELECTED_FONT, "default") ?: "default"
        val savedDynamicColor = prefs.getBoolean(MainActivity.KEY_USE_DYNAMIC_COLOR, true)

        if (savedFont != currentFont || savedDynamicColor != currentDynamicColor) {
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
                    recreate()
                }
                .start()
        } else {
            recreate()
        }
    }
}
