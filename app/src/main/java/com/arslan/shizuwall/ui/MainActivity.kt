package com.arslan.shizuwall.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.content.ActivityNotFoundException
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.arslan.shizuwall.services.AppMonitorService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.chip.Chip
import com.google.android.material.color.DynamicColors
import com.arslan.shizuwall.adapters.AppListAdapter
import com.arslan.shizuwall.adapters.SelectedAppsAdapter
import com.arslan.shizuwall.adapters.ErrorDetailsAdapter
import com.arslan.shizuwall.model.AppInfo
import com.arslan.shizuwall.widgets.FirewallWidgetProvider
import com.arslan.shizuwall.repo.FirewallStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.appcompat.widget.SwitchCompat
import com.arslan.shizuwall.R
import kotlin.comparisons.*
import com.arslan.shizuwall.adapters.ErrorEntry
import com.arslan.shizuwall.shizuku.ShizukuSetupActivity
import com.arslan.shizuwall.shell.ShellExecutorProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {
    companion object {
        const val PREF_NAME = "ShizuWallPrefs"
        const val KEY_SELECTED_APPS = "selected_apps"
        const val KEY_SELECTED_COUNT = "selected_count"
        const val KEY_FAVORITE_APPS = "favorite_apps"
        const val KEY_FIREWALL_ENABLED = "firewall_enabled"          // made public
        const val KEY_ACTIVE_PACKAGES = "active_packages"
        const val KEY_FIREWALL_SAVED_ELAPSED = "firewall_saved_elapsed" // made public
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1002
        private const val KEY_SKIP_ENABLE_CONFIRM = "skip_enable_confirm" 
        const val KEY_SKIP_ERROR_DIALOG = "skip_error_dialog"
        const val KEY_SKIP_ANDROID11_INFO = "skip_android11_info"
        const val KEY_KEEP_ERROR_APPS_SELECTED = "keep_error_apps_selected"
        const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
        const val KEY_MOVE_SELECTED_TOP = "move_selected_top"
        const val KEY_SELECTED_FONT = "selected_font"
        const val KEY_USE_DYNAMIC_COLOR = "use_dynamic_color"
        const val KEY_ADAPTIVE_MODE = "adaptive_mode"
        const val KEY_AUTO_ENABLE_ON_SHIZUKU_START = "auto_enable_on_shizuku_start"
        const val KEY_SHOW_SETUP_PROMPT = "show_setup_prompt"
        const val KEY_WORKING_MODE = "working_mode"
        const val KEY_SORT_ORDER = "sort_order"

        const val ACTION_FIREWALL_STATE_CHANGED = "com.arslan.shizuwall.ACTION_FIREWALL_STATE_CHANGED"
        const val EXTRA_FIREWALL_ENABLED = "state" 
        const val EXTRA_ACTIVE_PACKAGES = "com.arslan.shizuwall.EXTRA_ACTIVE_PACKAGES"

        const val ACTION_FIREWALL_CONTROL = "shizuwall.CONTROL" 
        const val EXTRA_PACKAGES_CSV = "apps" 

        const val KEY_FIREWALL_UPDATE_TS = "firewall_update_ts"
        const val KEY_APP_MONITOR_ENABLED = "app_monitor_enabled"


    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var appListAdapter: AppListAdapter
    private lateinit var firewallToggle: SwitchCompat
    private lateinit var firewallProgress: android.widget.ProgressBar
    private lateinit var searchView: SearchView
    private lateinit var selectedCountText: TextView
    private lateinit var selectAllCheckbox: CheckBox
    private val appList = mutableListOf<AppInfo>()
    private val filteredAppList = mutableListOf<AppInfo>()
    private var isFirewallEnabled = false
    private var currentQuery = ""
    private var showSystemApps = false 
    private var moveSelectedTop = true
    private var adaptiveMode = false
    private enum class SortOrder { NAME_ASC, NAME_DESC, INSTALL_TIME }
    private var currentSortOrder = SortOrder.NAME_ASC

    private lateinit var sharedPreferences: SharedPreferences

    private var firewallRepo: FirewallStateRepository? = null
    private var shizukuListenersAdded = false

    private var defaultItemAnimator: RecyclerView.ItemAnimator? = null

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        onRequestPermissionsResult(requestCode, grantResult)
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        val workingMode = sharedPreferences.getString(KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"
        if (workingMode == "LADB") return@OnBinderReceivedListener

        val autoEnable = sharedPreferences.getBoolean(KEY_AUTO_ENABLE_ON_SHIZUKU_START, false)
        if (!autoEnable) {
            checkShizukuPermission()
            return@OnBinderReceivedListener
        }

        // If already enabled, nothing to do
        if (isFirewallEnabled) return@OnBinderReceivedListener

        val selectedPkgs = loadSelectedApps().toList()
        if (selectedPkgs.isEmpty() && !adaptiveMode) {
            // Nothing to enable (and adaptive mode does not allow empty set)
            return@OnBinderReceivedListener
        }

        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                pendingAutoEnable = true
                pendingAutoEnableSelectedApps = selectedPkgs
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                return@OnBinderReceivedListener
            }
        } catch (e: Exception) {
            checkShizukuPermission()
            return@OnBinderReceivedListener
        }

        val skipConfirm = sharedPreferences.getBoolean(KEY_SKIP_ENABLE_CONFIRM, false)
        if (skipConfirm) {
            applyFirewallState(true, selectedPkgs)
            return@OnBinderReceivedListener
        }

        val selectedAppsList = appList.filter { selectedPkgs.contains(it.packageName) }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            runOnUiThread { showFirewallConfirmDialog(if (selectedAppsList.isNotEmpty()) selectedAppsList else emptyList()) }
        } else {
            pendingAutoEnable = true
            pendingAutoEnableSelectedApps = selectedPkgs
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        val workingMode = sharedPreferences.getString(KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"
        if (workingMode == "LADB") return@OnBinderDeadListener

        Toast.makeText(this, getString(R.string.shizuku_service_dead), Toast.LENGTH_SHORT).show()
        finish()
    }

    private var suppressToggleListener = false
    private var suppressSelectAllListener = false
    private val activeFirewallPackages = mutableSetOf<String>()
    // store the last operation and its console output per package so we can show details dialogs
    private val lastOperationErrorDetails = mutableMapOf<String, String>()
    private enum class Category { NONE, FAVORITES, SYSTEM, SELECTED, UNSELECTED, USER }
    private var currentCategory: Category = Category.NONE
    
    // Track if we're waiting for Shizuku permission due to toggle attempt
    private var pendingToggleEnable = false
    private var pendingToggleDisable = false
    // Store pending selections/packages when waiting for Shizuku permission
    private var pendingEnableSelectedApps: List<String>? = null
    private var pendingDisableActivePackages: List<String>? = null
    // Pending auto-enable triggered by Shizuku binder
    private var pendingAutoEnable = false
    private var pendingAutoEnableSelectedApps: List<String>? = null

    // receiver to handle package add/remove/replace events
    private val packageBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val action = intent?.action ?: return
            val pkg = intent.data?.schemeSpecificPart ?: return

            when (action) {
                Intent.ACTION_PACKAGE_REMOVED -> {
                    // ignore when package is being replaced (i.e. during an update)
                    val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    if (replacing) return
                    handlePackageRemoved(pkg)
                }
                Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED -> {
                    // reload apps to show newly installed/updated app
                    handlePackageAdded(pkg)
                }
            }
        }
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Reload settings and refresh the app list
            showSystemApps = sharedPreferences.getBoolean(KEY_SHOW_SYSTEM_APPS, false)
            moveSelectedTop = sharedPreferences.getBoolean(KEY_MOVE_SELECTED_TOP, true)
            adaptiveMode = sharedPreferences.getBoolean(KEY_ADAPTIVE_MODE, false)
            loadInstalledApps()
            updateCategoryChips()
            
            if (isFirewallEnabled) {
                if (adaptiveMode) hideDimOverlay() else showDimOverlay()
                appListAdapter.setSelectionEnabled(adaptiveMode || !isFirewallEnabled)
                updateInteractiveViews()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // Apply theme before any UI is created
        val useDynamicColor = sharedPreferences.getBoolean(KEY_USE_DYNAMIC_COLOR, true)
        if (useDynamicColor) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        
        enableEdgeToEdge()

        // Check if onboarding is complete
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val onboardingComplete = prefs.getBoolean("onboarding_complete", false)

        if (!onboardingComplete) {
            // Show onboarding
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Start App Monitor Service if enabled
        if (sharedPreferences.getBoolean(KEY_APP_MONITOR_ENABLED, false)) {
            val monitorIntent = Intent(this, AppMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(monitorIntent)
            } else {
                startService(monitorIntent)
            }
        }

        applyFontToViews(findViewById(android.R.id.content))

        // Show Android 11 compatibility warning if needed
        showAndroid11WarningDialog()

        // Prompt user to view Shizuku setup slides at app start
        val workingMode = sharedPreferences.getString(KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"
        val showSetupPrompt = sharedPreferences.getBoolean(KEY_SHOW_SETUP_PROMPT, true)
        if (showSetupPrompt && workingMode == "SHIZUKU") {
            val promptView = layoutInflater.inflate(R.layout.dialog_shizuku_prompt, null)
            val messageText: TextView = promptView.findViewById(R.id.shizuku_prompt_message_text)
            val checkbox: CheckBox = promptView.findViewById(R.id.shizuku_prompt_do_not_show)
            messageText.text = getString(R.string.shizuku_prompt_message)
            checkbox.text = getString(R.string.shizuku_prompt_do_not_show)

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.shizuku_prompt_title)
                .setView(promptView)
                .setPositiveButton(R.string.show_now) { _, _ ->
                    startActivity(Intent(this, ShizukuSetupActivity::class.java))
                    if (checkbox.isChecked) {
                        sharedPreferences.edit().putBoolean(KEY_SHOW_SETUP_PROMPT, false).apply()
                    }
                }
                .setNegativeButton(R.string.later) { _, _ ->
                    if (checkbox.isChecked) {
                        sharedPreferences.edit().putBoolean(KEY_SHOW_SETUP_PROMPT, false).apply()
                    }
                }
                .show()
        }

        // GitHub icon
        val openGithub = {
            val url = getString(R.string.github_url)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        val githubIcon: ImageView = findViewById(R.id.githubIcon)
        githubIcon.setOnClickListener { openGithub() }

        val appTitle: TextView = findViewById(R.id.appTitle)
        appTitle.setOnClickListener { openGithub() }

        val settingsButton: MaterialButton? = findViewById(R.id.settingsButton)
        settingsButton?.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            settingsLauncher.launch(intent)
        }

        val sortButton: MaterialButton? = findViewById(R.id.sortButton)
        sortButton?.setOnClickListener {
            showSortDialog()
        }

        showSystemApps = sharedPreferences.getBoolean(KEY_SHOW_SYSTEM_APPS, false)
        moveSelectedTop = sharedPreferences.getBoolean(KEY_MOVE_SELECTED_TOP, true)
        adaptiveMode = sharedPreferences.getBoolean(KEY_ADAPTIVE_MODE, false)
        currentSortOrder = try {
            SortOrder.valueOf(sharedPreferences.getString(KEY_SORT_ORDER, SortOrder.NAME_ASC.name) ?: SortOrder.NAME_ASC.name)
        } catch (e: Exception) {
            SortOrder.NAME_ASC
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        shizukuListenersAdded = true

        firewallRepo = FirewallStateRepository(applicationContext)
        firewallRepo?.let { repo ->
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    repo.state.collect { state ->
                        isFirewallEnabled = state.enabled
                        activeFirewallPackages.clear()
                        activeFirewallPackages.addAll(state.activePackages)

                        if (::firewallToggle.isInitialized) {
                            suppressToggleListener = true
                            firewallToggle.isChecked = state.enabled
                            suppressToggleListener = false
                        }

                        appListAdapter.setSelectionEnabled(!state.enabled || adaptiveMode)
                        if (state.enabled) showDimOverlay() else hideDimOverlay()

                        updateSelectedCount()
                        updateSelectAllCheckbox()
                        updateInteractiveViews()
                    }
                }
            }
        }

        setupFirewallToggle()
        setupSearchView()
        setupSelectAllCheckbox()
        setupRecyclerView()

        // wire category bar AFTER views are created
        val categoryGroup = findViewById<ChipGroup>(R.id.categoryChipGroup)
        categoryGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = if (checkedIds.isEmpty()) -1 else checkedIds[0]
            currentCategory = when (checkedId) {
                R.id.chip_favorites -> Category.FAVORITES
                R.id.chip_system -> Category.SYSTEM
                R.id.chip_selected -> Category.SELECTED
                R.id.chip_unselected -> Category.UNSELECTED
                R.id.chip_user -> Category.USER
                -1 -> Category.NONE
                else -> Category.NONE
            }
            sortAndFilterApps(preserveScrollPosition = false, scrollToTop = true)
        }

        // ensure the category chips reflect the saved "show system apps" preference
        updateCategoryChips()
       
        loadInstalledApps()

        // If user enabled auto-enable and Shizuku is already present, attempt to auto-enable.
        try {
            val autoPref = sharedPreferences.getBoolean(KEY_AUTO_ENABLE_ON_SHIZUKU_START, false)
            if (autoPref && !isFirewallEnabled) {
                if (Shizuku.pingBinder()) {
                    // If permission granted, proceed or request permission
                    if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                        // Avoid duplicating flows if we already have a pending auto enable
                        if (!pendingAutoEnable) {
                            val pkgs = loadSelectedApps().toList()
                            if (pkgs.isNotEmpty() || adaptiveMode) {
                                if (sharedPreferences.getBoolean(KEY_SKIP_ENABLE_CONFIRM, false)) {
                                    applyFirewallState(true, pkgs)
                                } else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                    val selectedAppsList = appList.filter { pkgs.contains(it.packageName) }
                                    runOnUiThread {
                                        showFirewallConfirmDialog(if (selectedAppsList.isNotEmpty()) selectedAppsList else emptyList())
                                    }
                                } else {
                                    pendingAutoEnable = true
                                    pendingAutoEnableSelectedApps = pkgs
                                }
                            }
                        }
                    } else {
                        // Request permission and mark pending so onRequestPermissionsResult can resume
                        pendingAutoEnable = true
                        pendingAutoEnableSelectedApps = loadSelectedApps().toList()
                        Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                    }
                }
            }
        } catch (_: Exception) {
            // ignore errors when pinging or permission checking
        }

        // Load and display saved selected count
        val savedCount = sharedPreferences.getInt(KEY_SELECTED_COUNT, 0)
        selectedCountText.text = savedCount.toString()

        // Load saved firewall state and apply to toggle without triggering listener
        isFirewallEnabled = loadFirewallEnabled()
        activeFirewallPackages.addAll(loadActivePackages())
        suppressToggleListener = true
        firewallToggle.isChecked = isFirewallEnabled
        suppressToggleListener = false

        // Ensure adapter and dim reflect saved firewall state
        appListAdapter.setSelectionEnabled(!isFirewallEnabled)
        updateInteractiveViews()
        if (isFirewallEnabled) {
            showDimOverlay()
        } else {
            hideDimOverlay()
        }

        // ensure the toggle is disabled if firewall is off AND there are no selected apps
        // (allows the toggle to remain enabled when firewall is active)
        if (::firewallToggle.isInitialized) {
            firewallToggle.isEnabled = isFirewallEnabled || savedCount > 0
        }

    }

    override fun onResume() {
        super.onResume()

        // If views were not initialized (e.g. onCreate returned early), avoid touching them.
        if (!::firewallToggle.isInitialized) {
            return
        }

        // Re-sync toggle with saved state without triggering listener
        suppressToggleListener = true
        firewallToggle.isChecked = loadFirewallEnabled()
        suppressToggleListener = false

        // Reflect current firewall state in UI
        val enabled = loadFirewallEnabled()
        appListAdapter.setSelectionEnabled(!enabled || adaptiveMode)
        updateInteractiveViews()
        if (enabled) showDimOverlay() else hideDimOverlay()
        loadInstalledApps()

        // Register package change receiver so installs/uninstalls/updates immediately refresh the list.
        // Wrapped in try/catch to avoid IllegalArgumentException if already registered.
        try {
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            registerReceiver(packageBroadcastReceiver, filter)
        } catch (e: IllegalArgumentException) {
            // already registered or other issue; ignore
        } catch (e: Exception) {
            // ignore other registration errors
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister package receiver to avoid leaks; ignore if not registered.
        try {
            unregisterReceiver(packageBroadcastReceiver)
        } catch (e: IllegalArgumentException) {
            // not registered
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (shizukuListenersAdded) {
            try {
                Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
                Shizuku.removeBinderReceivedListener(binderReceivedListener)
                Shizuku.removeBinderDeadListener(binderDeadListener)
            } catch (_: Exception) {
                // ignore if not registered or removal fails
            }
        }
        firewallRepo?.close()
        // Background service removed, nothing to stop here.
    }

    private fun checkShizukuPermission() {
        if (Shizuku.isPreV11()) {
            val d = MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.shizuku_update_required))
                .setMessage(getString(R.string.shizuku_version_too_old))
                .setPositiveButton(getString(R.string.ok), null) // do not close app, just dismiss
                .setCancelable(true)
                .create()
            d.setOnShowListener { d.window?.decorView?.let { applyFontToViews(it) } }
            d.show()
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            // Permission already granted
            return
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            // User denied permission permanently
            val d = MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.permission_required))
                .setMessage(getString(R.string.shizuku_permission_required_message))
                .setPositiveButton(getString(R.string.ok), null) // do not close app
                .setCancelable(true)
                .create()
            d.setOnShowListener { d.window?.decorView?.let { applyFontToViews(it) } }
            d.show()
        } else {
            // Request permission
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        }
    }

    private fun onRequestPermissionsResult(requestCode: Int, grantResult: Int) {
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            SHIZUKU_PERMISSION_REQUEST_CODE -> {
                if (granted) {
                    Toast.makeText(this, getString(R.string.shizuku_permission_granted), Toast.LENGTH_SHORT).show()
                    // If permission was requested due to toggle attempt, resume the enable flow
                    if (pendingToggleEnable) {
                        pendingToggleEnable = false
                        val selectedAppPkgs = pendingEnableSelectedApps ?: appList.filter { it.isSelected }.map { it.packageName }
                        val selectedApps = appList.filter { selectedAppPkgs.contains(it.packageName) }
                        pendingEnableSelectedApps = null
                        if (selectedApps.isNotEmpty()) {
                            // Set toggle to ON before showing confirmation dialog
                            suppressToggleListener = true
                            firewallToggle.isChecked = true
                            suppressToggleListener = false
                            showFirewallConfirmDialog(selectedApps)
                        } else {
                            suppressToggleListener = true
                            firewallToggle.isChecked = false
                            suppressToggleListener = false
                        }
                    } else if (pendingToggleDisable) {
                        pendingToggleDisable = false
                        // Use the active package list captured when the disable was requested; fallback to current active set
                        val pkgs = pendingDisableActivePackages ?: activeFirewallPackages.toList()
                        pendingDisableActivePackages = null
                        // Proceed with disabling the firewall
                        applyFirewallState(false, pkgs)
                    }
                    // If permission was requested for an auto-enable flow, resume it here
                    else if (pendingAutoEnable) {
                        pendingAutoEnable = false
                        val pkgs = pendingAutoEnableSelectedApps ?: appList.filter { it.isSelected }.map { it.packageName }
                        pendingAutoEnableSelectedApps = null
                        if (pkgs.isNotEmpty() || adaptiveMode) {
                            val selectedApps = appList.filter { pkgs.contains(it.packageName) }
                            if (sharedPreferences.getBoolean(KEY_SKIP_ENABLE_CONFIRM, false)) {
                                applyFirewallState(true, pkgs)
                            } else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                // Show confirmation dialog in foreground
                                if (selectedApps.isNotEmpty() || adaptiveMode) {
                                    showFirewallConfirmDialog(selectedApps)
                                }
                            } else {
                                // Not in the foreground; keep pending and rely on onResume to show dialog
                                pendingAutoEnable = true
                                pendingAutoEnableSelectedApps = pkgs
                            }
                        }
                    }
                } else {
                    // Permission denied, revert toggle to its previous state
                    pendingToggleEnable = false
                    pendingToggleDisable = false
                    pendingEnableSelectedApps = null
                    pendingDisableActivePackages = null
                    pendingAutoEnable = false
                    pendingAutoEnableSelectedApps = null
                    suppressToggleListener = true
                    // If we were trying to enable, revert to off; if trying to disable, revert to on
                    firewallToggle.isChecked = isFirewallEnabled
                    suppressToggleListener = false
                    val d = MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.permission_denied))
                        .setMessage(getString(R.string.shizuku_permission_required_message))
                        .setPositiveButton(getString(R.string.ok), null) // just dismiss dialog
                        .setCancelable(true)
                        .create()
                    d.setOnShowListener { d.window?.decorView?.let { applyFontToViews(it) } }
                    d.show()
                }
            }
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (granted) {
                    Toast.makeText(this, getString(R.string.notification_permission_granted), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.notification_permission_denied), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun checkPermission(code: Int): Boolean {
        val workingMode = sharedPreferences.getString(KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"
        if (workingMode == "LADB") {
            val daemonManager = com.arslan.shizuwall.daemon.PersistentDaemonManager(this)
            if (daemonManager.isDaemonRunning()) return true

            val d = MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.working_mode_ladb))
                .setMessage(getString(R.string.daemon_not_running))
                .setPositiveButton(getString(R.string.open_daemon_setup)) { _, _ ->
                    try {
                        startActivity(Intent(this, com.arslan.shizuwall.LadbSetupActivity::class.java))
                    } catch (_: Exception) {
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .create()
            d.setOnShowListener { d.window?.decorView?.let { applyFontToViews(it) } }
            d.show()
            return false
        }

        // First ensure Shizuku binder is reachable. If it's not running, show a friendly dialog prompting the user to start/install Shizuku.
        try {
            if (!Shizuku.pingBinder()) {
                val d = MaterialAlertDialogBuilder(this)
                     .setTitle(getString(R.string.shizuku_not_running_title))
                     .setMessage(getString(R.string.shizuku_not_running_message))
                     .setPositiveButton(getString(R.string.open_shizuku)) { _, _ ->
                        // Try to open the Shizuku app if present, otherwise open Play Store, otherwise fallback to GitHub.
                        val pm = packageManager
                        val candidates = listOf("moe.shizuku.privileged.api", "moe.shizuku.manager")
                        var launched = false
                        for (pkg in candidates) {
                            val launch = pm.getLaunchIntentForPackage(pkg)
                            if (launch != null) {
                                startActivity(launch)
                                launched = true
                                break
                            }
                        }
                        if (!launched) {
                            var openedPlay = false
                            for (pkgId in candidates) {
                                try {
                                    val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkgId"))
                                    startActivity(playIntent)
                                    openedPlay = true
                                    break
                                } catch (e: ActivityNotFoundException) {
                                    // Play Store app not available on device, will fallback to web below
                                } catch (e: Exception) {
                                    // details page not found or other error, try next candidate
                                }
                            }
                            if (!openedPlay) {
                                try {
                                    // Open Play Store search for "Shizuku" to avoid "not found" detail pages
                                    val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=Shizuku"))
                                    startActivity(searchIntent)
                                } catch (e: Exception) {
                                    try {
                                        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku"))
                                        startActivity(web)
                                    } catch (_: Exception) {
                                        // ignore
                                    }
                                }
                            }
                        }
                    }
                     .setNegativeButton(getString(R.string.cancel), null)
                     .create()
                d.setOnShowListener { d.window?.decorView?.let { applyFontToViews(it) } }
                d.show()
                return false
            }
        } catch (e: Exception) {
            // If ping fails unexpectedly, fall back to permission flow below.
        }

        if (Shizuku.isPreV11()) {
            // Pre-v11 is unsupported
            Toast.makeText(this, getString(R.string.shizuku_version_old_toast), Toast.LENGTH_SHORT).show()
            return false
        }

        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            // Granted
            true
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            // Users chose "Deny and don't ask again"
            Toast.makeText(this, getString(R.string.grant_shizuku_permission_settings), Toast.LENGTH_LONG).show()
            false
        } else {
            // Request the permission (this will show the Shizuku permission dialog)
            Shizuku.requestPermission(code)
            false
        }
    }

    private fun setupSearchView() {
        searchView = findViewById(R.id.searchView)
        searchView.queryHint = getString(R.string.search_app)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentQuery = newText ?: ""
                filterApps(currentQuery)

                // Disable animator to prevent visual clutter during search filtering
                recyclerView.itemAnimator = null
                appListAdapter.submitList(filteredAppList.toList()) { 
                    recyclerView.itemAnimator = defaultItemAnimator
                    updateSelectedCount()
                }
                return true
            }
        })
    }

    private fun setupSelectAllCheckbox() {
        selectAllCheckbox = findViewById(R.id.selectAllCheckbox)
        selectedCountText = findViewById(R.id.selectedCountText)
        
        selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (suppressSelectAllListener) return@setOnCheckedChangeListener
            if (!selectAllCheckbox.isEnabled) return@setOnCheckedChangeListener
            
            if (adaptiveMode && isFirewallEnabled && !checkPermission(SHIZUKU_PERMISSION_REQUEST_CODE)) {
                suppressSelectAllListener = true
                selectAllCheckbox.isChecked = !isChecked
                suppressSelectAllListener = false
                return@setOnCheckedChangeListener
            }
            
            val changedApps = filteredAppList.filter { it.isSelected != isChecked }
            if (changedApps.isNotEmpty()) {
                val packagesToUpdate = changedApps.map { it.packageName }
                val filteredPackages = filteredAppList.map { it.packageName }.toSet()
                
                for (i in appList.indices) {
                    val ai = appList[i]
                    if (ai.packageName in filteredPackages) {
                        appList[i] = ai.copy(isSelected = isChecked)
                    }
                }
                updateSelectedCount()
                saveSelectedApps()
                sortAndFilterApps(preserveScrollPosition = true)

                // Adaptive Mode apply rules immediately if firewall is enabled
                if (isFirewallEnabled && adaptiveMode) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val successful = mutableListOf<String>()
                        val failed = mutableListOf<String>()
                        lastOperationErrorDetails.clear()
                        
                        for (pkg in packagesToUpdate) {
                            val cmd = if (isChecked) {
                                "cmd connectivity set-package-networking-enabled false $pkg"
                            } else {
                                "cmd connectivity set-package-networking-enabled true $pkg"
                            }
                            val res = runCommandDetailed(cmd)
                            if (res.success) {
                                successful.add(pkg)
                            } else {
                                failed.add(pkg)
                                lastOperationErrorDetails[pkg] = res.stderr.ifEmpty { res.stdout }
                            }
                        }
                        
                        withContext(Dispatchers.Main) {
                            if (successful.isNotEmpty()) {
                                if (isChecked) {
                                    activeFirewallPackages.addAll(successful)
                                } else {
                                    activeFirewallPackages.removeAll(successful)
                                }
                                saveActivePackages(activeFirewallPackages)
                            }
                            
                            // Handle failures
                            if (failed.isNotEmpty()) {
                                if (isChecked) {
                                    // We tried to enable firewall (select) but failed. Revert selection.
                                    val skipErrorDialog = sharedPreferences.getBoolean(KEY_SKIP_ERROR_DIALOG, false)
                                    val keepErrorAppsSelected = sharedPreferences.getBoolean(KEY_KEEP_ERROR_APPS_SELECTED, false)
                                    
                                    if (!(skipErrorDialog && keepErrorAppsSelected)) {
                                        for (pkg in failed) {
                                            val idx = appList.indexOfFirst { it.packageName == pkg }
                                            if (idx != -1) {
                                                appList[idx] = appList[idx].copy(isSelected = false)
                                            }
                                        }
                                        updateSelectedCount()
                                        saveSelectedApps()
                                        sortAndFilterApps(preserveScrollPosition = true)
                                    }
                                    showOperationErrorsDialog(failed, lastOperationErrorDetails)
                                } else {
                                    Toast.makeText(this@MainActivity, getString(R.string.failed_to_update_rules_count, failed.size), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        }

        selectAllCheckbox.setOnLongClickListener {
            if (!selectAllCheckbox.isEnabled) return@setOnLongClickListener false

            if (appList.any { it.isSelected }) {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(getString(R.string.unselect_all))
                    .setMessage(getString(R.string.deselect_all_apps))
                    .setPositiveButton(getString(R.string.unselect)) { _, _ ->
                        val previouslySelected = appList.filter { it.isSelected }.map { it.packageName }

                        for (i in appList.indices) {
                            appList[i] = appList[i].copy(isSelected = false)
                        }
                        updateSelectedCount()
                        saveSelectedApps()
                        sortAndFilterApps(preserveScrollPosition = true)
                        
                        // Unblock all previously selected apps
                        if (isFirewallEnabled && adaptiveMode && previouslySelected.isNotEmpty()) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val successful = mutableListOf<String>()
                                val failed = mutableListOf<String>()
                                lastOperationErrorDetails.clear()
                                
                                for (pkg in previouslySelected) {
                                    val res = runCommandDetailed("cmd connectivity set-package-networking-enabled true $pkg")
                                    if (res.success) {
                                        successful.add(pkg)
                                    } else {
                                        failed.add(pkg)
                                        lastOperationErrorDetails[pkg] = res.stderr.ifEmpty { res.stdout }
                                    }
                                }
                                
                                withContext(Dispatchers.Main) {
                                    if (successful.isNotEmpty()) {
                                        activeFirewallPackages.removeAll(successful)
                                        saveActivePackages(activeFirewallPackages)
                                    }
                                    if (failed.isNotEmpty()) {
                                        Toast.makeText(this@MainActivity, getString(R.string.failed_to_unblock_count, failed.size), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }

                        Toast.makeText(this@MainActivity, getString(R.string.all_apps_unselected), Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
                true
            } else {
                false
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        appListAdapter = AppListAdapter(
            onAppClick = { appInfo ->
                if (adaptiveMode && isFirewallEnabled && !checkPermission(SHIZUKU_PERMISSION_REQUEST_CODE)) {
                    appListAdapter.notifyDataSetChanged()
                    return@AppListAdapter
                }

                val idx = appList.indexOfFirst { it.packageName == appInfo.packageName }
                if (idx != -1) {
                    appList[idx] = appList[idx].copy(isSelected = appInfo.isSelected)
                }
                updateSelectedCount()
                saveSelectedApps()
                sortAndFilterApps(preserveScrollPosition = true)

                // Apply rule immediately if firewall is enabled
                if (isFirewallEnabled && adaptiveMode) {
                    val pkg = appInfo.packageName
                    val isSelected = appInfo.isSelected
                    lifecycleScope.launch(Dispatchers.IO) {
                        lastOperationErrorDetails.clear()
                        val res = if (isSelected) {
                                runCommandDetailed("cmd connectivity set-package-networking-enabled false $pkg")
                            } else {
                                runCommandDetailed("cmd connectivity set-package-networking-enabled true $pkg")
                            }
                        val success = res.success
                        
                        withContext(Dispatchers.Main) {
                                    if (success) {
                                if (isSelected) {
                                    activeFirewallPackages.add(pkg)
                                } else {
                                    activeFirewallPackages.remove(pkg)
                                }
                                saveActivePackages(activeFirewallPackages)
                            } else {
                                // Operation failed
                                        if (isSelected) {
                                    // Failed to block (select)
                                    val skipErrorDialog = sharedPreferences.getBoolean(KEY_SKIP_ERROR_DIALOG, false)
                                    val keepErrorAppsSelected = sharedPreferences.getBoolean(KEY_KEEP_ERROR_APPS_SELECTED, false)

                                    if (!(skipErrorDialog && keepErrorAppsSelected)) {
                                        val revertIdx = appList.indexOfFirst { it.packageName == pkg }
                                        if (revertIdx != -1) {
                                            appList[revertIdx] = appList[revertIdx].copy(isSelected = false)
                                        }
                                        updateSelectedCount()
                                        saveSelectedApps()
                                        sortAndFilterApps(preserveScrollPosition = true)
                                    }
                                    lastOperationErrorDetails[pkg] = res.stderr.ifEmpty { res.stdout }
                                    showOperationErrorsDialog(listOf(pkg), lastOperationErrorDetails)
                                } else {
                                    // Failed to unblock (unselect)
                                    val revertIdx = appList.indexOfFirst { it.packageName == pkg }
                                    if (revertIdx != -1) {
                                        appList[revertIdx] = appList[revertIdx].copy(isSelected = true)
                                    }
                                    updateSelectedCount()
                                    saveSelectedApps()
                                    sortAndFilterApps(preserveScrollPosition = true)
                                    
                                    Toast.makeText(this@MainActivity, getString(R.string.failed_to_unblock_app, appInfo.appName), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            },
            onAppLongClick = { appInfo ->
                toggleFavorite(appInfo)
            },
            typeface = getSelectedTypeface()
        )
        recyclerView.adapter = appListAdapter
        defaultItemAnimator = recyclerView.itemAnimator
    }

    private fun toggleFavorite(appInfo: AppInfo) {
        val idx = appList.indexOfFirst { it.packageName == appInfo.packageName }
        if (idx != -1) {
            val newFavoriteState = !appList[idx].isFavorite
            appList[idx] = appList[idx].copy(isFavorite = newFavoriteState)
            
            saveFavoriteApps()
            
            // If we're viewing favorites and removed this item, remove it from filtered list
            if (currentCategory == Category.FAVORITES && !newFavoriteState) {
                filteredAppList.removeAll { it.packageName == appInfo.packageName }
            } else if (currentCategory == Category.FAVORITES && newFavoriteState) {
                // If we're viewing favorites and added this item, it should already be there
                // but let's update it to be safe
                val filteredIdx = filteredAppList.indexOfFirst { it.packageName == appInfo.packageName }
                if (filteredIdx != -1) {
                    filteredAppList[filteredIdx] = appList[idx]
                }
            } else {
                // For other categories, just update the item in place
                val filteredIdx = filteredAppList.indexOfFirst { it.packageName == appInfo.packageName }
                if (filteredIdx != -1) {
                    filteredAppList[filteredIdx] = appList[idx]
                }
            }
            
            // Force adapter to update by submitting a new list
            appListAdapter.submitList(filteredAppList.toList())
        }
    }

    private fun saveFavoriteApps() {
        val favoritePackages = appList.filter { it.isFavorite }.map { it.packageName }.toSet()
        sharedPreferences.edit()
            .putStringSet(KEY_FAVORITE_APPS, favoritePackages)
            .apply()
    }

    private fun loadFavoriteApps(): Set<String> {
        return sharedPreferences.getStringSet(KEY_FAVORITE_APPS, emptySet()) ?: emptySet()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun filterApps(query: String) {
        filteredAppList.clear()

        // Apply category filter first
        val baseList: List<AppInfo> = when (currentCategory) {
            Category.NONE -> if (showSystemApps) appList else appList.filter { !it.isSystem }
            Category.FAVORITES -> appList.filter { it.isFavorite }
            Category.SYSTEM -> appList.filter { it.isSystem }
            Category.USER -> appList.filter { !it.isSystem }
            Category.SELECTED -> appList.filter { it.isSelected }
            Category.UNSELECTED -> appList.filter { !it.isSelected }
        }

        if (query.isEmpty()) {
            filteredAppList.addAll(baseList)
        } else {
            val searchQuery = query.lowercase()
            filteredAppList.addAll(baseList.filter {
                it.appName.lowercase().contains(searchQuery) ||
                it.packageName.lowercase().contains(searchQuery)
            })
        }
        updateSelectAllCheckbox()
        // Removed submitList from here; handled in callers
    }

    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.sort_install_time),
            getString(R.string.sort_name_asc),
            getString(R.string.sort_name_desc)
        )
        
        val sortOrders = arrayOf(
            SortOrder.INSTALL_TIME,
            SortOrder.NAME_ASC,
            SortOrder.NAME_DESC
        )
        
        val currentIndex = sortOrders.indexOf(currentSortOrder)
        
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                currentSortOrder = sortOrders[which]
                sharedPreferences.edit().putString(KEY_SORT_ORDER, currentSortOrder.name).apply()
                sortAndFilterApps(preserveScrollPosition = false, scrollToTop = true, animate = true)
                dialog.dismiss()
            }
            .show()
    }

    private fun sortAndFilterApps(preserveScrollPosition: Boolean = false, scrollToTop: Boolean = false, animate: Boolean = false) {
        val turkishCollator = java.text.Collator.getInstance(java.util.Locale.forLanguageTag("tr-TR"))
        
        val baseComparator = if (moveSelectedTop) {
            compareByDescending<AppInfo> { it.isSelected }
                .thenBy { it.isSystem }
        } else {
            compareBy<AppInfo> { it.isSystem }
        }

        val finalComparator = when (currentSortOrder) {
            SortOrder.NAME_ASC -> baseComparator.thenBy(turkishCollator) { it.appName }
            SortOrder.NAME_DESC -> baseComparator.thenByDescending(turkishCollator) { it.appName }
            SortOrder.INSTALL_TIME -> baseComparator.thenByDescending { it.installTime }
        }

        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        
        var firstVisible = RecyclerView.NO_POSITION
        var offset = 0

        if (preserveScrollPosition && moveSelectedTop) {
            firstVisible = layoutManager.findFirstVisibleItemPosition()
            offset = layoutManager.findViewByPosition(firstVisible)?.top ?: 0
        }

        val updateList = {
            // Disable animator to prevent visual clutter during list updates
            recyclerView.itemAnimator = null
            appListAdapter.submitList(filteredAppList.toList()) {
                recyclerView.itemAnimator = defaultItemAnimator
                
                if (preserveScrollPosition && moveSelectedTop && firstVisible != RecyclerView.NO_POSITION) {
                    layoutManager.scrollToPositionWithOffset(firstVisible, offset)
                } else if (scrollToTop) {
                    layoutManager.scrollToPosition(0)
                }
                
                updateSelectedCount()
                updateSelectAllCheckbox()

                if (animate) {
                    recyclerView.post {
                        recyclerView.animate()
                            .alpha(1f)
                            .setStartDelay(400)
                            .setDuration(200)
                            .start()
                    }
                }
            }
        }

        if (animate) {
            recyclerView.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    appList.sortWith(finalComparator)
                    filterApps(currentQuery)
                    updateList()
                }
                .start()
        } else {
            appList.sortWith(finalComparator)
            filterApps(currentQuery)
            recyclerView.animate().cancel()
            recyclerView.alpha = 1f
            updateList()
        }
    }

    private fun setupFirewallToggle() {
        firewallToggle = findViewById(R.id.firewallToggle)
        firewallProgress = findViewById(R.id.firewallProgress)
        firewallProgress.visibility = android.view.View.GONE
        selectedCountText = findViewById(R.id.selectedCountText)

        firewallToggle.setOnCheckedChangeListener { _, isChecked ->
            if (suppressToggleListener) return@setOnCheckedChangeListener
            if (isChecked) {
                val selectedApps = appList.filter { it.isSelected }
                if (selectedApps.isEmpty() && !adaptiveMode) {
                    Toast.makeText(this, getString(R.string.select_at_least_one_app), Toast.LENGTH_SHORT).show()
                    suppressToggleListener = true
                    firewallToggle.isChecked = false
                    suppressToggleListener = false
                    return@setOnCheckedChangeListener
                }

                val workingMode = sharedPreferences.getString(KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"
                if (workingMode == "LADB") {
                    val daemonManager = com.arslan.shizuwall.daemon.PersistentDaemonManager(this)

                    // If daemon is running, proceed
                    if (daemonManager.isDaemonRunning()) {
                        showFirewallConfirmDialog(selectedApps)
                        return@setOnCheckedChangeListener
                    }

                    // Daemon not running — prompt to open Daemon setup
                    val d = MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.working_mode_ladb))
                        .setMessage(getString(R.string.daemon_not_running))
                        .setPositiveButton(getString(R.string.open_daemon_setup)) { _, _ ->
                            try {
                                startActivity(Intent(this, com.arslan.shizuwall.LadbSetupActivity::class.java))
                            } catch (_: Exception) {
                            }
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .create()
                    d.setOnShowListener { d.window?.decorView?.let { applyFontToViews(it) } }
                    d.show()

                    suppressToggleListener = true
                    firewallToggle.isChecked = false
                    suppressToggleListener = false
                    return@setOnCheckedChangeListener
                }

                // Non-LADB path: fall back to Shizuku permission flow
                if (!checkPermission(SHIZUKU_PERMISSION_REQUEST_CODE)) {
                    // Permission not granted, mark that we're waiting for it
                    pendingToggleEnable = true
                    pendingEnableSelectedApps = appList.filter { it.isSelected }.map { it.packageName }
                    suppressToggleListener = true
                    firewallToggle.isChecked = false
                    suppressToggleListener = false
                    return@setOnCheckedChangeListener
                }
                // Permission already granted, proceed
                pendingToggleEnable = false
                showFirewallConfirmDialog(selectedApps)
            } else {
                if (!isFirewallEnabled) {
                    return@setOnCheckedChangeListener
                }

                val workingMode = sharedPreferences.getString(KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"
                if (workingMode == "LADB") {
                    val daemonManager = com.arslan.shizuwall.daemon.PersistentDaemonManager(this)
                    if (!daemonManager.isDaemonRunning()) {
                        val d = MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.working_mode_ladb))
                            .setMessage(getString(R.string.daemon_not_running))
                            .setPositiveButton(getString(R.string.open_daemon_setup)) { _, _ ->
                                try {
                                    startActivity(Intent(this, com.arslan.shizuwall.LadbSetupActivity::class.java))
                                } catch (_: Exception) {
                                }
                            }
                            .setNegativeButton(getString(R.string.cancel), null)
                            .create()
                        d.setOnShowListener { d.window?.decorView?.let { applyFontToViews(it) } }
                        d.show()

                        suppressToggleListener = true
                        firewallToggle.isChecked = true
                        suppressToggleListener = false
                        return@setOnCheckedChangeListener
                    }

                    // Daemon is running — proceed with disable
                    applyFirewallState(false, activeFirewallPackages.toList())
                    return@setOnCheckedChangeListener
                }

                if (!checkPermission(SHIZUKU_PERMISSION_REQUEST_CODE)) {
                    // Permission not granted, mark that we're waiting for it
                    pendingToggleDisable = true
                    pendingDisableActivePackages = activeFirewallPackages.toList()
                    suppressToggleListener = true
                    firewallToggle.isChecked = true
                    suppressToggleListener = false
                    return@setOnCheckedChangeListener
                }
                // Permission already granted, proceed
                pendingToggleDisable = false
                applyFirewallState(false, activeFirewallPackages.toList())
            }
        }
    }

    private fun showFirewallConfirmDialog(selectedApps: List<AppInfo>) {
        // If user opted to skip the confirmation, directly apply the firewall
        val prefsLocal = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (prefsLocal.getBoolean(KEY_SKIP_ENABLE_CONFIRM, false)) {
            applyFirewallState(true, selectedApps.map { it.packageName })
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_firewall_confirm, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.enable)) { _, _ ->
                applyFirewallState(true, selectedApps.map { it.packageName })
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                suppressToggleListener = true
                firewallToggle.isChecked = false
                suppressToggleListener = false
                pendingEnableSelectedApps = null
            }
            .create()

        applyFontToViews(dialogView)
        dialog.setOnShowListener {
            dialog.window?.decorView?.let { applyFontToViews(it) }
        }
        val dialogMessage = dialogView.findViewById<TextView>(R.id.dialogMessage)
        val selectedAppsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.selectedAppsRecyclerView)

        dialogMessage.text = getString(R.string.enable_firewall_confirm, selectedApps.size)

        selectedAppsRecyclerView.layoutManager = LinearLayoutManager(this)
        selectedAppsRecyclerView.adapter = SelectedAppsAdapter(selectedApps, getSelectedTypeface())

        // Limit the RecyclerView height to a fraction of the screen
        // Calculate constraints BEFORE showing dialog to prevent visual jumping/sliding
        val displayMetrics = resources.displayMetrics
        val displayHeight = displayMetrics.heightPixels
        val maxRecyclerHeight = (displayHeight * 0.4).toInt() // 40% of screen height
        
        // Estimate height: ~72dp per item. If total exceeds max, fix the height.
        val estimatedItemHeight = (72 * displayMetrics.density).toInt()
        val estimatedContentHeight = estimatedItemHeight * selectedApps.size
        
        val lp = selectedAppsRecyclerView.layoutParams
        if (estimatedContentHeight > maxRecyclerHeight) {
            lp.height = maxRecyclerHeight
        } else {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        selectedAppsRecyclerView.layoutParams = lp
        selectedAppsRecyclerView.isNestedScrollingEnabled = false

        dialog.show()
    }

    private fun showAndroid11WarningDialog() {
        // Only show for Android 11 and if user hasn't opted out
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.R) return

        val prefsLocal = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (prefsLocal.getBoolean(KEY_SKIP_ANDROID11_INFO, false)) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_android11_info, null)
        val checkbox = dialogView.findViewById<CheckBox>(R.id.checkboxDontShowAgain)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                if (checkbox.isChecked) {
                    prefsLocal.edit().putBoolean(KEY_SKIP_ANDROID11_INFO, true).apply()
                }
            }
            .create()

        applyFontToViews(dialogView)
        dialog.setOnShowListener {
            dialog.window?.decorView?.let { applyFontToViews(it) }
        }

        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val dialogMessage = dialogView.findViewById<TextView>(R.id.dialogMessage)

        dialogTitle.text = getString(R.string.android11_unsupported_title)
        dialogMessage.text = getString(R.string.android11_unsupported_message)

        dialog.show()
    }

    private fun updateSelectedCount() {
        val count = appList.count { it.isSelected }
        selectedCountText.text = count.toString()

        // enable the firewall toggle if firewall is currently active (so user can disable),
        // or if there is at least one selected app (so user can enable).
        if (::firewallToggle.isInitialized) {
            firewallToggle.isEnabled = isFirewallEnabled || count > 0 || adaptiveMode
        }
        
        updateSelectAllCheckbox()
        updateInteractiveViews()
    }

    private fun updateInteractiveViews() {
        // The select-all checkbox should be disabled when the firewall is enabled and
        // Adaptive Mode is turned OFF. Otherwise it can be used.
        if (::selectAllCheckbox.isInitialized) {
            selectAllCheckbox.isEnabled = !isFirewallEnabled || adaptiveMode
        }
    }

    private fun updateSelectAllCheckbox() {
        if (!::selectAllCheckbox.isInitialized || filteredAppList.isEmpty()) return
        
        suppressSelectAllListener = true
        val allSelected = filteredAppList.all { it.isSelected }
        val noneSelected = filteredAppList.none { it.isSelected }
        
        selectAllCheckbox.isChecked = allSelected
        suppressSelectAllListener = false
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadInstalledApps() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val pm = packageManager
                val packages = pm.getInstalledPackages(0)
                
                val installedPackageNames = packages.map { it.packageName }.toSet()

                val savedActive = loadActivePackages().toMutableSet()
                val activeToRemove = savedActive.filter { !installedPackageNames.contains(it) }
                val appsWereRemoved = activeToRemove.isNotEmpty()

                if (appsWereRemoved) {
                    savedActive.removeAll(activeToRemove)
                    saveActivePackages(savedActive)
                }

                val savedSelected = loadSelectedApps().toMutableSet()
                val selectedToRemove = savedSelected.filter { !installedPackageNames.contains(it) }
                // Remove this app itself from saved selected apps if present
                val selfPkg = this@MainActivity.packageName
                var selectedChanged = false
                if (savedSelected.remove(selfPkg)) selectedChanged = true
                if (selectedToRemove.isNotEmpty()) {
                    savedSelected.removeAll(selectedToRemove)
                    selectedChanged = true
                }
                if (selectedChanged) {
                    sharedPreferences.edit()
                        .putStringSet(KEY_SELECTED_APPS, savedSelected)
                        .putInt(KEY_SELECTED_COUNT, savedSelected.size)
                        .apply()
                }

                val favoritePackages = loadFavoriteApps()
                val temp = mutableListOf<AppInfo>()
                for (packageInfo in packages) {
                    val appInfo = packageInfo.applicationInfo ?: continue
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    // skip apps that are disabled (treated as "offline" — don't show them even if system apps are enabled)
                    if (!appInfo.enabled) continue

                    // include if user requested system apps, or it's a user-installed app
                    if (!showSystemApps && isSystemApp) continue

                    val packageName = packageInfo.packageName

                    // never show Shizuku app(s) or this app itself in the list
                    if (packageName == "moe.shizuku.privileged.api") continue
                    if (packageName == this@MainActivity.packageName) continue

                    // skip apps that do not have internet permission (offline apps)
                    val hasInternetPermission = pm.checkPermission(
                        Manifest.permission.INTERNET,
                        packageName
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasInternetPermission) continue

                    val appName = pm.getApplicationLabel(appInfo).toString()
                    // Removed bitmap loading to save RAM
                    val isSelected = savedSelected.contains(packageName)
                    val isFavorite = favoritePackages.contains(packageName)
                    
                    val installTime = packageInfo.firstInstallTime

                    temp.add(AppInfo(appName, packageName, isSelected, isSystemApp, isFavorite, installTime))
                }
                Triple(temp, savedActive, appsWereRemoved)
            }

            val builtList = result.first
            val cleanedActivePackages = result.second
            val appsWereRemoved = result.third

            activeFirewallPackages.clear()
            activeFirewallPackages.addAll(cleanedActivePackages)

            // If firewall is enabled but no packages are active (e.g. all uninstalled), disable it
            // In Adaptive Mode, we allow firewall to stay ON even with 0 active packages
            if (isFirewallEnabled && activeFirewallPackages.isEmpty() && !adaptiveMode) {
                isFirewallEnabled = false
                saveFirewallEnabled(false)
                // Update UI to reflect disabled state
                suppressToggleListener = true
                firewallToggle.isChecked = false
                suppressToggleListener = false
                appListAdapter.setSelectionEnabled(true)
                hideDimOverlay()
                
                if (appsWereRemoved) {
                    Toast.makeText(this@MainActivity, getString(R.string.firewall_disabled_active_uninstalled), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.firewall_disabled_no_apps), Toast.LENGTH_SHORT).show()
                }
            }

            // Only update if the list has changed to prevent UI sliding/glitches on resume
            if (appList != builtList) {
                appList.clear()
                appList.addAll(builtList)
                sortAndFilterApps(preserveScrollPosition = false)
            }
            
            updateSelectedCount()
        }
    }

    private fun saveSelectedApps() {
        val selectedPackages = appList
            .filter { it.isSelected && it.packageName != "moe.shizuku.privileged.api" }
            .map { it.packageName }
            .toSet()
        sharedPreferences.edit()
            .putStringSet(KEY_SELECTED_APPS, selectedPackages)
            .putInt(KEY_SELECTED_COUNT, selectedPackages.size)
            .apply()
    }

    private fun loadSelectedApps(): Set<String> {
        return sharedPreferences.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
    }

    private fun saveFirewallEnabled(enabled: Boolean) {
        // store a boot-relative timestamp when enabling so we can detect reboots
        val elapsed = if (enabled) SystemClock.elapsedRealtime() else -1L

        // Regular (credential-protected) prefs
        sharedPreferences.edit().apply {
            putBoolean(KEY_FIREWALL_ENABLED, enabled)
            if (enabled) putLong(KEY_FIREWALL_SAVED_ELAPSED, elapsed) else remove(KEY_FIREWALL_SAVED_ELAPSED)
            putLong(KEY_FIREWALL_UPDATE_TS, System.currentTimeMillis())
            apply()
        }

        // Also persist into device-protected storage so a direct-boot receiver can read it after reboot.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val dpCtx = createDeviceProtectedStorageContext()
                val dpPrefs = dpCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                dpPrefs.edit().apply {
                    putBoolean(KEY_FIREWALL_ENABLED, enabled)
                    if (enabled) putLong(KEY_FIREWALL_SAVED_ELAPSED, elapsed) else remove(KEY_FIREWALL_SAVED_ELAPSED)
                    putLong(KEY_FIREWALL_UPDATE_TS, System.currentTimeMillis()) 
                    apply()
                }
            } catch (e: Exception) {
                // ignore device-protected write failures
            }
        }

        // Notify widget to update
        val intent = Intent(this, FirewallWidgetProvider::class.java)
        intent.action = ACTION_FIREWALL_STATE_CHANGED
        sendBroadcast(intent)
    }

    private fun loadFirewallEnabled(): Boolean {
        val enabled = sharedPreferences.getBoolean(KEY_FIREWALL_ENABLED, false)
        if (!enabled) return false

        val savedElapsed = sharedPreferences.getLong(KEY_FIREWALL_SAVED_ELAPSED, -1L)
        if (savedElapsed == -1L) {
            // no timestamp — treat as disabled and clean up
            sharedPreferences.edit().remove(KEY_FIREWALL_ENABLED).apply()
            return false
        }

        val currentElapsed = SystemClock.elapsedRealtime()
        // if currentElapsed < savedElapsed a reboot happened -> clear saved state
        if (currentElapsed < savedElapsed) {
            sharedPreferences.edit()
                .remove(KEY_FIREWALL_ENABLED)
                .remove(KEY_FIREWALL_SAVED_ELAPSED)
                .apply()
            return false
        }

        return true
    }

    private fun saveActivePackages(packages: Set<String>) {
        sharedPreferences.edit().apply {
            putStringSet(KEY_ACTIVE_PACKAGES, packages.toSet())
            putLong(KEY_FIREWALL_UPDATE_TS, System.currentTimeMillis()) 
            apply()
        }
    }

    private fun loadActivePackages(): Set<String> {
        return sharedPreferences.getStringSet(KEY_ACTIVE_PACKAGES, emptySet()) ?: emptySet()
    }

    private fun applyFirewallState(enable: Boolean, packageNames: List<String>) {
        if (enable && packageNames.isEmpty() && !adaptiveMode) return
        firewallToggle.isEnabled = false
        lifecycleScope.launch {
            firewallProgress.visibility = android.view.View.VISIBLE
            if (enable && !adaptiveMode) {
                appListAdapter.setSelectionEnabled(false)
                updateInteractiveViews()
                showDimOverlay()
            }
            try {
                // perform package existence checks and run enable/disable on IO thread
                val (installed, missing) = withContext(Dispatchers.IO) {
                    filterInstalledPackages(packageNames)
                }

                // If enabling and none of the chosen packages remain installed -> abort
                // In Adaptive Mode, allow enabling with empty list
                if (enable && installed.isEmpty() && !adaptiveMode) {
                    Toast.makeText(this@MainActivity, getString(R.string.none_selected_apps_installed), Toast.LENGTH_SHORT).show()
                    suppressToggleListener = true
                    firewallToggle.isChecked = false
                    suppressToggleListener = false
                    appListAdapter.setSelectionEnabled(true)
                    updateInteractiveViews()
                    hideDimOverlay()
                    return@launch
                }

                // Inform about ignored (missing) packages when appropriate
                if (missing.isNotEmpty()) {
                    Toast.makeText(this@MainActivity, getString(R.string.selected_apps_not_installed, missing.size), Toast.LENGTH_SHORT).show()
                }

                val (successful, failed) = withContext(Dispatchers.IO) {
                    if (enable) {
                        enableFirewall(installed)
                    } else {
                        disableFirewall(installed)
                    }
                }

                // Handle successes
                if (enable) {
                    if (successful.isNotEmpty() || adaptiveMode) {
                        isFirewallEnabled = true
                        activeFirewallPackages.clear()
                        activeFirewallPackages.addAll(successful)
                        saveActivePackages(activeFirewallPackages)
                        saveFirewallEnabled(true)
                        // Ensure toggle stays ON
                        suppressToggleListener = true
                        firewallToggle.isChecked = true
                        suppressToggleListener = false
                        
                        if (adaptiveMode) {
                            appListAdapter.setSelectionEnabled(true)
                            updateInteractiveViews()
                            hideDimOverlay()
                        } else {
                            appListAdapter.setSelectionEnabled(false)
                            updateInteractiveViews()
                            showDimOverlay()
                        }
                    
                    val msg = if (successful.isEmpty()) getString(R.string.firewall_enabled_adaptive) else getString(R.string.firewall_enabled_for_apps, successful.size)
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                } else {
                    // None succeeded, revert toggle
                    suppressToggleListener = true
                    firewallToggle.isChecked = false
                    suppressToggleListener = false
                    Toast.makeText(this@MainActivity, getString(R.string.failed_to_enable_firewall), Toast.LENGTH_SHORT).show()
                    if (!adaptiveMode) {
                        appListAdapter.setSelectionEnabled(true)
                        updateInteractiveViews()
                        hideDimOverlay()
                    }
                }
            } else {
                if (successful.isNotEmpty()) {
                    activeFirewallPackages.removeAll(successful)
                    saveActivePackages(activeFirewallPackages)
                }

                // If we successfully unblocked apps OR there were no apps to unblock (e.g. Adaptive Mode empty, or all uninstalled)
                // We consider it disabled because disableFirewall() disables the global chain.
                    if (successful.isNotEmpty() || installed.isEmpty()) {
                    isFirewallEnabled = false
                    saveFirewallEnabled(false)
                    // Ensure toggle stays OFF
                    suppressToggleListener = true
                    firewallToggle.isChecked = false
                    suppressToggleListener = false
                    appListAdapter.setSelectionEnabled(true)
                    updateInteractiveViews()
                    hideDimOverlay()

                    if (installed.isEmpty()) {
                        activeFirewallPackages.clear()
                        saveActivePackages(activeFirewallPackages)
                    }
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.failed_to_disable_firewall), Toast.LENGTH_SHORT).show()
                }
            }

            // Handle failures: unselect failed apps and show error dialog
            if (failed.isNotEmpty()) {
                val skipErrorDialog = sharedPreferences.getBoolean(KEY_SKIP_ERROR_DIALOG, false)
                val keepErrorAppsSelected = sharedPreferences.getBoolean(KEY_KEEP_ERROR_APPS_SELECTED, false)
                
                // Only unselect if user hasn't opted to keep them selected
                if (!(skipErrorDialog && keepErrorAppsSelected)) {
                    for (pkg in failed) {
                        val idx = appList.indexOfFirst { it.packageName == pkg }
                        if (idx != -1) {
                            appList[idx] = appList[idx].copy(isSelected = false)
                        }
                    }
                    updateSelectedCount()
                    saveSelectedApps()
                    sortAndFilterApps(preserveScrollPosition = false)
                }
                showOperationErrorsDialog(failed, lastOperationErrorDetails)
            }
            } finally {
                firewallProgress.visibility = android.view.View.GONE
                firewallToggle.isEnabled = true
            }
        }
    }

    private fun filterInstalledPackages(packageNames: List<String>): Pair<List<String>, List<String>> {
        val installed = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val pm = packageManager
        for (pkg in packageNames) {
            // Treat Shizuku packages as "missing" / never-operable
            if (pkg == "moe.shizuku.privileged.api") {
                missing.add(pkg)
                continue
            }
            try {
                pm.getPackageInfo(pkg, 0)
                installed.add(pkg)
            } catch (e: PackageManager.NameNotFoundException) {
                missing.add(pkg)
            } catch (e: Exception) {
                // defensively treat errors as missing
                missing.add(pkg)
            }
        }
        return Pair(installed, missing)
    }

    private suspend fun enableFirewall(packageNames: List<String>): Pair<List<String>, List<String>> {
        val successful = mutableListOf<String>()
        val failed = mutableListOf<String>()
        lastOperationErrorDetails.clear()

        val chain3Result = runCommandDetailed("cmd connectivity set-chain3-enabled true")
        if (!chain3Result.success) {
            // If chain3 enable fails, all packages fail; record the error message once with a key like "_chain3"
            val msg = chain3Result.stderr.ifEmpty { chain3Result.stdout }
            for (pkg in packageNames) {
                lastOperationErrorDetails[pkg] = msg
                failed.add(pkg)
            }
            return Pair(successful, failed)
        }
        for (packageName in packageNames) {
            val res = runCommandDetailed("cmd connectivity set-package-networking-enabled false $packageName")
            if (res.success) {
                successful.add(packageName)
            } else {
                failed.add(packageName)
                lastOperationErrorDetails[packageName] = res.stderr.ifEmpty { res.stdout }
            }
        }
        // No rollback; allow partial success
        return Pair(successful, failed)
    }

    private suspend fun disableFirewall(packageNames: List<String>): Pair<List<String>, List<String>> {
        val successful = mutableListOf<String>()
        val failed = mutableListOf<String>()
        lastOperationErrorDetails.clear()
        for (packageName in packageNames) {
            val res = runCommandDetailed("cmd connectivity set-package-networking-enabled true $packageName")
            if (res.success) {
                successful.add(packageName)
            } else {
                failed.add(packageName)
                lastOperationErrorDetails[packageName] = res.stderr.ifEmpty { res.stdout }
            }
        }
        // Disable chain3 regardless of individual results
        runCommandDetailed("cmd connectivity set-chain3-enabled false")
        return Pair(successful, failed)
    }


    private suspend fun runCommandDetailed(command: String): com.arslan.shizuwall.shell.ShellResult {
        return ShellExecutorProvider.forContext(this).exec(command)
    }

    private suspend fun runCommand(command: String): Boolean {
        return runCommandDetailed(command).success
    }

    // dim only the RecyclerView and disable its interactions
    private fun showDimOverlay() {
        // visually dim RecyclerView and block interactions
        if (adaptiveMode) return // do not dim in Adaptive Mode

        recyclerView.alpha = 0.5f
        recyclerView.isEnabled = false
        recyclerView.isClickable = false
        appListAdapter.setSelectionEnabled(false)
        updateInteractiveViews()
    }

    private fun hideDimOverlay() {
        recyclerView.alpha = 1.0f
        recyclerView.isEnabled = true
        recyclerView.isClickable = true
        appListAdapter.setSelectionEnabled(true)
        updateInteractiveViews()
    }

    // Called by packageBroadcastReceiver when a package is removed.
    private fun handlePackageRemoved(pkg: String) {
        runOnUiThread {
            var changed = false
            val it = appList.iterator()
            while (it.hasNext()) {
                val ai = it.next()
                if (ai.packageName == pkg) {
                    it.remove()
                    changed = true
                }
            }
            if (changed) {
                // update filtered list and UI
                filteredAppList.removeAll { it.packageName == pkg }

                // Remove from active firewall set and persist
                activeFirewallPackages.remove(pkg)
                saveActivePackages(activeFirewallPackages)

                // Remove from selected set and persist
                val currentSelected = sharedPreferences.getStringSet(KEY_SELECTED_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
                if (currentSelected.remove(pkg)) {
                    sharedPreferences.edit().apply {
                        putStringSet(KEY_SELECTED_APPS, currentSelected)
                        putInt(KEY_SELECTED_COUNT, currentSelected.size)
                        apply()
                    }
                }

                updateSelectedCount()
                sortAndFilterApps(preserveScrollPosition = false)
            }
        }
    }

    // Called by packageBroadcastReceiver when a package is added/updated.
    private fun handlePackageAdded(pkg: String) {
        lifecycleScope.launch {
            val maybeApp = withContext(Dispatchers.IO) {
                try {
                    val pm = packageManager
                    val pi = pm.getPackageInfo(pkg, 0)
                    val ai = pi.applicationInfo ?: return@withContext null
                    if (!ai.enabled) return@withContext null
                    val isSystemApp = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (!showSystemApps && isSystemApp) return@withContext null
                    val hasInternet = pm.checkPermission(Manifest.permission.INTERNET, pkg) == PackageManager.PERMISSION_GRANTED
                    if (!hasInternet) return@withContext null
                    val appName = pm.getApplicationLabel(ai).toString()
                    val isSelected = loadSelectedApps().contains(pkg)
                    val isFavorite = loadFavoriteApps().contains(pkg)
                    val installTime = pi.firstInstallTime
                    AppInfo(appName, pkg, isSelected, isSystemApp, isFavorite, installTime)
                } catch (e: Exception) {
                    null
                }
            }
            maybeApp?.let { appInfo ->
                // skip Shizuku packages entirely
                if (appInfo.packageName == "moe.shizuku.privileged.api") return@let

                // Avoid duplicates (in case it was already present)
                if (appList.any { it.packageName == appInfo.packageName }) return@let
                appList.add(appInfo)
                sortAndFilterApps(preserveScrollPosition = false)
                updateSelectedCount()
            }
        }
    }

    private fun updateCategoryChips() {
        // guard: views may not be initialized in some lifecycle flows
        val categoryGroup = findViewById<ChipGroup?>(R.id.categoryChipGroup) ?: return
        val chipSystem = findViewById<Chip?>(R.id.chip_system)
        val chipSelected = findViewById<Chip?>(R.id.chip_selected)
        val chipUnselected = findViewById<Chip?>(R.id.chip_unselected)

        chipSystem?.visibility = if (showSystemApps) View.VISIBLE else View.GONE

        chipSelected?.visibility = if (moveSelectedTop) View.GONE else View.VISIBLE

        // if we hid the system chip and it was selected, clear the selection (do NOT switch to a removed default)
        if (!showSystemApps && categoryGroup.checkedChipId == R.id.chip_system) {
            categoryGroup.clearCheck()
            currentCategory = Category.NONE
            sortAndFilterApps(preserveScrollPosition = false)
        }
        if (moveSelectedTop && (categoryGroup.checkedChipId == R.id.chip_selected || categoryGroup.checkedChipId == R.id.chip_unselected)) {
            categoryGroup.clearCheck()
            currentCategory = Category.NONE
            sortAndFilterApps(preserveScrollPosition = false)
        }
    }

    private fun showOperationErrorsDialog(failedPackages: List<String>, errorDetails: Map<String, String> = emptyMap()) {
        val failedApps = appList.filter { it.packageName in failedPackages }
        if (failedApps.isEmpty()) return

        // Check if user opted to skip error dialogs
        if (sharedPreferences.getBoolean(KEY_SKIP_ERROR_DIALOG, false)) {
            Toast.makeText(this, getString(R.string.operation_failed_for_apps, failedApps.size), Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_firewall_confirm, null)
        val builder = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton(getString(R.string.ok), null)

        // show details button only if we have any details for the failed packages
        val hasDetails = failedPackages.any { errorDetails[it]?.isNotEmpty() == true } || errorDetails.containsKey("_chain3")
        if (hasDetails) {
            builder.setNeutralButton(getString(R.string.details)) { _, _ ->
                showErrorDetailsDialog(failedPackages, errorDetails)
            }
        }
        val dialog = builder.create()
        applyFontToViews(dialogView)
        dialog.setOnShowListener {
            dialog.window?.decorView?.let { applyFontToViews(it) }
        }

        val dialogMessage = dialogView.findViewById<TextView>(R.id.dialogMessage)
        val selectedAppsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.selectedAppsRecyclerView)

        dialogMessage.text = getString(R.string.operation_failed_message, failedApps.size)

        // If any error detail indicates the chain3 command is missing, append a short hint
        val chain3Msg = errorDetails["_chain3"] ?: errorDetails.values.firstOrNull { it.contains("no command found set chain 3", ignoreCase = true) }
        if (chain3Msg != null) {
            dialogMessage.append("\n\n${getString(R.string.android11_unsupported_hint)}")
        }

        selectedAppsRecyclerView.layoutManager = LinearLayoutManager(this)
        selectedAppsRecyclerView.adapter = SelectedAppsAdapter(failedApps, getSelectedTypeface())

        // Limit the RecyclerView height to a fraction of the screen
        val displayMetrics = resources.displayMetrics
        val displayHeight = displayMetrics.heightPixels
        val maxRecyclerHeight = (displayHeight * 0.4).toInt() // 40% of screen height
        
        val estimatedItemHeight = (72 * displayMetrics.density).toInt()
        val estimatedContentHeight = estimatedItemHeight * failedApps.size
        
        val lp = selectedAppsRecyclerView.layoutParams
        if (estimatedContentHeight > maxRecyclerHeight) {
            lp.height = maxRecyclerHeight
        } else {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        selectedAppsRecyclerView.layoutParams = lp
        selectedAppsRecyclerView.isNestedScrollingEnabled = false

        dialog.show()
    }

    private fun showErrorDetailsDialog(failedPackages: List<String>, errorDetails: Map<String, String> = emptyMap()) {
        val failedApps = appList.filter { it.packageName in failedPackages }
        if (failedApps.isEmpty()) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_error_details, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        applyFontToViews(dialogView)
        dialog.setOnShowListener {
            dialog.window?.decorView?.let { applyFontToViews(it) }
        }

        val messageView = dialogView.findViewById<TextView>(R.id.dialogErrorDetailsMessage)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.errorDetailsRecyclerView)
        val btnCopy = dialogView.findViewById<android.widget.Button>(R.id.btnCopy)
        val btnClose = dialogView.findViewById<android.widget.Button>(R.id.btnClose)

        messageView.text = getString(R.string.operation_failed_message, failedApps.size)

        val list = failedApps.map { ai ->
            val err = errorDetails[ai.packageName] ?: errorDetails["_chain3"] ?: getString(R.string.no_error_details)
            ErrorEntry(ai.appName, ai.packageName, err)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ErrorDetailsAdapter(list, getSelectedTypeface())

        btnCopy.setOnClickListener {
            val sb = StringBuilder()
            for (entry in list) {
                sb.append(entry.appName)
                sb.append(" (")
                sb.append(entry.packageName)
                sb.append("):\n")
                sb.append(entry.errorText)
                sb.append("\n\n")
            }
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("error_details", sb.toString())
            cm.setPrimaryClip(clip)
            Toast.makeText(this, getString(R.string.copy) + "!", Toast.LENGTH_SHORT).show()
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        // continue was removed — close handles dialog dismissal

        dialog.show()
    }

    private fun applyFontToViews(view: View) {
        val savedFont = sharedPreferences.getString(KEY_SELECTED_FONT, "default") ?: "default"
        if (savedFont == "default") return
        
        val typeface = try {
            ResourcesCompat.getFont(this, R.font.ndot)
        } catch (e: Exception) {
            return
        }
        
        applyTypefaceRecursively(view, typeface)
    }

    private fun applyTypefaceRecursively(view: View, typeface: Typeface?) {
        if (typeface == null) return
        
        when (view) {
            is TextView -> {
                view.typeface = typeface
            }
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    applyTypefaceRecursively(view.getChildAt(i), typeface)
                }
            }
        }
    }

    private fun getSelectedTypeface(): Typeface? {
        val savedFont = sharedPreferences.getString(KEY_SELECTED_FONT, "default") ?: "default"
        if (savedFont == "default") return null
        
        return try {
            ResourcesCompat.getFont(this, R.font.ndot)
        } catch (e: Exception) {
            null
        }
    }
}