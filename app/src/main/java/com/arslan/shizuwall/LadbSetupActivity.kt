package com.arslan.shizuwall

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.arslan.shizuwall.R
import com.google.android.material.appbar.MaterialToolbar

class LadbSetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ladb_setup)

        val toolbar = findViewById<MaterialToolbar?>(R.id.ladbToolbar)
        toolbar?.setNavigationOnClickListener { finish() }

        // Respect system bars (status/navigation) similar to SettingsActivity
        val root = findViewById<android.view.View>(R.id.ladbSetupRoot) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply top margin to toolbar to account for status bar
            try {
                toolbar?.let {
                    val toolbarParams = it.layoutParams as ViewGroup.MarginLayoutParams
                    toolbarParams.topMargin = systemBars.top
                    it.layoutParams = toolbarParams
                }
            } catch (e: Exception) {
                // ignore
            }
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }
    }
}
