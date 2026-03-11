package com.arslan.shizuwall.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.app.ActivityManager
import android.app.NotificationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.arslan.shizuwall.shizuku.ShizukuSetupActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Typeface
import android.os.Build
import java.io.File
import androidx.appcompat.widget.SwitchCompat
import com.arslan.shizuwall.FirewallMode
import com.arslan.shizuwall.WorkingMode
import com.arslan.shizuwall.R
import com.arslan.shizuwall.ladb.LadbManager
import com.arslan.shizuwall.shell.RootShellExecutor
import com.arslan.shizuwall.shell.ShellExecutorProvider
import com.arslan.shizuwall.services.AppMonitorService
import com.arslan.shizuwall.services.ForegroundDetectionService
import com.arslan.shizuwall.services.FloatingButtonService
import com.arslan.shizuwall.utils.ShizukuPackageResolver
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku

class SettingsActivity : BaseActivity() {

    companion object {
        private const val EXPORT_SCHEMA = "shizuwall.preferences"
        private const val EXPORT_VERSION = 3
    }

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var prefs: SharedPreferences
    private lateinit var switchMoveSelectedTop: SwitchCompat
    private lateinit var switchSkipConfirm: SwitchCompat
    private lateinit var switchSkipErrorDialog: SwitchCompat
    private lateinit var cardKeepErrorApps: com.google.android.material.card.MaterialCardView
    private lateinit var layoutKeepErrorApps: LinearLayout
    private lateinit var switchKeepErrorAppsSelected: SwitchCompat
    private lateinit var layoutChangeFont: LinearLayout
    private lateinit var tvCurrentFont: TextView
    private lateinit var layoutChangeLanguage: LinearLayout
    private lateinit var tvCurrentLanguage: TextView
    private lateinit var btnExport: LinearLayout
    private lateinit var btnImport: LinearLayout
    private lateinit var btnDonate: LinearLayout
    private lateinit var btnGithub: LinearLayout
    private lateinit var tvVersion: TextView
    private lateinit var switchUseDynamicColor: SwitchCompat
    private lateinit var switchAutoEnableOnShizukuStart: SwitchCompat
    private lateinit var cardAutoEnableOnShizukuStart: com.google.android.material.card.MaterialCardView
    private lateinit var switchApplyRootRulesAfterReboot: SwitchCompat
    private lateinit var cardApplyRootRulesAfterReboot: com.google.android.material.card.MaterialCardView
    private lateinit var switchAppMonitor: SwitchCompat
    private lateinit var switchFloatingButton: SwitchCompat

    private lateinit var cardAdbBroadcastUsage: com.google.android.material.card.MaterialCardView
    private lateinit var layoutAdbBroadcastUsage: LinearLayout // new
    private lateinit var radioGroupWorkingMode: RadioGroup
    private lateinit var radioShizukuMode: RadioButton
    private lateinit var radioLadbMode: RadioButton
    private lateinit var radioRootMode: RadioButton
    private lateinit var cardSetLadb: com.google.android.material.card.MaterialCardView
    private lateinit var layoutSetLadb: LinearLayout
    private lateinit var cardSkipConfirm: com.google.android.material.card.MaterialCardView
    private var autoEnablePreviousState: Boolean = false  // Store previous state before disabling
    private var rootReapplyPreviousState: Boolean = false
    private var suppressWorkingModeListener = false
    
    // Firewall Mode Selector
    private lateinit var radioGroupFirewallMode: RadioGroup
    private lateinit var radioModeDefault: RadioButton
    private lateinit var radioModeAdaptive: RadioButton
    private lateinit var radioModeSmartForeground: RadioButton
    private lateinit var radioModeWhitelist: RadioButton
    private lateinit var tvSmartForegroundWarning: TextView
    private lateinit var tvFirewallModeDisabledWarning: TextView

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { lifecycleScope.launch { exportToUri(it) } }
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uris: Uri? ->
        uris?.let { lifecycleScope.launch { importFromUri(it) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        toolbar.menu.add(getString(R.string.reset_app_menu)).setOnMenuItemClickListener {
            showResetConfirmationDialog()
            true
        }

        // Add Shizuku Setup Guide to overflow menu
        toolbar.menu.add(getString(R.string.shizuku_setup_guide)).setOnMenuItemClickListener {
            startActivity(android.content.Intent(this, ShizukuSetupActivity::class.java))
            true
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply top margin to toolbar to account for status bar
            val toolbarParams = toolbar.layoutParams as ViewGroup.MarginLayoutParams
            toolbarParams.topMargin = systemBars.top
            toolbar.layoutParams = toolbarParams
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)

            insets
        }

        initializeViews()
        loadSettings()
        setupListeners()

        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        tvVersion.text = getString(R.string.version_format, packageInfo.versionName)
    }

    override fun onResume() {
        super.onResume()
        // Re-check accessibility state when returning from system settings
        val mode = FirewallMode.fromName(sharedPreferences.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name))
        if (::tvSmartForegroundWarning.isInitialized) {
            updateFirewallModeUI(mode)
            // Also update firewall mode selector state based on firewall enabled status
            val isFirewallEnabled = sharedPreferences.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
            updateFirewallModeSelectorState(isFirewallEnabled)
        }

