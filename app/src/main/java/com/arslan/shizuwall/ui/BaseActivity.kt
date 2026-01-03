package com.arslan.shizuwall.ui

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.arslan.shizuwall.R
import com.google.android.material.color.DynamicColors

open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val savedFont = prefs.getString(MainActivity.KEY_SELECTED_FONT, "default") ?: "default"
        
        if (savedFont == "ndot") {
            setTheme(R.style.Theme_ShizuWall_Ndot)
        } else {
            setTheme(R.style.Theme_ShizuWall)
        }
        
        super.onCreate(savedInstanceState)

        val useDynamicColor = prefs.getBoolean(MainActivity.KEY_USE_DYNAMIC_COLOR, true)
        if (useDynamicColor) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
    }
}
