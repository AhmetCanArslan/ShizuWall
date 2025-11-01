package com.arslan.shizuwall

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private val pages = mutableListOf<OnboardingPage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPager)

        setupPages()
        viewPager.adapter = OnboardingPagerAdapter(pages, this)
    }

    private fun setupPages() {
        pages.add(
            OnboardingPage(
                title = "Welcome to ShizuWall",
                message = "Block unwanted network connections without root or vpn",
                buttonText = "Next",
                onButtonClick = { goToNextPage() }
            )
        )

        pages.add(
            OnboardingPage(
                title = "Notification Permission",
                message = "We need notification permission to keep you informed about firewall status if you reboot while firewall is active.",
                buttonText = "Grant Permission",
                onButtonClick = { requestNotificationPermission() },
                isPermissionPage = true
            )
        )

        pages.add(
            OnboardingPage(
                title = "Shizuku Required",
                message = "Shizuku is required for this application to run. Therefore, the developer is not responsible for any negative consequences. Please install and activate Shizuku before proceeding.",
                buttonText = "Get Started",
                onButtonClick = { finishOnboarding() }
            )
        )
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, move to next page
            goToNextPage()
        } else {
            // Permission denied, still move to next page
            goToNextPage()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                    goToNextPage()
                }
                else -> {
                    // Request permission
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // No permission needed for Android 12 and below
            goToNextPage()
        }
    }

    fun goToNextPage() {
        if (viewPager.currentItem < pages.size - 1) {
            viewPager.currentItem = viewPager.currentItem + 1
        }
    }

    fun finishOnboarding() {
        // Save that onboarding is complete
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_complete", true)
            .apply()

        // Navigate to MainActivity
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

data class OnboardingPage(
    val title: String,
    val message: String,
    val buttonText: String,
    val onButtonClick: () -> Unit,
    val isPermissionPage: Boolean = false
)
