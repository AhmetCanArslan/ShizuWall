package com.arslan.shizuwall.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.arslan.shizuwall.R
import com.arslan.shizuwall.security.AppLock
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch

class SecuritySettingsActivity : BaseActivity() {

    private lateinit var switchAppLock: MaterialSwitch
    private lateinit var switchBiometrics: MaterialSwitch
    private var pendingAfterVerify: (() -> Unit)? = null

    private val verifyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) pendingAfterVerify?.invoke()
        pendingAfterVerify = null
        refresh()
    }

    private val setupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, R.string.app_lock_pin_saved, Toast.LENGTH_SHORT).show()
        }
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_settings_security)

        val root = findViewById<View>(R.id.securitySettingsRoot)
        if (getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(MainActivity.KEY_USE_AMOLED_BLACK, false)
        ) {
            root.setBackgroundColor(android.graphics.Color.BLACK)
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (toolbar.layoutParams as ViewGroup.MarginLayoutParams).topMargin = bars.top
            toolbar.requestLayout()
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bars.bottom)
            insets
        }

        switchAppLock = findViewById(R.id.switchAppLock)
        switchBiometrics = findViewById(R.id.switchBiometrics)

        switchAppLock.setOnClickListener {
            if (switchAppLock.isChecked) {
                setupLauncher.launch(setupIntent())
            } else {
                requireVerification { AppLock.disable(this) }
            }
        }

        switchBiometrics.setOnClickListener {
            AppLock.setBiometricsEnabled(this, switchBiometrics.isChecked)
        }

        findViewById<LinearLayout>(R.id.rowChangePin).setOnClickListener {
            requireVerification { setupLauncher.launch(setupIntent()) }
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun setupIntent() = Intent(this, AppLockActivity::class.java)
        .putExtra(AppLockActivity.EXTRA_MODE, AppLockActivity.MODE_SETUP)

    private fun requireVerification(action: () -> Unit) {
        if (!AppLock.isEnabled(this)) {
            action()
            refresh()
            return
        }
        pendingAfterVerify = action
        verifyLauncher.launch(
            Intent(this, AppLockActivity::class.java)
                .putExtra(AppLockActivity.EXTRA_MODE, AppLockActivity.MODE_VERIFY)
        )
    }

    private fun refresh() {
        val enabled = AppLock.isEnabled(this)
        switchAppLock.isChecked = enabled
        switchBiometrics.isChecked = enabled && AppLock.biometricsEnabled(this)
        switchBiometrics.isEnabled = enabled && AppLock.biometricsAvailable(this)
        findViewById<View>(R.id.rowChangePin).isEnabled = enabled
        findViewById<View>(R.id.rowChangePin).alpha = if (enabled) 1f else 0.4f
        findViewById<View>(R.id.tvBiometricDesc).alpha = if (switchBiometrics.isEnabled) 1f else 0.4f
    }
}
