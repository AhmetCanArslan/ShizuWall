package com.arslan.shizuwall

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_onboarding)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.onboarding_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE)
        viewPager = findViewById(R.id.viewPager)
        val adapter = OnboardingPageAdapter(this)
        viewPager.adapter = adapter
        viewPager.isUserInputEnabled = false // Disable swipe, use buttons only
    }

    fun goToNextPage() {
        if (viewPager.currentItem < 1) {
            viewPager.currentItem += 1
        }
    }

    fun finishOnboarding() {
        sharedPreferences.edit().putBoolean(MainActivity.KEY_ONBOARDING_DONE, true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onBackPressed() {
        if (viewPager.currentItem > 0) {
            viewPager.currentItem -= 1
        } else {
            super.onBackPressed()
        }
    }
}
