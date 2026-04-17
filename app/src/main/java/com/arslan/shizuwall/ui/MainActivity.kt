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
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.arslan.shizuwall.services.AppMonitorService
import com.arslan.shizuwall.services.ForegroundDetectionService
import com.arslan.shizuwall.services.ScreenLockMonitorService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.ChipGroup
import com.google.android.material.chip.Chip
import com.arslan.shizuwall.adapters.AppListAdapter
import com.arslan.shizuwall.adapters.SelectedAppsAdapter
import com.arslan.shizuwall.adapters.ErrorDetailsAdapter
import com.arslan.shizuwall.model.AppInfo
import com.arslan.shizuwall.widgets.FirewallWidgetProvider
import com.arslan.shizuwall.repo.FirewallStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import androidx.appcompat.widget.SwitchCompat
import com.arslan.shizuwall.R
import kotlin.comparisons.*
import com.arslan.shizuwall.adapters.ErrorEntry
import com.arslan.shizuwall.FirewallMode
import com.arslan.shizuwall.WorkingMode
import com.arslan.shizuwall.shizuku.ShizukuSetupActivity
import com.arslan.shizuwall.shell.RootShellExecutor
import com.arslan.shizuwall.shell.ShellExecutorProvider
import com.arslan.shizuwall.receivers.ScreenLockModeReceiver
import com.arslan.shizuwall.utils.ShizukuPackageResolver
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.radiobutton.MaterialRadioButton
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : BaseActivity() {
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
        const val KEY_SKIP_ENABLE_CONFIRM = "skip_enable_confirm" 
        const val KEY_SKIP_ERROR_DIALOG = "skip_error_dialog"
        const val KEY_SKIP_ANDROID11_INFO = "skip_android11_info"
        const val KEY_KEEP_ERROR_APPS_SELECTED = "keep_error_apps_selected"
        const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
        const val KEY_MOVE_SELECTED_TOP = "move_selected_top"
        const val KEY_SELECTED_FONT = "selected_font"
        const val KEY_USE_DYNAMIC_COLOR = "use_dynamic_color"
        const val KEY_USE_AMOLED_BLACK = "use_amoled_black"
        const val KEY_ADAPTIVE_MODE = "adaptive_mode" 
        const val KEY_FIREWALL_MODE = "firewall_mode" 
        const val KEY_SCREEN_LOCK_DELAY_SECONDS = "screen_lock_delay_seconds"
        const val DEFAULT_SCREEN_LOCK_DELAY_SECONDS = 2
        const val KEY_SMART_FOREGROUND_APP = "smart_foreground_app"  // Current foreground app in smart mode
        const val KEY_LAST_FOREGROUND_APP = "last_foreground_app"
        const val KEY_FIREWALL_INDICATOR_ENABLED = "firewall_indicator_enabled"
        const val KEY_FIREWALL_INDICATOR_X = "firewall_indicator_x"
        const val KEY_FIREWALL_INDICATOR_Y = "firewall_indicator_y"
        const val KEY_FIREWALL_INDICATOR_SIZE = "firewall_indicator_size"
        const val KEY_AUTO_ENABLE_ON_SHIZUKU_START = "auto_enable_on_shizuku_start"
        const val KEY_APPLY_ROOT_RULES_AFTER_REBOOT = "apply_root_rules_after_reboot"
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
        const val KEY_APP_MODES = "app_modes_json"
        private const val KEY_APPS_CACHE_JSON = "apps_cache_json_v1"


    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var appListAdapter: AppListAdapter
    private lateinit var firewallToggle: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var firewallProgress: android.widget.ProgressBar
    private lateinit var searchView: SearchView
    private lateinit var selectedCountText: TextView
    private lateinit var appListLoadingContainer: View
    private val appList = mutableListOf<AppInfo>()
    private val filteredAppList = mutableListOf<AppInfo>()
    private var isFirewallEnabled = false
    private var currentQuery = ""
    private var showSystemApps = false 
    private var moveSelectedTop = true
    private var firewallMode = FirewallMode.DEFAULT
    private enum class SortOrder { NAME_ASC, NAME_DESC, INSTALL_TIME }
    private var currentSortOrder = SortOrder.NAME_ASC

    private lateinit var sharedPreferences: SharedPreferences

    private var firewallRepo: FirewallStateRepository? = null
    private var shizukuListenersAdded = false

    private var defaultItemAnimator: RecyclerView.ItemAnimator? = null
    private var isAppListLoadingVisible = false
    private var isFirewallProcessRunning = false
    private var isEnablingProcess = false
    private var lastSelectedCountToastTime = 0L

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        onRequestPermissionsResult(requestCode, grantResult)
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        val workingMode = sharedPreferences.getString(KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"
        if (workingMode != WorkingMode.SHIZUKU.name) return@OnBinderReceivedListener

        val autoEnable = sharedPreferences.getBoolean(KEY_AUTO_ENABLE_ON_SHIZUKU_START, false)
        if (!autoEnable) {
            checkShizukuPermission()
            return@OnBinderReceivedListener
        }

        // If already enabled, nothing to do
        if (isFirewallEnabled) return@OnBinderReceivedListener

        // Use coroutine to handle auto-enable with proper timing
        lifecycleScope.launch(Dispatchers.IO) {
            // Wait a bit for Shizuku binder to fully initialize
            var binderReady = false
            for (attempt in 1..10) {
                try {
                    if (Shizuku.pingBinder()) {
                        binderReady = true
                        break
                    }
                } catch (_: Throwable) {}
                if (attempt < 10) delay(300L)
            }

            if (!binderReady) {
                withContext(Dispatchers.Main) { checkShizukuPermission() }
                return@launch
            }

            val selectedPkgs = loadSelectedApps().toList()
            if (selectedPkgs.isEmpty() && !firewallMode.allowsDynamicSelection()) {
                // Nothing to enable (and adaptive/smart mode does not allow empty set)
                return@launch
            }

            val hasPermission = try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }

            withContext(Dispatchers.Main) {
                if (!hasPermission) {
                    pendingAutoEnable = true
                    pendingAutoEnableSelectedApps = selectedPkgs
                    try {
                        Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                    } catch (_: Exception) {
                        checkShizukuPermission()
                    }
                    return@withContext
                }

                val skipConfirm = sharedPreferences.getBoolean(KEY_SKIP_ENABLE_CONFIRM, false)
                val targetPkgs = getTargetPackagesToBlock(selectedPkgs)
                if (skipConfirm) {
                    applyFirewallState(true, targetPkgs)
                    return@withContext
                }

                val selectedAppsList = appList.filter { targetPkgs.contains(it.packageName) }
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    showFirewallConfirmDialog(if (selectedAppsList.isNotEmpty()) selectedAppsList else emptyList())
                } else {
                    pendingAutoEnable = true
                    pendingAutoEnableSelectedApps = selectedPkgs
                }
            }
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        val workingMode = sharedPreferences.getString(KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"
        if (workingMode != WorkingMode.SHIZUKU.name) return@OnBinderDeadListener

        Toast.makeText(this, getString(R.string.shizuku_service_dead), Toast.LENGTH_SHORT).show()
        finish()
    }

    private var suppressToggleListener = false
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
            firewallMode = FirewallMode.fromName(sharedPreferences.getString(KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name))
            updateFirewallToggleThumbIcon()
            loadInstalledApps()
            updateCategoryChips()
            
            if (isFirewallEnabled) {
                if (firewallMode.allowsDynamicSelection()) hideDimOverlay() else showDimOverlay()
                appListAdapter.setSelectionEnabled(firewallMode.allowsDynamicSelection() || !isFirewallEnabled)
                updateInteractiveViews()
            }
            appListAdapter.setHybridModeEnabled(firewallMode == FirewallMode.HYBRID)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
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

        // Start Foreground Firewall Indicator if enabled
        if (sharedPreferences.getBoolean(KEY_FIREWALL_INDICATOR_ENABLED, false)) {
            com.arslan.shizuwall.services.ForegroundFirewallIndicatorService.start(this)
        }

        // Start Floating Button Service if enabled
        if (sharedPreferences.getBoolean(com.arslan.shizuwall.services.FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, false)) {
            com.arslan.shizuwall.services.FloatingButtonService.start(this)
        }


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

        val settingsButton: View? = findViewById(R.id.settingsButton)
        settingsButton?.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            settingsLauncher.launch(intent)
        }

        val sortButton: View? = findViewById(R.id.sortButton)
        sortButton?.setOnClickListener {
            showSortDialog()
        }

        showSystemApps = sharedPreferences.getBoolean(KEY_SHOW_SYSTEM_APPS, false)
        moveSelectedTop = sharedPreferences.getBoolean(KEY_MOVE_SELECTED_TOP, true)
        firewallMode = FirewallMode.fromName(sharedPreferences.getString(KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name))
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

                        appListAdapter.setSelectionEnabled(!state.enabled || firewallMode.allowsDynamicSelection())
                        applyListInteractionState()

                        updateSelectedCount()
                        updateSelectAllCheckbox()
                        updateInteractiveViews()
                    }
                }
            }
        }

        setupFirewallToggle()
        updateFirewallToggleThumbIcon()
        setupSearchView()
        setupSelectAllCheckbox()
        setupRecyclerView()

        val scrollTopButton: View? = findViewById(R.id.scrollTopButton)
        scrollTopButton?.setOnClickListener {
            if (filteredAppList.isNotEmpty()) {
                recyclerView.smoothScrollToPosition(0)
            }
        }

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

        val hasWarmCache = restoreAppListFromCache()
        loadInstalledApps(showLoadingIfListEmpty = !hasWarmCache)

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
                            if (pkgs.isNotEmpty() || firewallMode.allowsDynamicSelection()) {
                                val targetPkgs = getTargetPackagesToBlock(pkgs)
                                if (sharedPreferences.getBoolean(KEY_SKIP_ENABLE_CONFIRM, false)) {
                                    applyFirewallState(true, targetPkgs)
                                } else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                    val selectedAppsList = appList.filter { targetPkgs.contains(it.packageName) }
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
        appListAdapter.setSelectionEnabled(!isFirewallEnabled || firewallMode.allowsDynamicSelection())
        updateInteractiveViews()
        applyListInteractionState()

        // ensure the toggle is disabled if firewall is off AND there are no selected apps
        // (allows the toggle to remain enabled when firewall is active)
        if (::firewallToggle.isInitialized) {
            firewallToggle.isEnabled = isFirewallEnabled || savedCount > 0
        }

        // "Press back again to exit" confirmation dialog
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            private var backPressedOnce = false

            override fun handleOnBackPressed() {
                if (backPressedOnce) {
                    // Second press within the window – exit the app
                    finish()
                    return
                }

                backPressedOnce = true
                Toast.makeText(this@MainActivity, getString(R.string.exit_confirm_message), Toast.LENGTH_SHORT).show()

                // Reset the flag after 2 seconds
                recyclerView.postDelayed({ backPressedOnce = false }, 2000)
            }
        })

    }

    override fun onResume() {
        super.onResume()

        // If views were not initialized (e.g. onCreate returned early), avoid touching them.
        if (!::firewallToggle.isInitialized) {
            return
        }

        // Re-sync toggle with saved state without triggering listener
        if (!isFirewallProcessRunning) {
            suppressToggleListener = true
            firewallToggle.isChecked = loadFirewallEnabled()
            suppressToggleListener = false
        }

        // Update firewallMode from preferences
        firewallMode = FirewallMode.fromName(sharedPreferences.getString(KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name))
        updateFirewallToggleThumbIcon()

        // Reflect current firewall state in UI
        if (!isFirewallProcessRunning) {
            val enabled = loadFirewallEnabled()
            appListAdapter.setSelectionEnabled(!enabled || firewallMode.allowsDynamicSelection())
            updateInteractiveViews()
            isFirewallEnabled = enabled
            applyListInteractionState()
        } else {
            showDimOverlay(force = true)
            suppressToggleListener = true
            firewallToggle.isChecked = isEnablingProcess
            suppressToggleListener = false
        }
        appListAdapter.setHybridModeEnabled(firewallMode == FirewallMode.HYBRID)
        loadInstalledApps()
        
        // Auto-enable accessibility service if revoked (e.g. after debug APK reinstall)
        val isIndicatorEnabled = sharedPreferences.getBoolean(KEY_FIREWALL_INDICATOR_ENABLED, false)
        if (firewallMode == FirewallMode.SMART_FOREGROUND || firewallMode == FirewallMode.HYBRID || isIndicatorEnabled) {
            if (!ForegroundDetectionService.isServiceEnabled(this)) {
                // Try to auto-enable via Shizuku/LADB shell
                lifecycleScope.launch {
                    val success = ForegroundDetectionService.enableServiceViaShell(this@MainActivity)
                    if (success) {
                        Toast.makeText(this@MainActivity, getString(R.string.accessibility_auto_enabled), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, getString(R.string.accessibility_manual_enable_needed), Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            if (ForegroundDetectionService.isServiceEnabled(this)) {
                lifecycleScope.launch {
                    ForegroundDetectionService.disableServiceViaShell(this@MainActivity)
                }
            }
        }

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

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            if (::searchView.isInitialized && searchView.hasFocus()) {
                val rect = Rect()
                searchView.getGlobalVisibleRect(rect)
                if (!rect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    searchView.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(searchView.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
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
                        if (pkgs.isNotEmpty() || firewallMode.allowsDynamicSelection()) {
                            val targetPkgs = getTargetPackagesToBlock(pkgs)
                            val selectedApps = appList.filter { targetPkgs.contains(it.packageName) }
                            if (sharedPreferences.getBoolean(KEY_SKIP_ENABLE_CONFIRM, false)) {
                                applyFirewallState(true, targetPkgs)
                            } else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                // Show confirmation dialog in foreground
                                if (selectedApps.isNotEmpty() || firewallMode.allowsDynamicSelection()) {
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
        if (workingMode == WorkingMode.ROOT.name) {
            if (RootShellExecutor.hasRootAccess()) return true

            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.working_mode_root))
                .setMessage(getString(R.string.root_not_found_message))
                .setPositiveButton(getString(R.string.ok), null)
                .setCancelable(true)
                .show()
            return false
        }

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
                         val candidates = ShizukuPackageResolver.getLaunchCandidates(this@MainActivity)
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

        // Hide keyboard when SearchView loses focus
        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                try {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(searchView.windowToken, 0)
                } catch (_: Exception) { }
            }
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentQuery = newText ?: ""
                filterApps(currentQuery)

                // Disable animator to prevent visual clutter during search filtering
                if (::recyclerView.isInitialized) recyclerView.itemAnimator = null
                appListAdapter.submitList(filteredAppList.toList()) {
                    if (::recyclerView.isInitialized) recyclerView.itemAnimator = defaultItemAnimator
                    updateSelectedCount()
                }
                return true
            }
        })


    }

    private fun setupSelectAllCheckbox() {
        selectedCountText = findViewById(R.id.selectedCountText)

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!selectedCountText.isEnabled) return true
                
                val currentTime = SystemClock.elapsedRealtime()
                if (currentTime - lastSelectedCountToastTime < 4000) return true
                lastSelectedCountToastTime = currentTime

                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.double_tap_or_long_press),
                    Toast.LENGTH_SHORT
                ).show()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!selectedCountText.isEnabled) return true

                if (firewallMode.allowsDynamicSelection() && isFirewallEnabled
                    && !checkPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                ) return true

                val allSelected = filteredAppList.all { it.isSelected }
                if (allSelected) return true

                // Show confirmation dialog before selecting all
                showSelectAllConfirmDialog()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (!selectedCountText.isEnabled) return
                if (appList.any { it.isSelected }) {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(getString(R.string.unselect_all))
                        .setMessage(getString(R.string.deselect_all_apps))
                        .setPositiveButton(getString(R.string.unselect)) { _, _ ->
                            val previouslySelected = appList.filter { it.isSelected }.map { it.packageName }
                            for (i in appList.indices) appList[i] = appList[i].copy(isSelected = false)
                            updateSelectedCount()
                            saveSelectedApps()
                            sortAndFilterApps(preserveScrollPosition = true)

                            if (isFirewallEnabled && firewallMode.allowsDynamicSelection() && previouslySelected.isNotEmpty()) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val successful = mutableListOf<String>()
                                    val failed = mutableListOf<String>()
                                    lastOperationErrorDetails.clear()
                                    for (pkg in previouslySelected) {
                                        val shouldBlock = firewallMode == FirewallMode.WHITELIST
                                        val cmd = if (shouldBlock) "cmd connectivity set-package-networking-enabled false $pkg"
                                                  else "cmd connectivity set-package-networking-enabled true $pkg"
                                        val res = runCommandDetailed(cmd)
                                        if (res.success) successful.add(pkg)
                                        else {
                                            failed.add(pkg)
                                            lastOperationErrorDetails[pkg] = res.stderr.ifEmpty { res.stdout }
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        val shouldBlockMain = firewallMode == FirewallMode.WHITELIST
                                        if (successful.isNotEmpty()) {
                                            if (shouldBlockMain) activeFirewallPackages.addAll(successful)
                                            else activeFirewallPackages.removeAll(successful)
                                            saveActivePackages(activeFirewallPackages)
                                        }
                                        if (failed.isNotEmpty()) {
                                            Toast.makeText(this@MainActivity, getString(R.string.failed_to_unblock_count, failed.size), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                }
            }
        })

        selectedCountText.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            // consume only if enabled so ripple still works
            if (!selectedCountText.isEnabled) false else {
                v.performClick()
                true
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        appListLoadingContainer = findViewById(R.id.appListLoadingContainer)
        recyclerView.layoutManager = LinearLayoutManager(this)
        appListAdapter = AppListAdapter(
            onAppClick = { appInfo ->
                if (firewallMode.allowsDynamicSelection() && isFirewallEnabled && !checkPermission(SHIZUKU_PERMISSION_REQUEST_CODE)) {
                    appListAdapter.notifyDataSetChanged()
                    return@AppListAdapter
                }

                val idx = appList.indexOfFirst { it.packageName == appInfo.packageName }
                if (idx != -1) {
                    appList[idx] = appInfo
                }
                updateSelectedCount()
                saveSelectedApps()
                sortAndFilterApps(preserveScrollPosition = true)

                // Apply rule immediately if firewall is enabled
                if (isFirewallEnabled && firewallMode.allowsDynamicSelection()) {
                    val pkg = appInfo.packageName
                    val isSelected = appInfo.isSelected
                    lifecycleScope.launch(Dispatchers.IO) {
                        lastOperationErrorDetails.clear()
                        val shouldBlock = if (firewallMode == FirewallMode.WHITELIST) !isSelected else isSelected
                        val res = if (shouldBlock) {
                                runCommandDetailed("cmd connectivity set-package-networking-enabled false $pkg")
                            } else {
                                runCommandDetailed("cmd connectivity set-package-networking-enabled true $pkg")
                            }
                        val success = res.success
                        
                        withContext(Dispatchers.Main) {
                            if (success) {
                                if (shouldBlock) {
                                    activeFirewallPackages.add(pkg)
                                } else {
                                    activeFirewallPackages.remove(pkg)
                                }
                                saveActivePackages(activeFirewallPackages)
                            } else {
                                // Operation failed
                                if (shouldBlock) {
                                    // Failed to block
                                    val skipErrorDialog = sharedPreferences.getBoolean(KEY_SKIP_ERROR_DIALOG, false)
                                    val keepErrorAppsSelected = sharedPreferences.getBoolean(KEY_KEEP_ERROR_APPS_SELECTED, false)

                                    if (!(skipErrorDialog && keepErrorAppsSelected)) {
                                        val revertIdx = appList.indexOfFirst { it.packageName == pkg }
                                        if (revertIdx != -1) {
                                            appList[revertIdx] = appList[revertIdx].copy(isSelected = !isSelected)
                                        }
                                        updateSelectedCount()
                                        saveSelectedApps()
                                        sortAndFilterApps(preserveScrollPosition = true)
                                    }
                                    lastOperationErrorDetails[pkg] = res.stderr.ifEmpty { res.stdout }
                                    showOperationErrorsDialog(listOf(pkg), lastOperationErrorDetails)
                                } else {
                                    // Failed to unblock
                                    val revertIdx = appList.indexOfFirst { it.packageName == pkg }
                                    if (revertIdx != -1) {
                                        appList[revertIdx] = appList[revertIdx].copy(isSelected = !isSelected)
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
            }
        )
        appListAdapter.setHybridModeEnabled(firewallMode == FirewallMode.HYBRID)
        recyclerView.adapter = appListAdapter
        defaultItemAnimator = recyclerView.itemAnimator


    }

    private fun showSelectAllConfirmDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_all))
            .setMessage(getString(R.string.select_all_apps_confirm, filteredAppList.count { !it.isSelected }))
            .setPositiveButton(getString(R.string.select)) { _, _ ->
                val isChecked = true
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

                    if (isFirewallEnabled && firewallMode.allowsDynamicSelection()) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val successful = mutableListOf<String>()
                            val failed = mutableListOf<String>()
                            lastOperationErrorDetails.clear()
                            for (pkg in packagesToUpdate) {
                                val shouldBlock = if (firewallMode == FirewallMode.WHITELIST) !isChecked else isChecked
                                val cmd = if (shouldBlock) {
                                    "cmd connectivity set-package-networking-enabled false $pkg"
                                } else {
                                    "cmd connectivity set-package-networking-enabled true $pkg"
                                }
                                val res = runCommandDetailed(cmd)
                                if (res.success) successful.add(pkg)
                                else {
                                    failed.add(pkg)
                                    lastOperationErrorDetails[pkg] = res.stderr.ifEmpty { res.stdout }
                                }
                            }
                            withContext(Dispatchers.Main) {
                                val shouldBlockMain = if (firewallMode == FirewallMode.WHITELIST) !isChecked else isChecked
                                if (successful.isNotEmpty()) {
                                    if (shouldBlockMain) activeFirewallPackages.addAll(successful)
                                    else activeFirewallPackages.removeAll(successful)
                                    saveActivePackages(activeFirewallPackages)
                                }
                                if (failed.isNotEmpty()) {
                                    if (shouldBlockMain) {
                                        val skipErrorDialog = sharedPreferences.getBoolean(KEY_SKIP_ERROR_DIALOG, false)
                                        val keepErrorAppsSelected = sharedPreferences.getBoolean(KEY_KEEP_ERROR_APPS_SELECTED, false)
                                        if (!(skipErrorDialog && keepErrorAppsSelected)) {
                                            for (pkg in failed) {
                                                val idx = appList.indexOfFirst { it.packageName == pkg }
                                                if (idx != -1) appList[idx] = appList[idx].copy(isSelected = !isChecked)
                                            }
                                            updateSelectedCount()
                                            saveSelectedApps()
                                            sortAndFilterApps(preserveScrollPosition = true)
                                        }
                                        showOperationErrorsDialog(failed, lastOperationErrorDetails)
                                    } else {
                                        for (pkg in failed) {
                                            val idx = appList.indexOfFirst { it.packageName == pkg }
                                            if (idx != -1) appList[idx] = appList[idx].copy(isSelected = !isChecked)
                                        }
                                        updateSelectedCount()
                                        saveSelectedApps()
                                        sortAndFilterApps(preserveScrollPosition = true)
                                        Toast.makeText(this@MainActivity, getString(R.string.failed_to_update_rules_count, failed.size), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
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
            Category.NONE -> appList
            Category.FAVORITES -> appList.filter { it.isFavorite }
            Category.SYSTEM -> appList.filter { it.isSystem }
            Category.USER -> appList.filter { !it.isSystem }
            Category.SELECTED -> appList.filter { it.isSelected }
            Category.UNSELECTED -> appList.filter { !it.isSelected }
        }.filter { app ->
            if (currentCategory == Category.SYSTEM) true
            else showSystemApps || !app.isSystem
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
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_sort, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupSort)
        val checkboxShowSystem = dialogView.findViewById<MaterialCheckBox>(R.id.checkboxShowSystemApps)

        // Set current sort order
        when (currentSortOrder) {
            SortOrder.INSTALL_TIME -> radioGroup.check(R.id.radioInstallTime)
            SortOrder.NAME_ASC -> radioGroup.check(R.id.radioNameAsc)
            SortOrder.NAME_DESC -> radioGroup.check(R.id.radioNameDesc)
        }

        // Set current show system apps state
        checkboxShowSystem.isChecked = showSystemApps
        
        // Disable show system apps checkbox when firewall is enabled to prevent list changes during active filtering
        if (isFirewallEnabled) {
            checkboxShowSystem.isEnabled = false
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // Handle sort order change
                val newSortOrder = when (radioGroup.checkedRadioButtonId) {
                    R.id.radioInstallTime -> SortOrder.INSTALL_TIME
                    R.id.radioNameAsc -> SortOrder.NAME_ASC
                    R.id.radioNameDesc -> SortOrder.NAME_DESC
                    else -> currentSortOrder
                }

                val sortChanged = newSortOrder != currentSortOrder
                if (sortChanged) {
                    currentSortOrder = newSortOrder
                    sharedPreferences.edit().putString(KEY_SORT_ORDER, currentSortOrder.name).apply()
                }

                // Handle show system apps change
                val newShowSystem = checkboxShowSystem.isChecked
                val showSystemChanged = newShowSystem != showSystemApps
                if (showSystemChanged) {
                    showSystemApps = newShowSystem
                    sharedPreferences.edit().putBoolean(KEY_SHOW_SYSTEM_APPS, showSystemApps).apply()
                    
                    // When hiding system apps, deselect all system apps to prevent them from being firewalled
                    if (!showSystemApps) {
                        val systemAppsToDeselect = appList.filter { it.isSystem && it.isSelected }
                        for (i in appList.indices) {
                            if (appList[i].isSystem && appList[i].isSelected) {
                                appList[i] = appList[i].copy(isSelected = false)
                            }
                        }
                        if (systemAppsToDeselect.isNotEmpty()) {
                            saveSelectedApps()
                            updateSelectedCount()
                        }
                    }
                    
                    updateCategoryChips()
                    // Always refresh the list when show system apps changes, with animation only if sort didn't change
                    sortAndFilterApps(preserveScrollPosition = false, scrollToTop = true, animate = false)
                } else if (sortChanged) {
                    sortAndFilterApps(preserveScrollPosition = false, scrollToTop = true, animate = true)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun sortAndFilterApps(preserveScrollPosition: Boolean = false, scrollToTop: Boolean = false, animate: Boolean = false) {
        val turkishCollator = java.text.Collator.getInstance(java.util.Locale.forLanguageTag("tr-TR"))
        
        var finalComparator: Comparator<AppInfo> = when (currentSortOrder) {
            SortOrder.NAME_ASC -> Comparator { o1: AppInfo, o2: AppInfo -> turkishCollator.compare(o1.appName, o2.appName) }
            SortOrder.NAME_DESC -> Comparator { o1: AppInfo, o2: AppInfo -> turkishCollator.compare(o1.appName, o2.appName) }.reversed()
            SortOrder.INSTALL_TIME -> compareByDescending<AppInfo> { it.installTime }
        }

        if (moveSelectedTop) {
            finalComparator = compareByDescending<AppInfo> { it.isSelected }.then(finalComparator)
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
                    val targetAlpha = if ((isFirewallEnabled && !firewallMode.allowsDynamicSelection()) || isFirewallProcessRunning) 0.5f else 1f
                        recyclerView.animate()
                            .alpha(targetAlpha)
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
            val targetAlpha = if ((isFirewallEnabled && !firewallMode.allowsDynamicSelection()) || isFirewallProcessRunning) 0.5f else 1f
            recyclerView.alpha = targetAlpha
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
                val targetApps = if (firewallMode == FirewallMode.WHITELIST) {
                    val showSys = sharedPreferences.getBoolean(KEY_SHOW_SYSTEM_APPS, false)
                    appList.filter { !it.isSelected && (showSys || !it.isSystem) }
                } else {
                    selectedApps
                }
                
                if (targetApps.isEmpty() && !firewallMode.allowsDynamicSelection()) {
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
                        showFirewallConfirmDialog(targetApps)
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
                    pendingEnableSelectedApps = targetApps.map { it.packageName }
                    suppressToggleListener = true
                    firewallToggle.isChecked = false
                    suppressToggleListener = false
                    return@setOnCheckedChangeListener
                }
                // Permission already granted, proceed
                pendingToggleEnable = false
                showFirewallConfirmDialog(targetApps)
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

    private fun updateFirewallToggleThumbIcon() {
        if (!::firewallToggle.isInitialized) return

        val workingMode = WorkingMode.fromName(
            sharedPreferences.getString(KEY_WORKING_MODE, WorkingMode.SHIZUKU.name)
        )
        val iconRes = when (workingMode) {
            WorkingMode.SHIZUKU -> R.drawable.ic_shizuku
            WorkingMode.LADB -> R.drawable.adb_24px
            WorkingMode.ROOT -> R.drawable.hashtag_24px
        }
        firewallToggle.setThumbIconResource(iconRes)
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

        dialog.setOnShowListener {
        }
        val dialogMessage = dialogView.findViewById<TextView>(R.id.dialogMessage)
        val selectedAppsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.selectedAppsRecyclerView)

        dialogMessage.text = getString(R.string.enable_firewall_confirm, selectedApps.size)

        selectedAppsRecyclerView.layoutManager = LinearLayoutManager(this)
        selectedAppsRecyclerView.adapter = SelectedAppsAdapter(selectedApps)

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

        dialog.setOnShowListener {
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
            firewallToggle.isEnabled = (isFirewallEnabled || count > 0 || firewallMode.allowsDynamicSelection()) && !isFirewallProcessRunning
        }
        
        updateSelectAllCheckbox()
        updateInteractiveViews()
    }

    private fun updateInteractiveViews() {
        // The select-all badge should be disabled when the firewall is enabled and
        // Adaptive Mode is turned OFF. Otherwise it can be used.
        if (::selectedCountText.isInitialized) {
            selectedCountText.isEnabled = !isFirewallEnabled || firewallMode.allowsDynamicSelection()
            selectedCountText.alpha = if (selectedCountText.isEnabled) 1.0f else 0.4f
        }
    }

    private fun updateSelectAllCheckbox() {
        // Badge visual is driven by selectedCountText.text; no extra state needed.
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadInstalledApps(showLoadingIfListEmpty: Boolean = false) {
        lifecycleScope.launch {
            if (showLoadingIfListEmpty && appList.isEmpty()) {
                setAppListLoadingVisible(true)
            }

            val result = withContext(Dispatchers.IO) {
                val pm = packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                
                val installedPackageNames = packages.mapTo(HashSet(packages.size)) { it.packageName }

                val savedActive = loadActivePackages().toMutableSet()
                val activeToRemove = savedActive.filterNot { installedPackageNames.contains(it) }
                val appsWereRemoved = activeToRemove.isNotEmpty()

                if (appsWereRemoved) {
                    savedActive.removeAll(activeToRemove.toSet())
                    saveActivePackages(savedActive)
                }

                val savedSelected = loadSelectedApps().toMutableSet()
                val selectedToRemove = savedSelected.filterNot { installedPackageNames.contains(it) }
                // Remove this app itself from saved selected apps if present
                val selfPkg = this@MainActivity.packageName
                var selectedChanged = false
                if (savedSelected.remove(selfPkg)) selectedChanged = true
                if (selectedToRemove.isNotEmpty()) {
                    savedSelected.removeAll(selectedToRemove.toSet())
                    selectedChanged = true
                }
                if (selectedChanged) {
                    sharedPreferences.edit()
                        .putStringSet(KEY_SELECTED_APPS, savedSelected)
                        .putInt(KEY_SELECTED_COUNT, savedSelected.size)
                        .apply()
                }

                val favoritePackages = loadFavoriteApps()
                val modesStr = sharedPreferences.getString(KEY_APP_MODES, "{}")
                val modesJson = try { JSONObject(modesStr!!) } catch (e: Exception) { JSONObject() }
                
                // Process packages in parallel chunks for better performance
                val chunkSize = 50
                val chunks = packages.chunked(chunkSize)
                val results = chunks.map { chunk ->
                    async(Dispatchers.Default) {
                        val chunkResult = mutableListOf<AppInfo>()
                        for (packageInfo in chunk) {
                            val appInfo = packageInfo.applicationInfo ?: continue
                            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                            // skip apps that are disabled
                            if (!appInfo.enabled) continue

                            val packageName = packageInfo.packageName

                            // never show Shizuku app(s) or this app itself
                            if (ShizukuPackageResolver.isShizukuPackage(this@MainActivity, packageName)) continue
                            if (packageName == selfPkg) continue

                            // Check INTERNET permission from pre-fetched permissions array
                            val hasInternetPermission = packageInfo.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
                            if (!hasInternetPermission) continue

                            val appName = appInfo.loadLabel(pm).toString()
                            val isSelected = savedSelected.contains(packageName)
                            val isFavorite = favoritePackages.contains(packageName)
                            val installTime = packageInfo.firstInstallTime
                            val appMode = modesJson.optInt(packageName, 0)

                            chunkResult.add(AppInfo(appName, packageName, isSelected, isSystemApp, isFavorite, installTime, appMode))
                        }
                        chunkResult
                    }
                }.awaitAll().flatten()
                
                Triple(results, savedActive, appsWereRemoved)
            }

            val builtList = result.first
            val cleanedActivePackages = result.second
            val appsWereRemoved = result.third

            activeFirewallPackages.clear()
            activeFirewallPackages.addAll(cleanedActivePackages)

            // If firewall is enabled but no packages are active (e.g. all uninstalled), disable it
            // In Adaptive Mode, we allow firewall to stay ON even with 0 active packages
            if (isFirewallEnabled && activeFirewallPackages.isEmpty() && !firewallMode.allowsDynamicSelection()) {
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

            saveAppsCache(builtList)

            if (isAppListLoadingVisible) {
                setAppListLoadingVisible(false)
            }
            
            updateSelectedCount()
        }
    }

    private fun restoreAppListFromCache(): Boolean {
        val json = sharedPreferences.getString(KEY_APPS_CACHE_JSON, null) ?: return false
        val selectedPackages = loadSelectedApps()
        val favoritePackages = loadFavoriteApps()
        val modesStr = sharedPreferences.getString(KEY_APP_MODES, "{}")
        val modesJson = try { JSONObject(modesStr!!) } catch (e: Exception) { JSONObject() }

        val cachedList = try {
            val arr = JSONArray(json)
            val parsed = mutableListOf<AppInfo>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val packageName = obj.optString("packageName")
                if (packageName.isBlank()) continue

                parsed.add(
                    AppInfo(
                        appName = obj.optString("appName", packageName),
                        packageName = packageName,
                        isSelected = selectedPackages.contains(packageName),
                        isSystem = obj.optBoolean("isSystem", false),
                        isFavorite = favoritePackages.contains(packageName),
                        installTime = obj.optLong("installTime", 0L),
                        appFirewallMode = modesJson.optInt(packageName, 0)
                    )
                )
            }
            parsed
        } catch (_: Exception) {
            emptyList()
        }

        if (cachedList.isEmpty()) return false

        appList.clear()
        appList.addAll(cachedList)
        sortAndFilterApps(preserveScrollPosition = false)
        return true
    }

    private fun saveAppsCache(apps: List<AppInfo>) {
        val arr = JSONArray()
        apps.forEach { app ->
            arr.put(
                JSONObject().apply {
                    put("appName", app.appName)
                    put("packageName", app.packageName)
                    put("isSystem", app.isSystem)
                    put("installTime", app.installTime)
                    put("appFirewallMode", app.appFirewallMode)
                }
            )
        }
        sharedPreferences.edit().putString(KEY_APPS_CACHE_JSON, arr.toString()).apply()
    }

    private fun setAppListLoadingVisible(visible: Boolean) {
        if (!::appListLoadingContainer.isInitialized) return
        if (visible == isAppListLoadingVisible) return

        isAppListLoadingVisible = visible
        appListLoadingContainer.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun saveSelectedApps() {
        val selectedPackages = appList
            .filter { it.isSelected && !ShizukuPackageResolver.isShizukuPackage(this, it.packageName) }
            .map { it.packageName }
            .toSet()
            
        val modesJson = JSONObject()
        appList.filter { it.appFirewallMode != 0 }.forEach {
            modesJson.put(it.packageName, it.appFirewallMode)
        }
            
        sharedPreferences.edit()
            .putStringSet(KEY_SELECTED_APPS, selectedPackages)
            .putInt(KEY_SELECTED_COUNT, selectedPackages.size)
            .putString(KEY_APP_MODES, modesJson.toString())
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

        ScreenLockMonitorService.sync(this)
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
        if (currentElapsed < savedElapsed) {
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
        if (enable && packageNames.isEmpty() && !firewallMode.allowsDynamicSelection()) return

        val effectivePackageNames = if (enable) {
            if (firewallMode == FirewallMode.SCREEN_LOCK_MODE && !ScreenLockModeReceiver.isDeviceLocked(this)) {
                emptyList()
            } else if (firewallMode == FirewallMode.HYBRID) {
                val appModesStr = sharedPreferences.getString(KEY_APP_MODES, "{}")
                val appModes = try { JSONObject(appModesStr!!) } catch (e: Exception) { JSONObject() }
                val isLocked = ScreenLockModeReceiver.isDeviceLocked(this)
                packageNames.filter {
                    val mode = appModes.optInt(it, 0)
                    when (mode) {
                        1 -> false // SMART_FOREGROUND app -> handled dynamically by accessibility service
                        2 -> isLocked // SCREEN_LOCK app -> block if screen locked
                        else -> true // DEFAULT app -> block immediately
                    }
                }
            } else if (firewallMode == FirewallMode.SMART_FOREGROUND) {
                emptyList()
            } else {
                packageNames
            }
        } else {
            packageNames
        }

        firewallToggle.isEnabled = false
        isFirewallProcessRunning = true
        isEnablingProcess = enable
        lifecycleScope.launch {
            firewallProgress.visibility = android.view.View.VISIBLE
            appListAdapter.setSelectionEnabled(false)
            updateInteractiveViews()
            showDimOverlay(force = true)
            
            if (enable && (firewallMode == FirewallMode.SMART_FOREGROUND || firewallMode == FirewallMode.HYBRID)) {
                if (!ForegroundDetectionService.isServiceEnabled(this@MainActivity)) {
                    val granted = ForegroundDetectionService.enableServiceViaShell(this@MainActivity)
                    if (granted) {
                        Toast.makeText(this@MainActivity, getString(R.string.accessibility_auto_enabled), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            try {
                // perform package existence checks and run enable/disable on IO thread
                val (installed, missing) = withContext(Dispatchers.IO) {
                    filterInstalledPackages(effectivePackageNames)
                }

                // If enabling and none of the chosen packages remain installed -> abort
                // In Adaptive Mode, allow enabling with empty list
                if (enable && installed.isEmpty() && !firewallMode.allowsDynamicSelection()) {
                    Toast.makeText(this@MainActivity, getString(R.string.none_selected_apps_installed), Toast.LENGTH_SHORT).show()
                    suppressToggleListener = true
                    firewallToggle.isChecked = false
                    suppressToggleListener = false
                    appListAdapter.setSelectionEnabled(true)
                    updateInteractiveViews()
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
                    if (successful.isNotEmpty() || firewallMode.allowsDynamicSelection()) {
                        isFirewallEnabled = true
                        activeFirewallPackages.clear()
                        activeFirewallPackages.addAll(successful)
                        saveActivePackages(activeFirewallPackages)
                        saveFirewallEnabled(true)
                        // Ensure toggle stays ON
                        suppressToggleListener = true
                        firewallToggle.isChecked = true
                        suppressToggleListener = false
                        
                        if (sharedPreferences.getBoolean(KEY_FIREWALL_INDICATOR_ENABLED, false)) {
                            com.arslan.shizuwall.services.ForegroundFirewallIndicatorService.start(this@MainActivity)
                        }
                        if (sharedPreferences.getBoolean(com.arslan.shizuwall.services.FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, false)) {
                            com.arslan.shizuwall.services.FloatingButtonService.start(this@MainActivity)
                        }
                        
                        if (firewallMode.allowsDynamicSelection()) {
                            appListAdapter.setSelectionEnabled(true)
                            updateInteractiveViews()
                            hideDimOverlay()
                        } else {
                            appListAdapter.setSelectionEnabled(false)
                            updateInteractiveViews()
                            showDimOverlay()
                        }
                    
                } else {
                    // None succeeded, revert toggle
                    suppressToggleListener = true
                    firewallToggle.isChecked = false
                    suppressToggleListener = false
                    Toast.makeText(this@MainActivity, getString(R.string.failed_to_enable_firewall), Toast.LENGTH_SHORT).show()
                    if (!firewallMode.allowsDynamicSelection()) {
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

            val showedLadbRecoveryDialog = enable && maybeShowLadbDaemonRecoveryDialog(failed, lastOperationErrorDetails)

            // Handle failures: unselect failed apps and show error dialog
            if (failed.isNotEmpty()) {
                val skipErrorDialog = sharedPreferences.getBoolean(KEY_SKIP_ERROR_DIALOG, false)
                val keepErrorAppsSelected = sharedPreferences.getBoolean(KEY_KEEP_ERROR_APPS_SELECTED, false)
                
                // Only unselect if user hasn't opted to keep them selected
                if (!(skipErrorDialog && keepErrorAppsSelected)) {
                    for (pkg in failed) {
                        val idx = appList.indexOfFirst { it.packageName == pkg }
                        if (idx != -1) {
                            if (firewallMode == FirewallMode.WHITELIST) {
                                // For whitelist mode: If we failed to block/unblock during enable/disable, invert.
                                appList[idx] = appList[idx].copy(isSelected = !appList[idx].isSelected)
                            } else {
                                appList[idx] = appList[idx].copy(isSelected = false)
                            }
                        }
                    }
                    updateSelectedCount()
                    saveSelectedApps()
                    sortAndFilterApps(preserveScrollPosition = false)
                }
                if (!showedLadbRecoveryDialog) {
                    showOperationErrorsDialog(failed, lastOperationErrorDetails)
                }
            }
            } finally {
                firewallProgress.visibility = android.view.View.GONE
                firewallToggle.isEnabled = true
                isFirewallProcessRunning = false
                applyListInteractionState()
            }
        }
    }

    private fun filterInstalledPackages(packageNames: List<String>): Pair<List<String>, List<String>> {
        val installed = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val pm = packageManager
        for (pkg in packageNames) {
            // Treat Shizuku packages as "missing" / never-operable
            if (ShizukuPackageResolver.isShizukuPackage(this, pkg)) {
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
            val msg = chain3Result.stderr.ifEmpty { chain3Result.stdout }
            // In Smart Foreground mode the list is empty — record a top-level chain3 error.
            if (packageNames.isEmpty()) {
                lastOperationErrorDetails["_chain3"] = msg
                return Pair(successful, failed)
            }
            for (pkg in packageNames) {
                lastOperationErrorDetails[pkg] = msg
                failed.add(pkg)
            }
            return Pair(successful, failed)
        }

        if (packageNames.isEmpty()) {
            return Pair(successful, failed)
        }

        if (firewallMode == FirewallMode.SMART_FOREGROUND) {
            return Pair(successful, failed)
        }

        for (packageName in packageNames) {
            // In Whitelist mode, the target apps passed to enableFirewall are ALWAYS the apps that SHOULD be blocked (the ones the user didn't select).
            val cmd = "cmd connectivity set-package-networking-enabled false $packageName"
            val res = runCommandDetailed(cmd)
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

        val toUnblock = packageNames.toMutableList()
        if (firewallMode == FirewallMode.SMART_FOREGROUND || firewallMode == FirewallMode.HYBRID) {
            val currentFgApp = sharedPreferences.getString(MainActivity.KEY_SMART_FOREGROUND_APP, null)
            if (!currentFgApp.isNullOrEmpty() && !toUnblock.contains(currentFgApp)) {
                toUnblock.add(currentFgApp)
            }
        }

        for (packageName in toUnblock) {
            // disableFirewall is called with activeFirewallPackages. 
            // In any mode, these are apps that were explicitly BLOCKED by us. 
            // When disabling the firewall, we must restore them to TRUE (unblocked).
            val res = runCommandDetailed("cmd connectivity set-package-networking-enabled true $packageName")
            if (res.success) {
                successful.add(packageName)
            } else {
                failed.add(packageName)
                lastOperationErrorDetails[packageName] = res.stderr.ifEmpty { res.stdout }
            }
        }
        runCommandDetailed("cmd connectivity set-chain3-enabled false")

        if (firewallMode == FirewallMode.SMART_FOREGROUND || firewallMode == FirewallMode.HYBRID) {
            sharedPreferences.edit()
                .putString(MainActivity.KEY_SMART_FOREGROUND_APP, "")
                .putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet())
                .apply()
        }

        return Pair(successful, failed)
    }


    private suspend fun runCommandDetailed(command: String): com.arslan.shizuwall.shell.ShellResult {
        return ShellExecutorProvider.forContext(this).exec(command)
    }

    private suspend fun runCommand(command: String): Boolean {
        return runCommandDetailed(command).success
    }

    // dim only the RecyclerView and disable its interactions
    private fun showDimOverlay(force: Boolean = false) {
        // visually dim RecyclerView and block interactions
        if (firewallMode.allowsDynamicSelection() && !force) return

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

    // Keep list interactivity and dim state consistent with current firewall mode/state.
    private fun applyListInteractionState() {
        if (isFirewallProcessRunning) {
            showDimOverlay(force = true)
            return
        }

        val shouldLockSelection = isFirewallEnabled && !firewallMode.allowsDynamicSelection()
        if (shouldLockSelection) {
            showDimOverlay()
        } else {
            hideDimOverlay()
        }
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
                if (ShizukuPackageResolver.isShizukuPackage(this@MainActivity, appInfo.packageName)) return@let

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
        val chipUser = findViewById<Chip?>(R.id.chip_user)
        val chipSelected = findViewById<Chip?>(R.id.chip_selected)
        val chipUnselected = findViewById<Chip?>(R.id.chip_unselected)

        chipSystem?.visibility = if (showSystemApps) View.VISIBLE else View.GONE
        chipUser?.visibility = if (showSystemApps) View.VISIBLE else View.GONE

        chipSelected?.visibility = if (moveSelectedTop) View.GONE else View.VISIBLE

        // if we hid the system/user chip and it was selected, clear the selection (do NOT switch to a removed default)
        if (!showSystemApps && (categoryGroup.checkedChipId == R.id.chip_system || categoryGroup.checkedChipId == R.id.chip_user)) {
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
        val hasDetails = failedPackages.any { errorDetails[it]?.isNotEmpty() == true } ||
            errorDetails.containsKey("_chain3") ||
            errorDetails.containsKey("_daemon_log")
        if (hasDetails) {
            builder.setNeutralButton(getString(R.string.details)) { _, _ ->
                showErrorDetailsDialog(failedPackages, errorDetails)
            }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
        }

        val dialogMessage = dialogView.findViewById<TextView>(R.id.dialogMessage)
        val selectedAppsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.selectedAppsRecyclerView)

        dialogMessage.text = getString(R.string.operation_failed_message, failedApps.size)

        // If any error detail indicates the chain3 command is missing, append a short hint
        val chain3Msg = errorDetails["_chain3"] ?: errorDetails.values.firstOrNull { it.contains("no command found set chain 3", ignoreCase = true) }
        if (chain3Msg != null) {
            dialogMessage.append("\n\n${getString(R.string.android11_unsupported_hint)}")
        }

        if (!errorDetails["_daemon_log"].isNullOrBlank()) {
            dialogMessage.append("\n\n${getString(R.string.ladb_daemon_log_detected_hint)}")
        }

        selectedAppsRecyclerView.layoutManager = LinearLayoutManager(this)
        selectedAppsRecyclerView.adapter = SelectedAppsAdapter(failedApps)

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

    private suspend fun maybeShowLadbDaemonRecoveryDialog(
        failedPackages: List<String>,
        errorDetails: MutableMap<String, String>
    ): Boolean {
        val daemonLogs = loadRecentDaemonLogsForDiagnostics()
        if (!daemonLogs.isNullOrBlank()) {
            errorDetails["_daemon_log"] = daemonLogs
        }

        if (!hasLadbDaemonRecoveryError(errorDetails, daemonLogs)) {
            return false
        }

        val diagnosis = buildLadbDaemonRecoveryMessage(errorDetails, daemonLogs)

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ladb_daemon_restart_required_title)
            .setMessage(diagnosis)
            .setPositiveButton(R.string.open_ladb_setup) { _, _ ->
                try {
                    startActivity(Intent(this, com.arslan.shizuwall.LadbSetupActivity::class.java))
                } catch (_: Exception) {
                }
            }
            .setNegativeButton(R.string.cancel, null)

        if (failedPackages.isNotEmpty()) {
            builder.setNeutralButton(R.string.details) { _, _ ->
                showErrorDetailsDialog(failedPackages, errorDetails)
            }
        }

        builder.show()
        return true
    }

    private fun hasLadbDaemonRecoveryError(errorDetails: Map<String, String>, daemonLogs: String?): Boolean {
        val combined = buildList {
            addAll(errorDetails.values)
            if (!daemonLogs.isNullOrBlank()) add(daemonLogs)
        }

        return combined.any { error ->
            val normalizedError = error.lowercase()
            normalizedError.contains("unauthorized") ||
                normalizedError.contains("failed to authorizate daemon") ||
                normalizedError.contains("daemon not responding") ||
                normalizedError.contains("daemon not responing") ||
                normalizedError.contains("token file") ||
                normalizedError.contains("token")
        }
    }

    private suspend fun loadRecentDaemonLogsForDiagnostics(): String? {
        return try {
            com.arslan.shizuwall.daemon.PersistentDaemonManager(this).readRecentDaemonLogs(20)
                ?.lineSequence()
                ?.filter { it.isNotBlank() }
                ?.toList()
                ?.takeLast(2)
                ?.joinToString("\n")
                ?.trim()
                ?.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildLadbDaemonRecoveryMessage(errorDetails: Map<String, String>, daemonLogs: String?): String {
        val reasons = linkedSetOf<String>()
        val combined = buildList {
            addAll(errorDetails.values)
            if (!daemonLogs.isNullOrBlank()) add(daemonLogs)
        }

        combined.forEach { error ->
            val normalizedError = error.lowercase()
            when {
                normalizedError.contains("unauthorized") ||
                    normalizedError.contains("failed to authorizate daemon") -> {
                    reasons.add(getString(R.string.ladb_daemon_restart_reason_auth))
                }

                normalizedError.contains("daemon not responding") ||
                    normalizedError.contains("daemon not responing") ||
                    normalizedError.contains("timeout") ||
                    normalizedError.contains("connection refused") -> {
                    reasons.add(getString(R.string.ladb_daemon_restart_reason_unresponsive))
                }

                normalizedError.contains("token file") || normalizedError.contains("token") -> {
                    reasons.add(getString(R.string.ladb_daemon_restart_reason_token))
                }
            }
        }

        val builder = StringBuilder(getString(R.string.ladb_daemon_restart_required_message))
        if (reasons.isNotEmpty()) {
            builder.append("\n\n")
            builder.append(getString(R.string.ladb_daemon_restart_reason_prefix))
            builder.append("\n")
            builder.append(reasons.joinToString("\n") { "- $it" })
        }

        if (!daemonLogs.isNullOrBlank()) {
            builder.append("\n\n")
            builder.append(getString(R.string.ladb_daemon_restart_log_prefix))
            builder.append("\n")
            builder.append(daemonLogs)
        }

        return builder.toString()
    }

    private fun showErrorDetailsDialog(failedPackages: List<String>, errorDetails: Map<String, String> = emptyMap()) {
        val failedApps = appList.filter { it.packageName in failedPackages }
        if (failedApps.isEmpty()) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_error_details, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.setOnShowListener {
        }

        val messageView = dialogView.findViewById<TextView>(R.id.dialogErrorDetailsMessage)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.errorDetailsRecyclerView)
        val btnCopy = dialogView.findViewById<android.widget.Button>(R.id.btnCopy)
        val btnClose = dialogView.findViewById<android.widget.Button>(R.id.btnClose)

        messageView.text = getString(R.string.operation_failed_message, failedApps.size)

        val list = failedApps.map { ai ->
            val err = errorDetails[ai.packageName]
                ?: errorDetails["_chain3"]
                ?: errorDetails["_daemon_log"]
                ?: getString(R.string.no_error_details)
            ErrorEntry(ai.appName, ai.packageName, err)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ErrorDetailsAdapter(list)

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

    private fun getTargetPackagesToBlock(selectedPkgs: List<String>): List<String> {
        val mode = FirewallMode.fromName(sharedPreferences.getString(KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name))
        return if (mode == FirewallMode.WHITELIST) {
            val showSys = sharedPreferences.getBoolean(KEY_SHOW_SYSTEM_APPS, false)
            com.arslan.shizuwall.utils.WhitelistFilter.getPackagesToBlock(this, selectedPkgs, showSys)
        } else {
            selectedPkgs
        }
    }
}