        // Re-check overlay permission — auto-enable floating button if user just granted it
        if (::switchFloatingButton.isInitialized) {
            val wantEnabled = sharedPreferences.getBoolean(
                com.arslan.shizuwall.services.FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, false
            )
            if (wantEnabled && !Settings.canDrawOverlays(this)) {
                // Permission was revoked while we were open — turn off
                switchFloatingButton.isChecked = false
                sharedPreferences.edit().putBoolean(
                    com.arslan.shizuwall.services.FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, false
                ).apply()
                com.arslan.shizuwall.services.FloatingButtonService.stop(this)
            } else if (Settings.canDrawOverlays(this) && !switchFloatingButton.isChecked) {
                // User might have just granted permission — keep switch in sync with pref
                val prefEnabled = sharedPreferences.getBoolean(
                    com.arslan.shizuwall.services.FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, false
                )
                switchFloatingButton.isChecked = prefEnabled
            }
        }
    }

    private fun initializeViews() {
        switchMoveSelectedTop = findViewById(R.id.switchMoveSelectedTop)
        cardSkipConfirm = findViewById(R.id.cardSkipConfirm)
        switchSkipConfirm = findViewById(R.id.switchSkipConfirm)
        switchSkipErrorDialog = findViewById(R.id.switchSkipErrorDialog)
        cardKeepErrorApps = findViewById(R.id.cardKeepErrorApps)
        layoutKeepErrorApps = findViewById(R.id.layoutKeepErrorApps)
        switchKeepErrorAppsSelected = findViewById(R.id.switchKeepErrorAppsSelected)
        layoutChangeFont = findViewById(R.id.layoutChangeFont)
        tvCurrentFont = findViewById(R.id.tvCurrentFont)
        layoutChangeLanguage = findViewById(R.id.layoutChangeLanguage)
        tvCurrentLanguage = findViewById(R.id.tvCurrentLanguage)
        btnExport = findViewById(R.id.btnExport)
        btnImport = findViewById(R.id.btnImport)
        btnDonate = findViewById(R.id.btnDonate)
        btnGithub = findViewById(R.id.btnGithub)
        tvVersion = findViewById(R.id.tvVersion)
        switchUseDynamicColor = findViewById(R.id.switchUseDynamicColor)

        // new: bind XML item
        cardAdbBroadcastUsage = findViewById(R.id.cardAdbBroadcastUsage)
        layoutAdbBroadcastUsage = findViewById(R.id.layoutAdbBroadcastUsage)
        // Working mode controls
        radioGroupWorkingMode = findViewById(R.id.radioGroupWorkingMode)
        radioShizukuMode = findViewById(R.id.radioShizukuMode)
        radioLadbMode = findViewById(R.id.radioLadbMode)
        radioRootMode = findViewById(R.id.radioRootMode)
        cardSetLadb = findViewById(R.id.cardSetLadb)
        layoutSetLadb = findViewById(R.id.layoutSetLadb)
        switchAppMonitor = findViewById(R.id.switchAppMonitor)
        switchFloatingButton = findViewById(R.id.switchFloatingButton)
        // Auto-enable switch (new)
        switchAutoEnableOnShizukuStart = findViewById(R.id.switchAutoEnableOnShizukuStart)
        cardAutoEnableOnShizukuStart = findViewById(R.id.cardAutoEnableOnShizukuStart)
        switchApplyRootRulesAfterReboot = findViewById(R.id.switchApplyRootRulesAfterReboot)
        cardApplyRootRulesAfterReboot = findViewById(R.id.cardApplyRootRulesAfterReboot)
        
        // Firewall Mode Selector
        radioGroupFirewallMode = findViewById(R.id.radioGroupFirewallMode)
        radioModeDefault = findViewById(R.id.radioModeDefault)
        radioModeAdaptive = findViewById(R.id.radioModeAdaptive)
        radioModeSmartForeground = findViewById(R.id.radioModeSmartForeground)
        radioModeWhitelist = findViewById(R.id.radioModeWhitelist)
        tvSmartForegroundWarning = findViewById(R.id.tvSmartForegroundWarning)
        tvFirewallModeDisabledWarning = findViewById(R.id.tvFirewallModeDisabledWarning)
    }

    private fun loadSettings() {
        prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        switchMoveSelectedTop.isChecked = prefs.getBoolean(MainActivity.KEY_MOVE_SELECTED_TOP, true)
        switchSkipConfirm.isChecked = prefs.getBoolean("skip_enable_confirm", false)
        switchSkipErrorDialog.isChecked = prefs.getBoolean(MainActivity.KEY_SKIP_ERROR_DIALOG, false)
        switchKeepErrorAppsSelected.isChecked = prefs.getBoolean(MainActivity.KEY_KEEP_ERROR_APPS_SELECTED, false)
        
        // Show/hide keep error apps option based on skip error dialog state
        cardKeepErrorApps.visibility = if (switchSkipErrorDialog.isChecked) View.VISIBLE else View.GONE

        // Load firewall mode (migrate from old adaptive mode if needed)
        migrateAdaptiveModeToFirewallMode(prefs)
        val firewallMode = FirewallMode.fromName(prefs.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name))
        
        // Set the radio button based on mode
        when (firewallMode) {
            FirewallMode.ADAPTIVE -> radioGroupFirewallMode.check(R.id.radioModeAdaptive)
            FirewallMode.SMART_FOREGROUND -> radioGroupFirewallMode.check(R.id.radioModeSmartForeground)
            FirewallMode.WHITELIST -> radioGroupFirewallMode.check(R.id.radioModeWhitelist)
            else -> radioGroupFirewallMode.check(R.id.radioModeDefault)
        }
        
        // Update UI based on mode
        updateFirewallModeUI(firewallMode)
        
        // Update firewall mode selector state based on whether firewall is enabled
        val isFirewallEnabled = prefs.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
        updateFirewallModeSelectorState(isFirewallEnabled)

        val currentFont = prefs.getString(MainActivity.KEY_SELECTED_FONT, "default") ?: "default"
        tvCurrentFont.text = if (currentFont == "ndot") getString(R.string.font_ndot) else getString(R.string.font_default)
        updateCurrentLanguageDisplay()
        switchUseDynamicColor.isChecked = prefs.getBoolean(MainActivity.KEY_USE_DYNAMIC_COLOR, true)
        switchAutoEnableOnShizukuStart.isChecked = prefs.getBoolean(MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START, false)
        switchApplyRootRulesAfterReboot.isChecked = prefs.getBoolean(MainActivity.KEY_APPLY_ROOT_RULES_AFTER_REBOOT, false)
        switchAppMonitor.isChecked = prefs.getBoolean(MainActivity.KEY_APP_MONITOR_ENABLED, false)
        switchFloatingButton.isChecked = prefs.getBoolean(
            com.arslan.shizuwall.services.FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, false
        )

        // Load working mode
        val workingModeName = prefs.getString(MainActivity.KEY_WORKING_MODE, WorkingMode.SHIZUKU.name)
        val workingMode = WorkingMode.fromName(workingModeName)
        when (workingMode) {
            WorkingMode.LADB -> radioGroupWorkingMode.check(R.id.radioLadbMode)
            WorkingMode.ROOT -> radioGroupWorkingMode.check(R.id.radioRootMode)
            else -> radioGroupWorkingMode.check(R.id.radioShizukuMode)
        }

        if (com.arslan.shizuwall.BuildConfig.FLAVOR == "fdroid") {
            radioLadbMode.isEnabled = false
            radioLadbMode.alpha = 0.5f
            val notSupportedText = getString(R.string.ladb_not_supported_fdroid)
            if (!radioLadbMode.text.toString().contains(notSupportedText)) {
                radioLadbMode.text = "${radioLadbMode.text}$notSupportedText"
            }
            if (radioLadbMode.isChecked) {
                radioGroupWorkingMode.check(R.id.radioShizukuMode)
                prefs.edit().putString(MainActivity.KEY_WORKING_MODE, WorkingMode.SHIZUKU.name).apply()
            }
        }

        updateWorkingModeDependentUi(workingMode, restoreAutoEnable = false)
    }
    
    /**
     * Migrate from old adaptive mode boolean to new firewall mode enum
     */
    private fun migrateAdaptiveModeToFirewallMode(prefs: SharedPreferences) {
        // Check if we have the old adaptive mode key but not the new firewall mode key
        if (prefs.contains(MainActivity.KEY_ADAPTIVE_MODE) && !prefs.contains(MainActivity.KEY_FIREWALL_MODE)) {
            val adaptiveMode = prefs.getBoolean(MainActivity.KEY_ADAPTIVE_MODE, false)
            val newMode = if (adaptiveMode) FirewallMode.ADAPTIVE else FirewallMode.DEFAULT
            prefs.edit()
                .putString(MainActivity.KEY_FIREWALL_MODE, newMode.name)
                .remove(MainActivity.KEY_ADAPTIVE_MODE)
                .apply()
        }
    }
    
    /**
     * Update UI elements based on firewall mode
     */
    private fun updateFirewallModeUI(mode: FirewallMode) {
        // Show/hide skip confirm card based on mode
        val showSkipConfirm = mode == FirewallMode.DEFAULT
        cardSkipConfirm.visibility = if (showSkipConfirm) View.VISIBLE else View.GONE
        
        // If adaptive or smart foreground, force skip confirm to ON
        if (mode != FirewallMode.DEFAULT && !switchSkipConfirm.isChecked) {
            switchSkipConfirm.isChecked = true
            sharedPreferences.edit().putBoolean("skip_enable_confirm", true).apply()
        }
        
        // Show warning for Smart Foreground if accessibility not enabled
        if (mode == FirewallMode.SMART_FOREGROUND) {
            val accessibilityEnabled = ForegroundDetectionService.isServiceEnabled(this)
            tvSmartForegroundWarning.visibility = if (!accessibilityEnabled) View.VISIBLE else View.GONE
        } else {
            tvSmartForegroundWarning.visibility = View.GONE
        }
    }

    /**
     * Update firewall mode selector state based on whether firewall is enabled.
     * When firewall is enabled, disable mode changes and show warning.
     */
    private fun updateFirewallModeSelectorState(isFirewallEnabled: Boolean) {
        radioGroupFirewallMode.isEnabled = !isFirewallEnabled
        radioModeDefault.isEnabled = !isFirewallEnabled
        radioModeAdaptive.isEnabled = !isFirewallEnabled
        radioModeSmartForeground.isEnabled = !isFirewallEnabled
        radioModeWhitelist.isEnabled = !isFirewallEnabled
        
        // Update alpha for visual feedback
        val targetAlpha = if (isFirewallEnabled) 0.5f else 1f
        radioGroupFirewallMode.alpha = targetAlpha
        
        // Show/hide warning message
        tvFirewallModeDisabledWarning.visibility = if (isFirewallEnabled) View.VISIBLE else View.GONE
    }

    private fun setupListeners() {

        switchMoveSelectedTop.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_MOVE_SELECTED_TOP, isChecked).apply()
            setResult(RESULT_OK)
        }

        // Firewall Mode Selector Listener
        radioGroupFirewallMode.setOnCheckedChangeListener { _, checkedId ->
            // Check if firewall is enabled - if so, prevent mode change
            val isFirewallEnabled = sharedPreferences.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
            if (isFirewallEnabled) {
                // Show toast message and revert selection
                Toast.makeText(this, R.string.firewall_mode_change_disabled, Toast.LENGTH_LONG).show()
                
                // Revert to current mode
                val currentMode = FirewallMode.fromName(sharedPreferences.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name))
                when (currentMode) {
                    FirewallMode.ADAPTIVE -> radioGroupFirewallMode.check(R.id.radioModeAdaptive)
                    FirewallMode.SMART_FOREGROUND -> radioGroupFirewallMode.check(R.id.radioModeSmartForeground)
                    FirewallMode.WHITELIST -> radioGroupFirewallMode.check(R.id.radioModeWhitelist)
                    else -> radioGroupFirewallMode.check(R.id.radioModeDefault)
                }
                return@setOnCheckedChangeListener
            }
            
            val newMode = when (checkedId) {
                R.id.radioModeAdaptive -> FirewallMode.ADAPTIVE
                R.id.radioModeSmartForeground -> FirewallMode.SMART_FOREGROUND
                R.id.radioModeWhitelist -> FirewallMode.WHITELIST
                else -> FirewallMode.DEFAULT
            }
            
            prefs.edit().putString(MainActivity.KEY_FIREWALL_MODE, newMode.name).apply()
            setResult(RESULT_OK)
            
            TransitionManager.beginDelayedTransition(findViewById(R.id.settingsRoot), AutoTransition())
            updateFirewallModeUI(newMode)
            
            // Handle accessibility for Smart Foreground mode
            if (newMode == FirewallMode.SMART_FOREGROUND) {
                if (!ForegroundDetectionService.isServiceEnabled(this)) {
                    // Check dialog status
                    val dialogShown = sharedPreferences.getBoolean("accessibility_dialog_shown", false)
                    val dialogAccepted = sharedPreferences.getBoolean("accessibility_dialog_accepted", false)
                    
                    if (!dialogShown || !dialogAccepted) {
                        // Show permission dialog
                        showAccessibilityPermissionDialog()
                    } else {
                        // Previously accepted, try to auto-enable again
                        lifecycleScope.launch {
                            val success = ForegroundDetectionService.enableServiceViaShell(this@SettingsActivity)
                            if (success) {
                                updateFirewallModeUI(newMode)
                            }
                        }
                    }
                }
            }
            
            // Show info dialog for Whitelist mode
            if (newMode == FirewallMode.WHITELIST) {
                showWhitelistInfoDialog()
            }
        }

        switchSkipConfirm.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("skip_enable_confirm", isChecked).apply()
        }

        switchSkipErrorDialog.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_SKIP_ERROR_DIALOG, isChecked).apply()
            
            TransitionManager.beginDelayedTransition(findViewById(R.id.settingsRoot), AutoTransition())
            // Show/hide the keep error apps option
            cardKeepErrorApps.visibility = if (isChecked) View.VISIBLE else View.GONE
            
            // If disabling skip error dialog, also disable keep error apps selected
            if (!isChecked) {
                switchKeepErrorAppsSelected.isChecked = false
                prefs.edit().putBoolean(MainActivity.KEY_KEEP_ERROR_APPS_SELECTED, false).apply()
            }
        }

        switchKeepErrorAppsSelected.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_KEEP_ERROR_APPS_SELECTED, isChecked).apply()
        }

        layoutChangeFont.setOnClickListener {
            showFontSelectorDialog()
        }

        layoutChangeLanguage.setOnClickListener {
            showLanguageSelectorDialog()
        }

        btnExport.setOnClickListener {
            createDocumentLauncher.launch("shizuwall_settings")
        }

        btnImport.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
        }

        btnDonate.setOnClickListener {
            val url = getString(R.string.buymeacoffee_url)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        btnGithub.setOnClickListener {
            val url = getString(R.string.github_url)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        switchUseDynamicColor.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit()
                .putBoolean(MainActivity.KEY_USE_DYNAMIC_COLOR, isChecked)
                .apply()
            recreateWithAnimation()
        }

        switchAutoEnableOnShizukuStart.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START, isChecked).apply()
            setResult(RESULT_OK)
        }

        switchApplyRootRulesAfterReboot.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_APPLY_ROOT_RULES_AFTER_REBOOT, isChecked).apply()
            setResult(RESULT_OK)
        }

        switchAppMonitor.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1002)
                }
            }
            prefs.edit().putBoolean(MainActivity.KEY_APP_MONITOR_ENABLED, isChecked).apply()
            val intent = Intent(this, AppMonitorService::class.java)
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } else {
                stopService(intent)
            }
        }

        radioGroupWorkingMode.setOnCheckedChangeListener { _, checkedId ->
            if (suppressWorkingModeListener) return@setOnCheckedChangeListener

            val currentMode = WorkingMode.fromName(prefs.getString(MainActivity.KEY_WORKING_MODE, WorkingMode.SHIZUKU.name))
            val mode = when (checkedId) {
                R.id.radioLadbMode -> WorkingMode.LADB
                R.id.radioRootMode -> WorkingMode.ROOT
                else -> WorkingMode.SHIZUKU
            }
            if (mode == currentMode) return@setOnCheckedChangeListener

            if (mode == WorkingMode.ROOT && !RootShellExecutor.hasRootAccess()) {
                showRootNotFoundDialog()
                suppressWorkingModeListener = true
                when (currentMode) {
                    WorkingMode.LADB -> radioGroupWorkingMode.check(R.id.radioLadbMode)
                    WorkingMode.ROOT -> radioGroupWorkingMode.check(R.id.radioRootMode)
                    else -> radioGroupWorkingMode.check(R.id.radioShizukuMode)
                }
                suppressWorkingModeListener = false
                return@setOnCheckedChangeListener
            }

            prefs.edit().putString(MainActivity.KEY_WORKING_MODE, mode.name).apply()
            setResult(RESULT_OK)
            
            TransitionManager.beginDelayedTransition(findViewById(R.id.settingsRoot), AutoTransition())
            updateWorkingModeDependentUi(mode, restoreAutoEnable = true)
        }

        layoutSetLadb.setOnClickListener {
            // Only allow opening when LADB mode is selected
            if (!radioLadbMode.isChecked) {
                Toast.makeText(this, getString(R.string.working_mode_select_ladb_prompt), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val intent = android.content.Intent(this, com.arslan.shizuwall.LadbSetupActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.open_ladb_setup), Toast.LENGTH_SHORT).show()
            }
        }

        switchFloatingButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Check overlay permission
                if (!Settings.canDrawOverlays(this)) {
                    // Reset switch — will be restored in onResume if user grants permission
                    switchFloatingButton.isChecked = false
                    Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_LONG).show()
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                    return@setOnCheckedChangeListener
                }
                prefs.edit().putBoolean(
                    com.arslan.shizuwall.services.FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, true
                ).apply()
                com.arslan.shizuwall.services.FloatingButtonService.start(this)
            } else {
                prefs.edit().putBoolean(
                    com.arslan.shizuwall.services.FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, false
                ).apply()
                com.arslan.shizuwall.services.FloatingButtonService.stop(this)
            }
        }

        layoutAdbBroadcastUsage.setOnClickListener { showAdbBroadcastDialog() }

        // Make the whole card area toggle the corresponding switches when tapped
        makeCardClickableForSwitch(switchMoveSelectedTop)
        makeCardClickableForSwitch(switchUseDynamicColor)
        makeCardClickableForSwitch(switchSkipConfirm)
        makeCardClickableForSwitch(switchSkipErrorDialog)
        makeCardClickableForSwitch(switchKeepErrorAppsSelected)
        makeCardClickableForSwitch(switchAutoEnableOnShizukuStart)
        makeCardClickableForSwitch(switchApplyRootRulesAfterReboot)
        makeCardClickableForSwitch(switchAppMonitor)
        makeCardClickableForSwitch(switchFloatingButton)
    }
    

    /**
     * Show info dialog about Smart Foreground mode.
     * @param accessibilityGranted true if accessibility was already enabled or just auto-granted
     */
    private fun showSmartForegroundInfoDialog(accessibilityGranted: Boolean) {
        val showPrompt = sharedPreferences.getBoolean("show_smart_foreground_prompt", true)
        if (!showPrompt) return

        val message = getString(R.string.smart_foreground_info_enabled)
        
        val promptView = layoutInflater.inflate(R.layout.dialog_shizuku_prompt, null)
        val messageText: TextView = promptView.findViewById(R.id.shizuku_prompt_message_text)
        val checkbox: android.widget.CheckBox = promptView.findViewById(R.id.shizuku_prompt_do_not_show)
        
        messageText.text = message
        checkbox.text = getString(R.string.dont_show_again)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.firewall_mode_smart_foreground)
            .setView(promptView)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (checkbox.isChecked) {
                    sharedPreferences.edit().putBoolean("show_smart_foreground_prompt", false).apply()
                }
            }
            .show()
    }

    private fun showRootNotFoundDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.working_mode_root)
            .setMessage(R.string.root_not_found_message)
            .setPositiveButton(R.string.ok, null)
            .setCancelable(true)
            .show()
    }

    private fun updateWorkingModeDependentUi(mode: WorkingMode, restoreAutoEnable: Boolean) {
        val isLadb = mode == WorkingMode.LADB
        val isShizuku = mode == WorkingMode.SHIZUKU
        val isRoot = mode == WorkingMode.ROOT

        cardSetLadb.visibility = if (isLadb) View.VISIBLE else View.GONE
        cardAutoEnableOnShizukuStart.visibility = if (isShizuku) View.VISIBLE else View.GONE
        switchAutoEnableOnShizukuStart.isEnabled = isShizuku
        cardApplyRootRulesAfterReboot.visibility = if (isRoot) View.VISIBLE else View.GONE
        switchApplyRootRulesAfterReboot.isEnabled = isRoot

        if (!isShizuku) {
            autoEnablePreviousState = switchAutoEnableOnShizukuStart.isChecked
            if (switchAutoEnableOnShizukuStart.isChecked) {
                switchAutoEnableOnShizukuStart.isChecked = false
                prefs.edit().putBoolean(MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START, false).apply()
            }
        } else if (restoreAutoEnable && autoEnablePreviousState) {
            switchAutoEnableOnShizukuStart.isChecked = true
            prefs.edit().putBoolean(MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START, true).apply()
        }

        if (!isRoot) {
            rootReapplyPreviousState = switchApplyRootRulesAfterReboot.isChecked
            if (switchApplyRootRulesAfterReboot.isChecked) {
                switchApplyRootRulesAfterReboot.isChecked = false
                prefs.edit().putBoolean(MainActivity.KEY_APPLY_ROOT_RULES_AFTER_REBOOT, false).apply()
            }
        } else if (restoreAutoEnable && rootReapplyPreviousState) {
            switchApplyRootRulesAfterReboot.isChecked = true
            prefs.edit().putBoolean(MainActivity.KEY_APPLY_ROOT_RULES_AFTER_REBOOT, true).apply()
        }
    }

    private fun showAccessibilityPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.accessibility_permission_title)
            .setMessage(R.string.accessibility_permission_message)
            .setCancelable(true)
            .setPositiveButton(R.string.accept) { _, _ ->
                // Mark as shown and accepted
                sharedPreferences.edit()
                    .putBoolean("accessibility_dialog_shown", true)
                    .putBoolean("accessibility_dialog_accepted", true)
                    .apply()
                // Open accessibility settings
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton(R.string.decline) { _, _ ->
                // Mark as shown and declined
                sharedPreferences.edit()
                    .putBoolean("accessibility_dialog_shown", true)
                    .putBoolean("accessibility_dialog_accepted", false)
                    .apply()
                // Revert to default mode
                revertToDefaultMode()
            }
            .setOnCancelListener {
                // Mark as shown and declined if dismissed
                sharedPreferences.edit()
                    .putBoolean("accessibility_dialog_shown", true)
                    .putBoolean("accessibility_dialog_accepted", false)
                    .apply()
                // Revert to default mode if dialog is dismissed
                revertToDefaultMode()
            }
            .show()
    }

    private fun revertToDefaultMode() {
        prefs.edit().putString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name).apply()
        radioGroupFirewallMode.check(R.id.radioModeDefault)
        updateFirewallModeUI(FirewallMode.DEFAULT)
    }

    /**
     * Show info dialog about Whitelist mode and system apps behavior.
     * Uses the same dialog_shizuku_prompt layout with a "don't show again" checkbox.
     */
    private fun showWhitelistInfoDialog() {
        val showPrompt = sharedPreferences.getBoolean("show_whitelist_prompt", true)
        if (!showPrompt) return

        val promptView = layoutInflater.inflate(R.layout.dialog_shizuku_prompt, null)
        val messageText: TextView = promptView.findViewById(R.id.shizuku_prompt_message_text)
        val checkbox: android.widget.CheckBox = promptView.findViewById(R.id.shizuku_prompt_do_not_show)

        messageText.text = getString(R.string.whitelist_info_system_apps_warning)
        checkbox.text = getString(R.string.dont_show_again)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.firewall_mode_whitelist)
            .setView(promptView)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (checkbox.isChecked) {
                    sharedPreferences.edit().putBoolean("show_whitelist_prompt", false).apply()
                }
            }
            .show()
    }

    private fun buildSupportedLocales(): LinkedHashMap<String, String> {
        val map = linkedMapOf("" to getString(R.string.language_system_default))
        try {
            val parser = resources.getXml(R.xml.locales_config)
            var eventType = parser.eventType
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "locale") {
                    val tag = parser.getAttributeValue(
                        "http://schemas.android.com/apk/res/android", "name"
                    )
                    if (!tag.isNullOrEmpty()) {
                        val locale = java.util.Locale.forLanguageTag(tag)
                        val displayName = locale.getDisplayName(locale)
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        map[tag] = displayName.ifEmpty { tag }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            map["en"] = "English"
        }
        return map
    }

    private fun updateCurrentLanguageDisplay() {
        val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        if (currentLocales.isEmpty) {
            tvCurrentLanguage.text = getString(R.string.language_system_default)
        } else {
            val tag = currentLocales.get(0)?.toLanguageTag() ?: ""
            val locale = java.util.Locale.forLanguageTag(tag)
            tvCurrentLanguage.text = locale.getDisplayName(locale)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                .ifEmpty { getString(R.string.language_system_default) }
        }
    }

    private fun showLanguageSelectorDialog() {
        val supportedLocales = buildSupportedLocales()
        val localeKeys = supportedLocales.keys.toList()
        val localeNames = supportedLocales.values.toTypedArray()

        val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        val currentTag = if (currentLocales.isEmpty) "" else (currentLocales.get(0)?.toLanguageTag() ?: "")
        val checkedItem = localeKeys.indexOf(currentTag).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.language)
            .setSingleChoiceItems(localeNames, checkedItem) { dialog, which ->
                val selectedTag = localeKeys[which]
                val newLocales = if (selectedTag.isEmpty()) {
                    androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                } else {
                    androidx.core.os.LocaleListCompat.forLanguageTags(selectedTag)
                }
                dialog.dismiss()
                // Fade out, then apply locale (which triggers recreate with fade in)
                val rootView = findViewById<android.view.View>(android.R.id.content)
                if (rootView != null) {
                    rootView.animate()
                        .alpha(0f)
                        .setDuration(400)
                        .withEndAction {
                            BaseActivity.requestFadeInAnimation()
                            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(newLocales)
                        }
                        .start()
                } else {
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(newLocales)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

   private fun makeCardClickableForSwitch(switch: SwitchCompat) {
        try {
            val parent = switch.parent as? View ?: return

            // Apply ripple/selectable background so the row gives visual feedback when tapped
            val typedValue = TypedValue()
            if (theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)) {
                parent.setBackgroundResource(typedValue.resourceId)
            }

            parent.isClickable = true
            parent.isFocusable = true

            parent.setOnClickListener {
                // Only toggle if the switch is enabled (respect dependencies)
                if (switch.isEnabled) {
                    // Flip checked state; this will trigger the switch's change listener
                    switch.isChecked = !switch.isChecked
                }
            }
        } catch (e: Exception) {
            // Don't crash if layout assumptions differ; silently ignore
        }
    }

    private fun showFontSelectorDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_font_selector, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Apply font to dialog views

        val radioGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.fontRadioGroup)
        val btnApply = dialogView.findViewById<MaterialButton>(R.id.btnApplyFont)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelFont)

        val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val currentFont = prefs.getString(MainActivity.KEY_SELECTED_FONT, "default") ?: "default"
        when (currentFont) {
            "ndot" -> radioGroup.check(R.id.radio_ndot)
            else -> radioGroup.check(R.id.radio_default)
        }

        btnApply.setOnClickListener {
            val selectedId = radioGroup.checkedRadioButtonId
            val fontKey = when (selectedId) {
                R.id.radio_ndot -> "ndot"
                else -> "default"
            }

            prefs.edit().putString(MainActivity.KEY_SELECTED_FONT, fontKey).apply()
            tvCurrentFont.text = if (fontKey == "ndot") getString(R.string.font_ndot) else getString(R.string.font_default)

            dialog.dismiss()
            recreateWithAnimation()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private suspend fun exportToUri(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val files = JSONObject().apply {
                    put(
                        MainActivity.PREF_NAME,
                        exportSharedPreferences(
                            MainActivity.PREF_NAME,
                            setOf(
                                MainActivity.KEY_FIREWALL_ENABLED,
                                MainActivity.KEY_ACTIVE_PACKAGES,
                                MainActivity.KEY_FIREWALL_SAVED_ELAPSED,
                                MainActivity.KEY_FIREWALL_UPDATE_TS,
                                MainActivity.KEY_SMART_FOREGROUND_APP
                            )
                        )
                    )
                    put("app_prefs", exportSharedPreferences("app_prefs"))
                    put(LadbManager.PREFS_NAME, exportSharedPreferences(LadbManager.PREFS_NAME))
                    put("daemon_prefs", exportSharedPreferences("daemon_prefs"))
                }

                val exportJson = JSONObject().apply {
                    put("schema", EXPORT_SCHEMA)
                    put("version", EXPORT_VERSION)
                    put("exported_at", System.currentTimeMillis())
                    put("files", files)
                }

                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(exportJson.toString(2).toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: throw IllegalStateException("Unable to open output stream")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.export_successful), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.export_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun importFromUri(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val content = contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                } ?: throw IllegalStateException("Unable to open input stream")

                val obj = JSONObject(content)
                if (obj.optString("schema") == EXPORT_SCHEMA && obj.has("files")) {
                    val version = obj.optInt("version", 1)
                    if (version <= EXPORT_VERSION) {
                        if (!importStructuredBackup(obj)) {
                            android.util.Log.w("SettingsActivity", "Structured import completed with warnings")
                        }
                    } else {
                        // Fall back to legacy import for unknown future versions
                        importLegacyBackup(obj)
                    }
                } else {
                    importLegacyBackup(obj)
                }

                val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                sanitizeSelectedApps(prefs)

                // 5. Update Runtime State (App Monitor Service)
                val isMonitorEnabled = prefs.getBoolean(MainActivity.KEY_APP_MONITOR_ENABLED, false)
                val monitorIntent = Intent(this@SettingsActivity, AppMonitorService::class.java)
                try {
                    if (isMonitorEnabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(monitorIntent)
                        } else {
                            startService(monitorIntent)
                        }
                    } else {
                        stopService(monitorIntent)
                    }
                } catch (e: Exception) {
                    // Ignore service start failures during import
                }

                // 6. Mark onboarding as complete
                getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                    .putBoolean("onboarding_complete", true)
                    .apply()

                withContext(Dispatchers.Main) {
                    loadSettings()
                    setResult(RESULT_OK)
                    Toast.makeText(this@SettingsActivity, getString(R.string.import_successful), Toast.LENGTH_SHORT).show()
                    recreateWithAnimation()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.import_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportSharedPreferences(
        prefsName: String,
        excludedKeys: Set<String> = emptySet()
    ): JSONObject {
        val prefsMap = getSharedPreferences(prefsName, Context.MODE_PRIVATE).all
        val entries = JSONArray()

        for (key in prefsMap.keys.sorted()) {
            if (key in excludedKeys) continue
            val value = prefsMap[key] ?: continue
            val entry = JSONObject().put("key", key)

            when (value) {
                is Boolean -> {
                    entry.put("type", "boolean")
                    entry.put("value", value)
                }
                is String -> {
                    entry.put("type", "string")
                    entry.put("value", value)
                }
                is Int -> {
                    entry.put("type", "int")
                    entry.put("value", value)
                }
                is Long -> {
                    entry.put("type", "long")
                    entry.put("value", value)
                }
                is Float -> {
                    entry.put("type", "float")
                    entry.put("value", value.toDouble())
                }
                is Set<*> -> {
                    val stringSet = value.filterIsInstance<String>().toSet()
                    val arr = JSONArray()
                    stringSet.sorted().forEach { arr.put(it) }
                    entry.put("type", "string_set")
                    entry.put("value", arr)
                }
                else -> continue
            }

            entries.put(entry)
        }

        return JSONObject().put("entries", entries)
    }

    private fun importStructuredBackup(root: JSONObject): Boolean {
        val files = root.optJSONObject("files")
        if (files == null) {
            android.util.Log.w("SettingsActivity", "Import failed: missing files object")
            return false
        }
        val fileNames = files.keys()

        var success = false
        while (fileNames.hasNext()) {
            val prefsName = fileNames.next()
            val fileObj = files.optJSONObject(prefsName)
            if (fileObj == null) {
                android.util.Log.w("SettingsActivity", "Import warning: missing object for $prefsName")
                continue
            }
            val entries = fileObj.optJSONArray("entries")
            if (entries == null) {
                android.util.Log.w("SettingsActivity", "Import warning: missing entries for $prefsName")
                continue
            }

            val editor = getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                applyPreferenceEntry(editor, entry)
            }
            editor.apply()
            success = true
        }
        return success
    }

    private fun importLegacyBackup(obj: JSONObject) {
        val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        // Legacy selected/favorites arrays.
        val selectedKey = if (obj.has("selected")) "selected" else MainActivity.KEY_SELECTED_APPS
        val selectedJson = obj.optJSONArray(selectedKey)
        if (selectedJson != null) {
            editor.putStringSet(MainActivity.KEY_SELECTED_APPS, jsonArrayToStringSet(selectedJson))
        }

        val favoritesKey = if (obj.has("favorites")) "favorites" else MainActivity.KEY_FAVORITE_APPS
        val favoritesJson = obj.optJSONArray(favoritesKey)
        if (favoritesJson != null) {
            editor.putStringSet(MainActivity.KEY_FAVORITE_APPS, jsonArrayToStringSet(favoritesJson))
        }

        val reserved = setOf(
            "schema",
            "files",
            "version",
            "exported_at",
            "selected",
            "favorites",
            "ladb_config"
        )

        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key in reserved) continue

            val value = obj.opt(key)
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
                is JSONArray -> editor.putStringSet(key, jsonArrayToStringSet(value))
            }
        }

        editor.apply()

        // Legacy LADB section
        val ladbObj = obj.optJSONObject("ladb_config")
        if (ladbObj != null) {
            val ladbEditor = getSharedPreferences(LadbManager.PREFS_NAME, Context.MODE_PRIVATE).edit()
            val ladbKeys = ladbObj.keys()
            while (ladbKeys.hasNext()) {
                val key = ladbKeys.next()
                when (val value = ladbObj.opt(key)) {
                    is Boolean -> ladbEditor.putBoolean(key, value)
                    is String -> ladbEditor.putString(key, value)
                    is Int -> ladbEditor.putInt(key, value)
                    is Long -> ladbEditor.putLong(key, value)
                    is Double -> ladbEditor.putFloat(key, value.toFloat())
                    is JSONArray -> ladbEditor.putStringSet(key, jsonArrayToStringSet(value))
                }
            }
            ladbEditor.apply()
        }
    }

    private fun applyPreferenceEntry(editor: SharedPreferences.Editor, entry: JSONObject) {
        val key = entry.optString("key", "")
        if (key.isEmpty()) return

        when (entry.optString("type", "")) {
            "boolean" -> editor.putBoolean(key, entry.optBoolean("value"))
            "string" -> editor.putString(key, if (entry.isNull("value")) null else entry.optString("value"))
            "int" -> {
                val longValue = entry.getLong("value")
                if (longValue in Int.MIN_VALUE..Int.MAX_VALUE) {
                    editor.putInt(key, longValue.toInt())
                }
            }
            "long" -> editor.putLong(key, entry.optLong("value"))
            "float" -> editor.putFloat(key, entry.optDouble("value").toFloat())
            "string_set" -> editor.putStringSet(key, jsonArrayToStringSet(entry.optJSONArray("value")))
        }
    }

    private fun jsonArrayToStringSet(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        val result = linkedSetOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i, null)
            if (!value.isNullOrEmpty()) result.add(value)
        }
        return result
    }

    private fun sanitizeSelectedApps(prefs: SharedPreferences) {
        val selected = prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        val filtered = selected.filterNot { isShizukuPackage(it) }.toSet()
        prefs.edit()
            .putStringSet(MainActivity.KEY_SELECTED_APPS, filtered)
            .putInt(MainActivity.KEY_SELECTED_COUNT, filtered.size)
            .apply()
    }

    // helper to detect shizuku packages (supports both official and forked packages)
    private fun isShizukuPackage(pkg: String): Boolean {
        return ShizukuPackageResolver.isShizukuPackage(this, pkg)
    }

    private fun showAdbBroadcastDialog() {
        // Compose the instructions using the same constants the app uses so examples stay correct.
        val pkg = "com.arslan.shizuwall"
        val action = MainActivity.ACTION_FIREWALL_CONTROL
        val extraEnabled = MainActivity.EXTRA_FIREWALL_ENABLED
        val extraCsv = MainActivity.EXTRA_PACKAGES_CSV
        val component = "$pkg/.receivers.FirewallControlReceiver"

        val adbUsageText = getString(
            R.string.adb_broadcast_usage_text,
            action,
            component,
            extraEnabled,
            extraCsv
        )

        val tv = TextView(this).apply {
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt()
            )
            // Ensure text is set and TextView spans dialog width
            text = adbUsageText
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val scroll = ScrollView(this).apply {
            addView(tv)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.adb_broadcast_usage_title))
            .setView(scroll)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun showResetConfirmationDialog() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.reset_app_title))
            .setMessage(getString(R.string.reset_app_message))
            .setPositiveButton(getString(R.string.reset)) { _, _ ->
                resetApp()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
        }
        dialog.show()
    }

    private fun resetApp() {
        lifecycleScope.launch {
            try {
                stopResetSensitiveComponents()

                val cleanupPerformed = withContext(Dispatchers.IO) {
                    performBestEffortFirewallCleanup()
                }
                if (!cleanupPerformed) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.firewall_cleanup_warning), Toast.LENGTH_LONG).show()
                }

                // Prefer system-level data clear because it remains correct as new prefs/files are added.
                val clearRequested = requestSystemDataClear()
                if (clearRequested) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.app_reset_complete), Toast.LENGTH_SHORT).show()
                    delay(1200)
                    finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(0)
                    return@launch
                }

                val fallbackSuccess = withContext(Dispatchers.IO) { performManualDataClearFallback() }
                if (!fallbackSuccess) {
                    throw IllegalStateException("Manual reset fallback failed")
                }

                Toast.makeText(this@SettingsActivity, getString(R.string.app_reset_complete), Toast.LENGTH_SHORT).show()
                finishAffinity()
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(0)
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, getString(R.string.reset_app_failed, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopResetSensitiveComponents() {
        try { stopService(Intent(this, AppMonitorService::class.java)) } catch (_: Exception) {}
        try { stopService(Intent(this, ForegroundDetectionService::class.java)) } catch (_: Exception) {}
        try { FloatingButtonService.stop(this) } catch (_: Exception) {}
        try { (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancelAll() } catch (_: Exception) {}
    }

    private suspend fun performBestEffortFirewallCleanup(): Boolean {
        return try {
            val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)) return true

            val activePackages = prefs.getStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet()) ?: emptySet()
            val selectedApps = prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet()) ?: emptySet()
            val allPackages = (activePackages + selectedApps)
                .filterNot { it == packageName || ShizukuPackageResolver.isShizukuPackage(this, it) }

            val mode = prefs.getString(MainActivity.KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"
            val canPerformCleanup = when (mode) {
                "SHIZUKU" -> {
                    try {
                        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
                    } catch (_: Throwable) {
                        false
                    }
                }
                "ROOT" -> RootShellExecutor.hasRootAccess()
                else -> {
                    val dm = com.arslan.shizuwall.daemon.PersistentDaemonManager(this)
                    dm.isDaemonRunning() || LadbManager.getInstance(this).isConnected()
                }
            }

            if (!canPerformCleanup) return false

            val executor = ShellExecutorProvider.forContext(this)
            val chainResult = executor.exec("cmd connectivity set-chain3-enabled false")

            for (pkg in allPackages) {
                executor.exec("cmd connectivity set-package-networking-enabled true $pkg")
            }

            // Best-effort daemon cleanup in LADB mode so next launch starts fresh.
            if (mode == "LADB") {
                executor.exec("kill \\$(cat /data/local/tmp/daemon.pid 2>/dev/null) 2>/dev/null; pkill -f 'com.arslan.shizuwall.daemon.SystemDaemon' 2>/dev/null || true")
            }

            chainResult.success
        } catch (_: Exception) {
            false
        }
    }

    private fun requestSystemDataClear(): Boolean {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.clearApplicationUserData() == true
        } catch (_: Exception) {
            false
        }
    }

    private fun performManualDataClearFallback(): Boolean {
        var ok = true

        // Clear all discovered SharedPreferences in credential-protected storage.
        ok = clearAllSharedPrefs(this) && ok

        // Clear all discovered SharedPreferences in device-protected storage.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val dp = createDeviceProtectedStorageContext()
                ok = clearAllSharedPrefs(dp) && ok
            } catch (_: Exception) {
                ok = false
            }
        }

        // Clear databases.
        try {
            for (dbName in databaseList()) {
                if (!deleteDatabase(dbName)) ok = false
            }
        } catch (_: Exception) {
            ok = false
        }

        // Clear app-private files and caches.
        ok = deleteChildren(filesDir) && ok
        ok = deleteChildren(cacheDir) && ok
        ok = deleteChildren(codeCacheDir) && ok
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ok = deleteChildren(noBackupFilesDir) && ok
        }
        ok = deleteChildren(externalCacheDir) && ok
        ok = deleteChildren(getExternalFilesDir(null)) && ok

        return ok
    }

    private fun clearAllSharedPrefs(context: Context): Boolean {
        var ok = true
        val sharedPrefsDir = File(context.filesDir.parentFile, "shared_prefs")
        val prefNames = sharedPrefsDir.listFiles()
            ?.mapNotNull { file ->
                val name = file.name
                if (name.endsWith(".xml")) name.removeSuffix(".xml") else null
            }
            ?.toSet()
            ?: emptySet()

        for (name in prefNames) {
            try {
                val deleted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.deleteSharedPreferences(name)
                } else {
                    context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                }
                if (!deleted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                }
            } catch (_: Exception) {
                ok = false
            }
        }
        return ok
    }

    private fun deleteChildren(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return true
        var ok = true
        val children = dir.listFiles() ?: return true
        for (child in children) {
            if (!deleteRecursively(child)) ok = false
        }
        return ok
    }

    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    if (!deleteRecursively(child)) return false
                }
            }
        }
        return file.delete() || !file.exists()
    }


}
