package com.arslan.shizuwall.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Typeface
import android.os.Build
import androidx.appcompat.widget.SwitchCompat
import com.arslan.shizuwall.FirewallMode
import com.arslan.shizuwall.R
import com.arslan.shizuwall.ladb.LadbManager
import com.arslan.shizuwall.services.AppMonitorService
import com.arslan.shizuwall.services.ForegroundDetectionService
import com.arslan.shizuwall.utils.ShizukuPackageResolver
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku

class SettingsActivity : BaseActivity() {

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
    private lateinit var switchAppMonitor: SwitchCompat
    private lateinit var switchFloatingButton: SwitchCompat

    private lateinit var cardAdbBroadcastUsage: com.google.android.material.card.MaterialCardView
    private lateinit var layoutAdbBroadcastUsage: LinearLayout // new
    private lateinit var radioGroupWorkingMode: RadioGroup
    private lateinit var radioShizukuMode: RadioButton
    private lateinit var radioLadbMode: RadioButton
    private lateinit var cardSetLadb: com.google.android.material.card.MaterialCardView
    private lateinit var layoutSetLadb: LinearLayout
    private lateinit var cardSkipConfirm: com.google.android.material.card.MaterialCardView
    private var autoEnablePreviousState: Boolean = false  // Store previous state before disabling
    
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
        cardSetLadb = findViewById(R.id.cardSetLadb)
        layoutSetLadb = findViewById(R.id.layoutSetLadb)
        switchAppMonitor = findViewById(R.id.switchAppMonitor)
        switchFloatingButton = findViewById(R.id.switchFloatingButton)
        // Auto-enable switch (new)
        switchAutoEnableOnShizukuStart = findViewById(R.id.switchAutoEnableOnShizukuStart)
        cardAutoEnableOnShizukuStart = findViewById(R.id.cardAutoEnableOnShizukuStart)
        
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
        switchAppMonitor.isChecked = prefs.getBoolean(MainActivity.KEY_APP_MONITOR_ENABLED, false)
        switchFloatingButton.isChecked = prefs.getBoolean(
            com.arslan.shizuwall.services.FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, false
        )

