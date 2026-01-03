package com.arslan.shizuwall.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.arslan.shizuwall.ui.BaseActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import android.content.res.Configuration
import com.arslan.shizuwall.R
import com.arslan.shizuwall.adapters.OnboardingPageAdapter

class OnboardingActivity : BaseActivity() {

    private lateinit var viewPager: ViewPager2
    private val pages = mutableListOf<OnboardingPage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPager)

        setupPages()
        viewPager.adapter = OnboardingPageAdapter(pages, this) // use the renamed adapter class
    }

    private fun setupPages() {
        pages.add(
            OnboardingPage(
                title = getString(R.string.welcome_title),
                message = getString(R.string.welcome_message),
                buttonText = getString(R.string.next),
                onButtonClick = { goToNextPage() },
                imageResId = R.mipmap.ic_launcher // app icon
            )
        )

        pages.add(
            OnboardingPage(
                title = getString(R.string.notification_permission_title),
                message = getString(R.string.notification_permission_message),
                buttonText = getString(R.string.grant_permission),
                onButtonClick = { requestNotificationPermission() },
                isPermissionPage = true,
                imageResId = R.drawable.ic_notification
            )
        )

        pages.add(
            OnboardingPage(
                title = getString(R.string.favorites_title),
                message = getString(R.string.favorites_message),
                buttonText = getString(R.string.next),
                onButtonClick = { goToNextPage() },
                imageResId = R.drawable.ic_favorite
            )
        )

        pages.add(
            OnboardingPage(
                title = getString(R.string.app_visibility_title),
                message = getString(R.string.app_visibility_message),
                buttonText = getString(R.string.next),
                onButtonClick = { goToNextPage() },
                imageResId = R.drawable.ic_sort
            )
        )

        pages.add(
            OnboardingPage(
                title = getString(R.string.onboarding_mode_selection_title),
                message = getString(R.string.onboarding_mode_selection_message),
                buttonText = getString(R.string.use_shizuku),
                onButtonClick = { setWorkingMode("SHIZUKU") },
                secondaryButtonText = getString(R.string.use_ladb),
                onSecondaryButtonClick = { setWorkingMode("LADB") },
                isModeSelectionPage = true,
                imageResId = R.drawable.ic_settings
            )
        )

        pages.add(
            OnboardingPage(
                title = getString(R.string.shizuku_required_title),
                message = getString(R.string.shizuku_required_message),
                buttonText = getString(R.string.get_started),
                onButtonClick = { finishOnboarding() },
                imageResId = getShizukuIconRes()
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

    private fun getShizukuIconRes(): Int {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            R.drawable.ic_shizuku_white
        } else {
            R.drawable.ic_shizuku_black
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

    fun setWorkingMode(mode: String) {
        getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE)
            .edit()
            .putString(MainActivity.KEY_WORKING_MODE, mode)
            .apply()

        // Update the last page based on the selected mode
        val lastPageIndex = pages.size - 1
        if (mode == "LADB") {
            pages[lastPageIndex] = OnboardingPage(
                title = getString(R.string.working_mode_ladb),
                message = getString(R.string.ladb_onboarding_message),
                buttonText = getString(R.string.get_started),
                onButtonClick = { finishOnboarding() },
                imageResId = R.drawable.adb_24px
            )
        } else {
            pages[lastPageIndex] = OnboardingPage(
                title = getString(R.string.shizuku_required_title),
                message = getString(R.string.shizuku_required_message),
                buttonText = getString(R.string.get_started),
                onButtonClick = { finishOnboarding() },
                imageResId = getShizukuIconRes()
            )
        }
        viewPager.adapter?.notifyItemChanged(lastPageIndex)

        goToNextPage()
    }
}

data class OnboardingPage(
    val title: String,
    val message: String,
    val buttonText: String,
    val onButtonClick: () -> Unit,
    val isPermissionPage: Boolean = false,
    val isModeSelectionPage: Boolean = false,
    val secondaryButtonText: String? = null,
    val onSecondaryButtonClick: (() -> Unit)? = null,
    val imageResId: Int? = null
)
