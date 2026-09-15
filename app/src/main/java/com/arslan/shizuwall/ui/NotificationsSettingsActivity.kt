package com.arslan.shizuwall.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import com.arslan.shizuwall.R
import com.arslan.shizuwall.services.AppMonitorService
import com.arslan.shizuwall.services.FloatingButtonService
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class NotificationsSettingsActivity : BaseActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var switchAppMonitor: androidx.appcompat.widget.SwitchCompat
    private lateinit var switchAutoFirewallNewApps: androidx.appcompat.widget.SwitchCompat
    private lateinit var switchFirewallStatusNotification: androidx.appcompat.widget.SwitchCompat
    private lateinit var switchFloatingButton: androidx.appcompat.widget.SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        enableEdgeToEdge()
        setContentView(R.layout.activity_settings_notifications)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupSettingsChrome(R.id.notificationsSettingsRoot, toolbar)

        switchAppMonitor = findViewById(R.id.switchAppMonitor)
        switchAutoFirewallNewApps = findViewById(R.id.switchAutoFirewallNewApps)
        switchFirewallStatusNotification = findViewById(R.id.switchFirewallStatusNotification)
        switchFloatingButton = findViewById(R.id.switchFloatingButton)
        findViewById<MaterialButton>(R.id.btnFloatingButtonSettings).setOnClickListener {
            startActivity(Intent(this, FloatingButtonSettingsActivity::class.java))
        }

        loadSettings()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        val wantEnabled = sharedPreferences.getBoolean(FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, false)
        if (wantEnabled && !Settings.canDrawOverlays(this)) {
            switchFloatingButton.isChecked = false
            sharedPreferences.edit().putBoolean(FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, false).apply()
            FloatingButtonService.stop(this)
        } else if (Settings.canDrawOverlays(this) && !switchFloatingButton.isChecked) {
            val prefEnabled = sharedPreferences.getBoolean(FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, false)
            switchFloatingButton.isChecked = prefEnabled
        }
    }

    private fun loadSettings() {
        switchAppMonitor.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_APP_MONITOR_ENABLED, false)
        switchAutoFirewallNewApps.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_AUTO_FIREWALL_NEW_APPS, false)
        switchFirewallStatusNotification.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_SHOW_FIREWALL_STATUS_NOTIFICATION, false)
        switchFloatingButton.isChecked = sharedPreferences.getBoolean(FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, false)
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val hasAsked = sharedPreferences.getBoolean("has_asked_notif", false)
                if (!hasAsked || androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.POST_NOTIFICATIONS)) {
                    sharedPreferences.edit().putBoolean("has_asked_notif", true).apply()
                    androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1002)
                } else {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    startActivity(intent)
                    Toast.makeText(this, getString(R.string.notification_permission_background_request), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupListeners() {
        switchAppMonitor.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1002)
                }
            }
            sharedPreferences.edit().putBoolean(MainActivity.KEY_APP_MONITOR_ENABLED, isChecked).apply()
            syncAppMonitorService()
        }

        switchAutoFirewallNewApps.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(MainActivity.KEY_AUTO_FIREWALL_NEW_APPS, isChecked).apply()
            setResult(RESULT_OK)
            syncAppMonitorService()
        }

        switchFirewallStatusNotification.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkAndRequestNotificationPermission()
            }
            sharedPreferences.edit().putBoolean(MainActivity.KEY_SHOW_FIREWALL_STATUS_NOTIFICATION, isChecked).apply()
            setResult(RESULT_OK)
            syncAppMonitorService()
        }

        switchFloatingButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!Settings.canDrawOverlays(this)) {
                    switchFloatingButton.isChecked = false
                    Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_LONG).show()
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                    return@setOnCheckedChangeListener
                }
                checkAndRequestNotificationPermission()
                sharedPreferences.edit().putBoolean(FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, true).apply()
                FloatingButtonService.start(this)
            } else {
                sharedPreferences.edit().putBoolean(FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, false).apply()
                FloatingButtonService.stop(this)
            }
        }

        makeCardClickableForSwitch(switchAppMonitor)
        makeCardClickableForSwitch(switchAutoFirewallNewApps)
        makeCardClickableForSwitch(switchFirewallStatusNotification)
        makeCardClickableForSwitch(switchFloatingButton)
    }

    private fun syncAppMonitorService() {
        val anyEnabled = sharedPreferences.getBoolean(MainActivity.KEY_APP_MONITOR_ENABLED, false) ||
            sharedPreferences.getBoolean(MainActivity.KEY_AUTO_FIREWALL_NEW_APPS, false) ||
            sharedPreferences.getBoolean(MainActivity.KEY_SHOW_FIREWALL_STATUS_NOTIFICATION, false)
        if (anyEnabled) {
            startForegroundService(Intent(this, AppMonitorService::class.java))
        } else {
            stopService(Intent(this, AppMonitorService::class.java))
        }
    }
}