        // Load working mode
        val workingModeName = prefs.getString(MainActivity.KEY_WORKING_MODE, com.arslan.shizuwall.WorkingMode.SHIZUKU.name)
        when (com.arslan.shizuwall.WorkingMode.fromName(workingModeName)) {
            com.arslan.shizuwall.WorkingMode.LADB -> radioGroupWorkingMode.check(R.id.radioLadbMode)
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
                prefs.edit().putString(MainActivity.KEY_WORKING_MODE, com.arslan.shizuwall.WorkingMode.SHIZUKU.name).apply()
            }
        }
        // Show/hide LADB card if not selected
        val ladbSelected = radioLadbMode.isChecked
        cardSetLadb.visibility = if (ladbSelected) View.VISIBLE else View.GONE
        
        // Hide auto-enable firewall card when LADB mode is selected
        cardAutoEnableOnShizukuStart.visibility = if (ladbSelected) View.GONE else View.VISIBLE
        switchAutoEnableOnShizukuStart.isEnabled = !ladbSelected
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
            val mode = if (checkedId == R.id.radioLadbMode) com.arslan.shizuwall.WorkingMode.LADB else com.arslan.shizuwall.WorkingMode.SHIZUKU
            prefs.edit().putString(MainActivity.KEY_WORKING_MODE, mode.name).apply()
            setResult(RESULT_OK)
            
            TransitionManager.beginDelayedTransition(findViewById(R.id.settingsRoot), AutoTransition())
            // Update UI affordance for LADB setup
            val isLadb = mode == com.arslan.shizuwall.WorkingMode.LADB
            cardSetLadb.visibility = if (isLadb) View.VISIBLE else View.GONE

            if (isLadb) {
                autoEnablePreviousState = switchAutoEnableOnShizukuStart.isChecked
                cardAutoEnableOnShizukuStart.visibility = View.GONE
                switchAutoEnableOnShizukuStart.isEnabled = false
                if (switchAutoEnableOnShizukuStart.isChecked) {
                    switchAutoEnableOnShizukuStart.isChecked = false
                    prefs.edit().putBoolean(MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START, false).apply()
                }
            } else {
                cardAutoEnableOnShizukuStart.visibility = View.VISIBLE
                switchAutoEnableOnShizukuStart.isEnabled = true
                if (autoEnablePreviousState) {
                    switchAutoEnableOnShizukuStart.isChecked = true
                    prefs.edit().putBoolean(MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START, true).apply()
                }
            }
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
                val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                val exportJson = JSONObject().apply {
                    put("version", 2)
                    put("exported_at", System.currentTimeMillis())

                    // Main data lists
                    put("selected", JSONArray(prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())?.toList() ?: emptyList<String>()))
                    put("favorites", JSONArray(prefs.getStringSet(MainActivity.KEY_FAVORITE_APPS, emptySet())?.toList() ?: emptyList<String>()))

                    // All relevant settings
                    val keys = listOf(
                        MainActivity.KEY_SHOW_SYSTEM_APPS,
                        MainActivity.KEY_ADAPTIVE_MODE,
                        MainActivity.KEY_SKIP_ENABLE_CONFIRM,
                        MainActivity.KEY_MOVE_SELECTED_TOP,
                        MainActivity.KEY_SKIP_ERROR_DIALOG,
                        MainActivity.KEY_KEEP_ERROR_APPS_SELECTED,
                        MainActivity.KEY_USE_DYNAMIC_COLOR,
                        MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START,
                        MainActivity.KEY_APP_MONITOR_ENABLED,
                        MainActivity.KEY_SKIP_ANDROID11_INFO,
                        MainActivity.KEY_SELECTED_FONT,
                        MainActivity.KEY_WORKING_MODE,
                        MainActivity.KEY_SORT_ORDER,
                        MainActivity.KEY_SHOW_SETUP_PROMPT
                    )

                    for (key in keys) {
                        if (prefs.contains(key)) {
                            put(key, prefs.all[key])
                        }
                    }

                    // Export LADB config
                    val ladbPrefs = getSharedPreferences(LadbManager.PREFS_NAME, Context.MODE_PRIVATE)
                    val ladbJson = JSONObject().apply {
                        put(LadbManager.KEY_HOST, ladbPrefs.getString(LadbManager.KEY_HOST, null))
                        // We skip ephemeral ports as they change on every start/reboot
                        put(LadbManager.KEY_IS_PAIRED, ladbPrefs.getBoolean(LadbManager.KEY_IS_PAIRED, false))
                    }
                    put("ladb_config", ladbJson)
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
                val version = obj.optInt("version", 1)
                val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                val editor = prefs.edit()

                // 1. Handle Selected Apps (legacy key "selected")
                val selectedKey = if (obj.has("selected")) "selected" else MainActivity.KEY_SELECTED_APPS
                val selectedJson = obj.optJSONArray(selectedKey)
                if (selectedJson != null) {
                    val selectedSet = mutableSetOf<String>()
                    for (i in 0 until selectedJson.length()) {
                        val v = selectedJson.optString(i, null)
                        if (!v.isNullOrEmpty()) selectedSet.add(v)
                    }
                    val filteredSelectedSet = selectedSet.filterNot { isShizukuPackage(it) }.toSet()
                    editor.putStringSet(MainActivity.KEY_SELECTED_APPS, filteredSelectedSet)
                    editor.putInt(MainActivity.KEY_SELECTED_COUNT, filteredSelectedSet.size)
                }

                // 2. Handle Favorites (legacy key "favorites")
                val favoritesKey = if (obj.has("favorites")) "favorites" else MainActivity.KEY_FAVORITE_APPS
                val favoritesJson = obj.optJSONArray(favoritesKey)
                if (favoritesJson != null) {
                    val favoritesSet = mutableSetOf<String>()
                    for (i in 0 until favoritesJson.length()) {
                        val v = favoritesJson.optString(i, null)
                        if (!v.isNullOrEmpty()) favoritesSet.add(v)
                    }
                    editor.putStringSet(MainActivity.KEY_FAVORITE_APPS, favoritesSet)
                }

                // 3. Handle All Other Settings
                val keys = listOf(
                    MainActivity.KEY_SHOW_SYSTEM_APPS,
                    MainActivity.KEY_ADAPTIVE_MODE,
                    MainActivity.KEY_SKIP_ENABLE_CONFIRM,
                    MainActivity.KEY_MOVE_SELECTED_TOP,
                    MainActivity.KEY_SKIP_ERROR_DIALOG,
                    MainActivity.KEY_KEEP_ERROR_APPS_SELECTED,
                    MainActivity.KEY_USE_DYNAMIC_COLOR,
                    MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START,
                    MainActivity.KEY_APP_MONITOR_ENABLED,
                    MainActivity.KEY_SKIP_ANDROID11_INFO,
                    MainActivity.KEY_SELECTED_FONT,
                    MainActivity.KEY_WORKING_MODE,
                    MainActivity.KEY_SORT_ORDER,
                    MainActivity.KEY_SHOW_SETUP_PROMPT
                )

                for (key in keys) {
                    if (obj.has(key)) {
                        when (val value = obj.get(key)) {
                            is Boolean -> editor.putBoolean(key, value)
                            is String -> editor.putString(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                        }
                    } else if (version == 1) {
                        // Legacy v1 used slightly different names for some settings in JSON
                        // but most were matched to MainActivity constants already.
                        // We handled 'selected' and 'favorites' above.
                    }
                }

                editor.apply()

                // 4. Handle LADB config
                val ladbObj = obj.optJSONObject("ladb_config")
                if (ladbObj != null) {
                    val ladbEditor = getSharedPreferences(LadbManager.PREFS_NAME, Context.MODE_PRIVATE).edit()
                    if (ladbObj.has(LadbManager.KEY_HOST)) {
                        ladbEditor.putString(LadbManager.KEY_HOST, ladbObj.optString(LadbManager.KEY_HOST))
                    }
                    if (ladbObj.has(LadbManager.KEY_IS_PAIRED)) {
                        ladbEditor.putBoolean(LadbManager.KEY_IS_PAIRED, ladbObj.optBoolean(LadbManager.KEY_IS_PAIRED))
                    }
                    ladbEditor.apply()
                }

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
                val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

                // If firewall is enabled, try to disable it first to revert system changes
                if (prefs.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)) {
                    val activePackages = prefs.getStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet()) ?: emptySet()
                    val selectedApps = prefs.getStringSet("selected_apps", emptySet()) ?: emptySet()
                    // Combine lists to ensure we catch everything
                    val allPackages = activePackages + selectedApps

                    var cleanupPerformed = false
                    withContext(Dispatchers.IO) {
                        try {
                            val prefsLocal = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                            val mode = prefsLocal.getString(MainActivity.KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"

                            val canPerformCleanup = if (mode == "SHIZUKU") {
                                try {
                                    Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
                                } catch (t: Throwable) {
                                    false
                                }
                            } else {
                                com.arslan.shizuwall.ladb.LadbManager.getInstance(this@SettingsActivity).isConnected()
                            }

                            if (canPerformCleanup) {
                                // 1. Disable global chain first (most important to restore internet)
                                com.arslan.shizuwall.shell.ShellExecutorBlocking.runBlockingSuccess(this@SettingsActivity, "cmd connectivity set-chain3-enabled false")

                                // 2. Clean up individual package rules
                                for (pkg in allPackages) {
                                    com.arslan.shizuwall.shell.ShellExecutorBlocking.runBlockingSuccess(this@SettingsActivity, "cmd connectivity set-package-networking-enabled true $pkg")
                                }
                                cleanupPerformed = true
                            }
                        } catch (e: Exception) {
                            // Proceed with reset even if cleanup fails
                        }
                    }

                    if (!cleanupPerformed) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SettingsActivity, getString(R.string.firewall_cleanup_warning), Toast.LENGTH_LONG).show()
                        }
                    }
                }

                // Clear main prefs
                prefs.edit().clear().commit()

                // Clear onboarding prefs
                getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().clear().commit()

                // Clear device protected prefs if applicable
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    try {
                        createDeviceProtectedStorageContext().getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE).edit().clear().commit()
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                Toast.makeText(this@SettingsActivity, getString(R.string.app_reset_complete), Toast.LENGTH_SHORT).show()

                // Close all activities
                finishAffinity()

                // Kill process to ensure fresh start
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(0)
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, getString(R.string.reset_app_failed, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }


}
