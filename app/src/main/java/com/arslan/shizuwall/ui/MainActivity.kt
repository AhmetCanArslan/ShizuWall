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
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.os.SystemClock
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.CheckBox
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
import com.arslan.shizuwall.repo.FirewallStateRepository
import com.arslan.shizuwall.daemon.PersistentDaemonManager
import com.arslan.shizuwall.utils.FirewallUtils
import com.arslan.shizuwall.utils.WhitelistFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import com.arslan.shizuwall.R
import com.arslan.shizuwall.adapters.ErrorEntry
import com.arslan.shizuwall.FirewallMode
import com.arslan.shizuwall.WorkingMode
import com.arslan.shizuwall.shizuku.ShizukuSetupActivity
import com.arslan.shizuwall.shell.RootShellExecutor
import com.arslan.shizuwall.shell.ShellExecutorProvider
import com.arslan.shizuwall.receivers.ScreenLockModeReceiver
import com.arslan.shizuwall.utils.ShizukuPackageResolver
import com.arslan.shizuwall.firewall.ForegroundTaskProbe
import com.arslan.shizuwall.firewall.FirewallCommands
import com.arslan.shizuwall.firewall.FirewallTargets
import com.arslan.shizuwall.utils.AppIds
import com.arslan.shizuwall.utils.AppKey
import com.arslan.shizuwall.utils.CrossUserAppInfo
import com.arslan.shizuwall.utils.MultiUserApps
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : BaseActivity() {
    companion object {
        private const val FADE_IN_DURATION_MS = 300L
        private const val FADE_OUT_DURATION_MS = 220L
        private const val PROGRESS_SHOW_DELAY_MS = 250L
        private const val PROGRESS_MIN_VISIBLE_MS = 550L
        const val PREF_NAME = "ShizuWallPrefs"
        const val KEY_SELECTED_APPS = "selected_apps"
        const val KEY_SELECTED_COUNT = "selected_count"
        const val KEY_FAVORITE_APPS = "favorite_apps"
        const val KEY_FIREWALL_ENABLED = "firewall_enabled"
        const val KEY_ACTIVE_PACKAGES = "active_packages"
        const val KEY_FIREWALL_SAVED_ELAPSED = "firewall_saved_elapsed"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
        const val KEY_SKIP_ENABLE_CONFIRM = "skip_enable_confirm" 
        const val KEY_SKIP_ERROR_DIALOG = "skip_error_dialog"
        const val KEY_SKIP_ANDROID11_INFO = "skip_android11_info"
        const val KEY_KEEP_ERROR_APPS_SELECTED = "keep_error_apps_selected"
        const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
        const val KEY_SHOW_OTHER_PROFILES = "show_other_profiles"
        const val KEY_SHOW_APP_INFO_BUTTON = "show_app_info_button"
        const val KEY_MOVE_SELECTED_TOP = "move_selected_top"
        const val KEY_SELECTED_FONT = "selected_font"
        const val KEY_USE_DYNAMIC_COLOR = "use_dynamic_color"
        const val KEY_USE_AMOLED_BLACK = "use_amoled_black"
        const val KEY_FIREWALL_MODE = "firewall_mode"
        const val KEY_SCREEN_LOCK_DELAY_SECONDS = "screen_lock_delay_seconds"
        const val DEFAULT_SCREEN_LOCK_DELAY_SECONDS = 2
        const val KEY_SMART_FOREGROUND_APP = "smart_foreground_app"
        const val KEY_AUTO_ENABLE_ON_SHIZUKU_START = "auto_enable_on_shizuku_start"
        const val KEY_SHIZUKU_SERVER_ID = "shizuku_server_id"
        const val KEY_APPLY_ROOT_RULES_AFTER_REBOOT = "apply_root_rules_after_reboot"
        const val KEY_SHOW_SETUP_PROMPT = "show_setup_prompt"
        const val KEY_WORKING_MODE = "working_mode"
        const val KEY_SORT_ORDER = "sort_order"

        const val ACTION_FIREWALL_STATE_CHANGED = "com.arslan.shizuwall.ACTION_FIREWALL_STATE_CHANGED"
        const val EXTRA_FIREWALL_ENABLED = "state" 

        const val ACTION_FIREWALL_CONTROL = "shizuwall.CONTROL" 
        const val EXTRA_PACKAGES_CSV = "apps"

        const val KEY_FIREWALL_UPDATE_TS = "firewall_update_ts"
        const val KEY_APP_MONITOR_ENABLED = "app_monitor_enabled"
        const val KEY_AUTO_FIREWALL_NEW_APPS = "auto_firewall_new_apps"
        const val KEY_SHOW_FIREWALL_STATUS_NOTIFICATION = "show_firewall_status_notification"
        const val KEY_APP_MODES = "app_modes_json"
        const val KEY_PROFILES = "profiles_json"
        const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        const val KEY_AUTO_ENABLE_ON_PROFILE_ACTIVATE = "auto_enable_on_profile_activate"
        const val KEY_TILE_COLLAPSE_SHADE = "tile_collapse_shade"
        const val KEY_REMEMBER_DISABLED_APPS = "remember_disabled_apps"
        private const val KEY_APPS_CACHE_JSON = "apps_cache_json_v1"
        const val KEY_LAST_SEEN_CHANGELOG = "last_seen_changelog"

        fun changelogId(context: Context): String {
            val config = android.content.res.Configuration(context.resources.configuration)
            config.setLocale(java.util.Locale.ENGLISH)
            val base = context.createConfigurationContext(config).getString(R.string.changelog_message)
            return java.security.MessageDigest.getInstance("SHA-256")
                .digest(base.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(16)
        }

        const val ACTION_APP_INSTALLED = "shizuwall.APP_INSTALLED"

        const val ACTION_PROFILE_CONTROL = "shizuwall.PROFILE"
        const val EXTRA_PROFILE_NAME = "profile"
        const val EXTRA_PROFILE_ID = "profile_id"

    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var appListAdapter: AppListAdapter
    private lateinit var firewallToggle: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var firewallProgress: android.widget.ProgressBar
    private lateinit var searchView: SearchView
    private lateinit var selectedCountText: TextView
    private lateinit var appListLoadingContainer: View
    private lateinit var appListEmptyContainer: View
    private lateinit var appListEmptyTitle: TextView
    private lateinit var appListEmptyMessage: TextView
    private lateinit var appListEmptyAction: com.google.android.material.button.MaterialButton
    private lateinit var backendBanner: View
    private lateinit var backendBannerMessage: TextView
    private lateinit var backendBannerAction: com.google.android.material.button.MaterialButton
    private val appList = mutableListOf<AppInfo>()
    private val filteredAppList = mutableListOf<AppInfo>()
    private var isFirewallEnabled = false
    private var currentQuery = ""
    private var showSystemApps = false 
    private var showOtherProfiles = false
    private var moveSelectedTop = false
    private var firewallMode = FirewallMode.DEFAULT
    private enum class SortOrder { NAME_ASC, NAME_DESC, INSTALL_TIME }
    private var currentSortOrder = SortOrder.NAME_ASC

    private lateinit var sharedPreferences: SharedPreferences

    private var firewallRepo: FirewallStateRepository? = null
    private var shizukuListenersAdded = false

    private var defaultItemAnimator: RecyclerView.ItemAnimator? = null
    private var isAppListLoadingVisible = false
    private var isFirewallProcessRunning = false

    private var profileEnableActive = false
    private val skipDimForProfileEnable: Boolean
        get() = profileEnableActive && firewallMode != FirewallMode.DEFAULT
    private val listDimmed: Boolean
        get() = !firewallMode.allowsDynamicSelection() &&
            (isFirewallProcessRunning || (isFirewallEnabled && !skipDimForProfileEnable))

    private var profileEnableRequested = false
    private var isEnablingProcess = false
    private var lastSelectedCountToastTime = 0L
    private var sortFilterRunnable = Runnable {}

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        onRequestPermissionsResult(requestCode, grantResult)
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        if (currentWorkingMode() != WorkingMode.SHIZUKU) return@OnBinderReceivedListener
        if (!sharedPreferences.getBoolean(KEY_AUTO_ENABLE_ON_SHIZUKU_START, false)) {
            checkShizukuPermission()
            return@OnBinderReceivedListener
        }
        if (!loadFirewallEnabled() && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) autoEnableOnShizukuStart()
    }

    private fun autoEnableOnShizukuStart(selectedPkgs: List<String> = loadSelectedApps().toList()) {
        if (selectedPkgs.isEmpty() && !firewallMode.allowsDynamicSelection()) return
        val granted = runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        if (!granted) {
            pendingAutoEnable = true
            pendingAutoEnableSelectedApps = selectedPkgs
            try {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            } catch (_: Exception) {
                checkShizukuPermission()
            }
            return
        }
        val targetPkgs = getTargetPackagesToBlock(selectedPkgs)
        val allowPkgs = getWhitelistAllowPackages(selectedPkgs)
        when {
            sharedPreferences.getBoolean(KEY_SKIP_ENABLE_CONFIRM, false) -> applyFirewallState(true, targetPkgs, allowPkgs)
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ->
                showFirewallConfirmDialog(appList.filter { targetPkgs.contains(it.packageName) }, targetPkgs, allowPkgs)
            else -> {
                pendingAutoEnable = true
                pendingAutoEnableSelectedApps = selectedPkgs
            }
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        if (currentWorkingMode() != WorkingMode.SHIZUKU) return@OnBinderDeadListener

        Toast.makeText(this, getString(R.string.shizuku_service_dead), Toast.LENGTH_SHORT).show()
        finish()
    }

    private var suppressToggleListener = false
    private val activeFirewallPackages = mutableSetOf<String>()
    private val lastOperationErrorDetails = mutableMapOf<String, String>()
    private enum class Category { NONE, FAVORITES, SYSTEM, SELECTED, UNSELECTED, USER, PROFILE }
    private var currentCategory: Category = Category.NONE
    private var currentProfileUserId: Int = -1
    private val profileChipIds = LinkedHashMap<Int, Int>()
    
    private var pendingToggleEnable = false
    private var pendingToggleDisable = false
    private var pendingEnableSelectedApps: List<String>? = null
    private var pendingDisableActivePackages: List<String>? = null
    private var pendingAutoEnable = false
    private var pendingAutoEnableSelectedApps: List<String>? = null

    private var appListRefreshPending = true

    private val packageBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val action = intent?.action ?: return
            val pkg = intent.data?.schemeSpecificPart ?: return

            when (action) {
                Intent.ACTION_PACKAGE_REMOVED -> {
                    val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    if (replacing) return
                    handlePackageRemoved(pkg)
                }
                Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED -> {
                    handlePackageAdded(pkg)
                }
            }
        }
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            showSystemApps = sharedPreferences.getBoolean(KEY_SHOW_SYSTEM_APPS, false)
        showOtherProfiles = sharedPreferences.getBoolean(KEY_SHOW_OTHER_PROFILES, false)
            moveSelectedTop = sharedPreferences.getBoolean(KEY_MOVE_SELECTED_TOP, false)
            firewallMode = FirewallMode.fromName(sharedPreferences.getString(KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name))
            updateFirewallToggleThumbIcon()
            appListRefreshPending = false
            loadInstalledApps()
            updateCategoryChips()
            
            if (isFirewallEnabled) applyListInteractionState()
            appListAdapter.setHybridModeEnabled(firewallMode == FirewallMode.HYBRID)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        isFirewallEnabled = loadFirewallEnabled()

        enableEdgeToEdge()

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val onboardingComplete = prefs.getBoolean("onboarding_complete", false)

        if (!onboardingComplete) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        if (sharedPreferences.getBoolean(KEY_APP_MONITOR_ENABLED, false) ||
            sharedPreferences.getBoolean(KEY_AUTO_FIREWALL_NEW_APPS, false) ||
            sharedPreferences.getBoolean(KEY_SHOW_FIREWALL_STATUS_NOTIFICATION, false)) {
            val monitorIntent = Intent(this, AppMonitorService::class.java)
            startForegroundService(monitorIntent)
        }

        if (sharedPreferences.getBoolean(com.arslan.shizuwall.services.FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, false)) {
            com.arslan.shizuwall.services.FloatingButtonService.start(this)
        }

        showAndroid11WarningDialog()

        showChangelogDialogIfNeeded()

        val workingMode = sharedPreferences.getString(KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"
        if (workingMode == com.arslan.shizuwall.WorkingMode.LADB.name) {
            val ladbManager = com.arslan.shizuwall.ladb.LadbManager.getInstance(this)
            if (ladbManager.isPaired() && !ladbManager.isConnected()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    ladbManager.connect()
                }
            }
        }

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

        val settingsButton: View? = findViewById(R.id.settingsButton)
        settingsButton?.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            settingsLauncher.launch(intent)
        }

        val profileButton: View? = findViewById(R.id.profileButton)
        profileButton?.setOnClickListener { showProfilesSheet() }
        updateProfileButtonIcon()
        com.arslan.shizuwall.profiles.ProfileTileSlots.refreshTiles(this)

        val sortButton: View? = findViewById(R.id.sortButton)
        sortButton?.setOnClickListener { showSortDialog() }

        showSystemApps = sharedPreferences.getBoolean(KEY_SHOW_SYSTEM_APPS, false)
        showOtherProfiles = sharedPreferences.getBoolean(KEY_SHOW_OTHER_PROFILES, false)
        moveSelectedTop = sharedPreferences.getBoolean(KEY_MOVE_SELECTED_TOP, false)
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
                            setToggleChecked(state.enabled)
                        }

                        syncSelectionFromState(state)

                        appListAdapter.setSelectionEnabled(!state.enabled || firewallMode.allowsDynamicSelection())
                        applyListInteractionState()

                        updateSelectedCount()
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
                else -> {
                    val userId = profileChipIds.entries.firstOrNull { it.value == checkedId }?.key
                    if (userId != null) {
                        currentProfileUserId = userId
                        Category.PROFILE
                    } else {
                        Category.NONE
                    }
                }
            }
            sortAndFilterApps(preserveScrollPosition = false, scrollToTop = true)
        }

        updateCategoryChips()

        val hasWarmCache = restoreAppListFromCache()
        appListRefreshPending = false
        loadInstalledApps(showLoadingIfListEmpty = !hasWarmCache)

        try {
            if (sharedPreferences.getBoolean(KEY_AUTO_ENABLE_ON_SHIZUKU_START, false) &&
                !loadFirewallEnabled() && !pendingAutoEnable && Shizuku.pingBinder()
            ) {
                autoEnableOnShizukuStart()
            }
        } catch (_: Exception) {
        }

        val savedCount = sharedPreferences.getInt(KEY_SELECTED_COUNT, 0)
        selectedCountText.text = savedCount.toString()

        isFirewallEnabled = loadFirewallEnabled()
        activeFirewallPackages.addAll(loadActivePackages())
        setToggleChecked(isFirewallEnabled)

        appListAdapter.setSelectionEnabled(!isFirewallEnabled || firewallMode.allowsDynamicSelection())
        updateInteractiveViews()
        applyListInteractionState()

        if (::firewallToggle.isInitialized) {
            firewallToggle.isEnabled = isFirewallEnabled || savedCount > 0
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            private var backPressedOnce = false

            override fun handleOnBackPressed() {
                if (backPressedOnce) {
                    finish()
                    return
                }

                backPressedOnce = true
                Toast.makeText(this@MainActivity, getString(R.string.exit_confirm_message), Toast.LENGTH_SHORT).show()

                recyclerView.postDelayed({ backPressedOnce = false }, 2000)
            }
        })

    }

    override fun onResume() {
        super.onResume()

        if (!::firewallToggle.isInitialized) {
            return
        }

        if (pendingAutoEnable && runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)) {
            pendingAutoEnable = false
            val pkgs = pendingAutoEnableSelectedApps ?: loadSelectedApps().toList()
            pendingAutoEnableSelectedApps = null
            firewallToggle.post { if (!loadFirewallEnabled()) autoEnableOnShizukuStart(pkgs) }
        }

        if (!isFirewallProcessRunning) {
            setToggleChecked(loadFirewallEnabled())
        }

        firewallMode = FirewallMode.fromName(sharedPreferences.getString(KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name))
        updateFirewallToggleThumbIcon()

        reconcileActiveProfile()
        updateProfileButtonIcon()
        refreshBackendBanner()

        if (!isFirewallProcessRunning) {
            isFirewallEnabled = loadFirewallEnabled()
        } else {
            setToggleChecked(isEnablingProcess)
        }
        applyListInteractionState()
        appListAdapter.setHybridModeEnabled(firewallMode == FirewallMode.HYBRID)
        appListAdapter.setInfoButtonEnabled(
            sharedPreferences.getBoolean(KEY_SHOW_APP_INFO_BUTTON, true)
        )
        val visibilityTriggeredReload = syncAppVisibilityPreferences()
        if (appListRefreshPending) {
            appListRefreshPending = false
            if (!visibilityTriggeredReload) {
                loadInstalledApps()
            }
        }
        
        ForegroundDetectionService.sync(this)

    }

    override fun onStart() {
        super.onStart()

        try {
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            registerReceiver(packageBroadcastReceiver, filter)
        } catch (e: IllegalArgumentException) {
        } catch (e: Exception) {
        }
    }

    override fun onStop() {
        super.onStop()

        MultiUserApps.invalidate()
        appListRefreshPending = true

        try {
            unregisterReceiver(packageBroadcastReceiver)
        } catch (e: IllegalArgumentException) {
        } catch (e: Exception) {
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
            }
        }
        firewallRepo?.close()
    }

    private fun checkShizukuPermission() {
        if (Shizuku.isPreV11()) {
            val d = MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.shizuku_update_required))
                .setMessage(getString(R.string.shizuku_version_too_old))
                .setPositiveButton(getString(R.string.ok), null)
                .setCancelable(true)
                .create()
            d.show()
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            return
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            val d = MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.permission_required))
                .setMessage(getString(R.string.shizuku_permission_required_message))
                .setPositiveButton(getString(R.string.ok), null)
                .setCancelable(true)
                .create()
            d.show()
        } else {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        }
    }

    private fun onRequestPermissionsResult(requestCode: Int, grantResult: Int) {
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            SHIZUKU_PERMISSION_REQUEST_CODE -> {
                if (granted) {
                    Toast.makeText(this, getString(R.string.shizuku_permission_granted), Toast.LENGTH_SHORT).show()
                    if (pendingToggleEnable) {
                        pendingToggleEnable = false
                        val selectedAppPkgs = pendingEnableSelectedApps ?: appList.filter { it.isSelected }.map { it.key }
                        val selectedApps = appList.filter { selectedAppPkgs.contains(it.packageName) }
                        pendingEnableSelectedApps = null
                        if (selectedApps.isNotEmpty()) {
                            setToggleChecked(true)
                            val allowPkgs = getWhitelistAllowPackages(selectedAppPkgs)
                            showFirewallConfirmDialog(selectedApps, selectedAppPkgs, allowPkgs)
                        } else {
                            setToggleChecked(false)
                        }
                    } else if (pendingToggleDisable) {
                        pendingToggleDisable = false
                        val pkgs = pendingDisableActivePackages ?: activeFirewallPackages.toList()
                        pendingDisableActivePackages = null
                        applyFirewallState(false, pkgs)
                    }
                    else if (pendingAutoEnable) {
                        pendingAutoEnable = false
                        val pkgs = pendingAutoEnableSelectedApps ?: appList.filter { it.isSelected }.map { it.key }
                        pendingAutoEnableSelectedApps = null
                        autoEnableOnShizukuStart(pkgs)
                    }
                } else {
                    pendingToggleEnable = false
                    pendingToggleDisable = false
                    pendingEnableSelectedApps = null
                    pendingDisableActivePackages = null
                    pendingAutoEnable = false
                    pendingAutoEnableSelectedApps = null
                    setToggleChecked(isFirewallEnabled)
                    val d = MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.permission_denied))
                        .setMessage(getString(R.string.shizuku_permission_required_message))
                        .setPositiveButton(getString(R.string.ok), null)
                        .setCancelable(true)
                        .create()
                    d.show()
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

        if (workingMode == WorkingMode.LADB.name) return showLadbDaemonDialog {}

        try {
            if (!Shizuku.pingBinder()) {
                showShizukuNotRunningDialog()
                return false
            }
        } catch (e: Exception) {
        }

        if (Shizuku.isPreV11()) {
            Toast.makeText(this, getString(R.string.shizuku_version_old_toast), Toast.LENGTH_SHORT).show()
            return false
        }

        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            true
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            Toast.makeText(this, getString(R.string.grant_shizuku_permission_settings), Toast.LENGTH_LONG).show()
            false
        } else {
            Shizuku.requestPermission(code)
            false
        }
    }

    private fun setupSearchView() {
        searchView = findViewById(R.id.searchView)
        searchView.queryHint = getString(R.string.search_app)

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

                if (::recyclerView.isInitialized) recyclerView.itemAnimator = null
                appListAdapter.submitList(filteredAppList.toList()) {
                    if (::recyclerView.isInitialized) recyclerView.itemAnimator = defaultItemAnimator
                    updateSelectedCount()
                }
                updateEmptyState()
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

                showBulkSelectDialog(select = true)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (selectedCountText.isEnabled) showBulkSelectDialog(select = false)
            }
        })

        selectedCountText.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            if (!selectedCountText.isEnabled) false else {
                v.performClick()
                true
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        appListLoadingContainer = findViewById(R.id.appListLoadingContainer)
        appListEmptyContainer = findViewById(R.id.appListEmptyContainer)
        appListEmptyTitle = findViewById(R.id.appListEmptyTitle)
        appListEmptyMessage = findViewById(R.id.appListEmptyMessage)
        appListEmptyAction = findViewById(R.id.appListEmptyAction)
        appListEmptyAction.setOnClickListener { enableShowSystemAppsFromEmptyState() }
        backendBanner = findViewById(R.id.backendBanner)
        backendBannerMessage = findViewById(R.id.backendBannerMessage)
        backendBannerAction = findViewById(R.id.backendBannerAction)
        backendBannerAction.setOnClickListener { openBackendSetup() }
        recyclerView.layoutManager = LinearLayoutManager(this)
        appListAdapter = AppListAdapter(
            onAppClick = { appInfo ->
                if (isFirewallProcessRunning) return@AppListAdapter
                if (firewallMode.allowsDynamicSelection() && isFirewallEnabled && !checkPermission(SHIZUKU_PERMISSION_REQUEST_CODE)) {
                    appListAdapter.notifyDataSetChanged()
                    return@AppListAdapter
                }

                val idx = appList.indexOfFirst { it.key == appInfo.key }
                if (idx != -1) {
                    appList[idx] = appInfo
                }
                updateSelectedCount()
                saveSelectedApps()
                sortAndFilterApps(preserveScrollPosition = true)

                if (isFirewallEnabled && firewallMode == FirewallMode.HYBRID && appInfo.appFirewallMode != 0) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        if (appInfo.appFirewallMode == 2) {
                            ScreenLockMonitorService.sync(this@MainActivity)
                        }
                    }
                } else if (isFirewallEnabled && firewallMode.allowsDynamicSelection()) {
                    val pkg = appInfo.key
                    val isSelected = appInfo.isSelected
                    
                    lifecycleScope.launch(Dispatchers.IO) {
                        lastOperationErrorDetails.clear()

                        val shouldBlock = FirewallTargets.shouldBlockOnToggle(firewallMode, isSelected)
                        val res = runCommandDetailed(FirewallCommands.networking(pkg, !shouldBlock))
                        val success = res.isEffectivelySuccess
                        
                        withContext(Dispatchers.Main) {
                            if (success) {
                                if (shouldBlock) {
                                    activeFirewallPackages.add(pkg)
                                } else {
                                    activeFirewallPackages.remove(pkg)
                                }
                                saveActivePackages(activeFirewallPackages)
                            } else {
                                if (shouldBlock) {
                                    val skipErrorDialog = sharedPreferences.getBoolean(KEY_SKIP_ERROR_DIALOG, false)
                                    val keepErrorAppsSelected = sharedPreferences.getBoolean(KEY_KEEP_ERROR_APPS_SELECTED, false)

                                    if (!(skipErrorDialog && keepErrorAppsSelected)) revertSelection(listOf(pkg), !isSelected)
                                    lastOperationErrorDetails[pkg] = res.stderr.ifEmpty { res.stdout }
                                    showOperationErrorsDialog(listOf(pkg), lastOperationErrorDetails)
                                } else {
                                    revertSelection(listOf(pkg), !isSelected)
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
            onAppIconClick = { appInfo ->
                AppInfoDialog.show(this, this, appInfo.packageName, appInfo.appName, appInfo.userId)
            }
        )
        appListAdapter.setHybridModeEnabled(firewallMode == FirewallMode.HYBRID)
        recyclerView.adapter = appListAdapter
        defaultItemAnimator = recyclerView.itemAnimator
        (defaultItemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false

    }

    private fun selectableFilteredApps(): List<AppInfo> =
        filteredAppList.filter { AppIds.isBlockable(it.uid) }

    private fun showBulkSelectDialog(select: Boolean) {
        val filtered = selectableFilteredApps().filter { it.isSelected != select }
        val all = appList.filter { it.isSelected != select && AppIds.isBlockable(it.uid) && (showSystemApps || !it.isSystem || it.isSelected) }
        if (all.isEmpty()) return
        val apply = { changed: List<AppInfo> ->
            val keys = changed.map { it.key }.toSet()
            for (i in appList.indices) if (appList[i].key in keys) appList[i] = appList[i].copy(isSelected = select)
            updateSelectedCount()
            saveSelectedApps()
            sortAndFilterApps(preserveScrollPosition = true)
            applyBulkNetworking(keys.toList(), isSelected = select, revertOnFail = select,
                failToastRes = if (select) R.string.failed_to_update_rules_count else R.string.failed_to_unblock_count)
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(if (select) R.string.select_all else R.string.unselect_all))
            .setNegativeButton(getString(R.string.cancel), null)
        val narrowed = filtered.isNotEmpty() && (currentQuery.isNotEmpty() || currentCategory != Category.NONE)
        if (narrowed) {
            dialog.setPositiveButton(getString(R.string.bulk_only_filtered, filtered.size)) { _, _ -> apply(filtered) }
            if (all.size > filtered.size) {
                dialog.setNeutralButton(getString(R.string.bulk_all_apps, all.size)) { _, _ -> apply(all) }
            }
        } else {
            dialog.setPositiveButton(getString(R.string.bulk_all_apps, all.size)) { _, _ -> apply(all) }
        }
        dialog.show()
    }

    private fun applyBulkNetworking(keys: List<String>, isSelected: Boolean, revertOnFail: Boolean, failToastRes: Int) {
        if (!isFirewallEnabled || !firewallMode.allowsDynamicSelection() || keys.isEmpty()) return
        lifecycleScope.launch(Dispatchers.IO) {
            lastOperationErrorDetails.clear()
            val shouldBlock = FirewallTargets.shouldBlockOnToggle(firewallMode, isSelected)
            val results = runCommandsDetailed(FirewallCommands.networkingAll(keys, !shouldBlock))
            val successful = mutableListOf<String>()
            val failed = mutableListOf<String>()
            keys.forEachIndexed { index, pkg ->
                val res = results[index]
                if (res.isEffectivelySuccess) successful.add(pkg)
                else {
                    failed.add(pkg)
                    lastOperationErrorDetails[pkg] = res.stderr.ifEmpty { res.stdout }
                }
            }
            withContext(Dispatchers.Main) {
                if (successful.isNotEmpty()) {
                    if (shouldBlock) activeFirewallPackages.addAll(successful) else activeFirewallPackages.removeAll(successful)
                    saveActivePackages(activeFirewallPackages)
                }
                if (failed.isEmpty()) return@withContext
                val showDialog = revertOnFail && shouldBlock
                val keepFailed = showDialog &&
                    sharedPreferences.getBoolean(KEY_SKIP_ERROR_DIALOG, false) &&
                    sharedPreferences.getBoolean(KEY_KEEP_ERROR_APPS_SELECTED, false)
                if (revertOnFail && !keepFailed) revertSelection(failed, !isSelected)
                if (showDialog) showOperationErrorsDialog(failed, lastOperationErrorDetails)
                else Toast.makeText(this@MainActivity, getString(failToastRes, failed.size), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun revertSelection(keys: List<String>, isSelected: Boolean) {
        for (pkg in keys) {
            val idx = appList.indexOfFirst { it.key == pkg }
            if (idx != -1) appList[idx] = appList[idx].copy(isSelected = isSelected)
        }
        updateSelectedCount()
        saveSelectedApps()
        sortAndFilterApps(preserveScrollPosition = true)
    }

    private fun toggleFavorite(appInfo: AppInfo) {
        val idx = appList.indexOfFirst { it.key == appInfo.key }
        if (idx != -1) {
            val newFavoriteState = !appList[idx].isFavorite
            appList[idx] = appList[idx].copy(isFavorite = newFavoriteState)
            
            saveFavoriteApps()
            
            if (currentCategory == Category.FAVORITES && !newFavoriteState) {
                filteredAppList.removeAll { it.key == appInfo.key }
            } else if (currentCategory == Category.FAVORITES && newFavoriteState) {
                val filteredIdx = filteredAppList.indexOfFirst { it.key == appInfo.key }
                if (filteredIdx != -1) {
                    filteredAppList[filteredIdx] = appList[idx]
                }
            } else {
                val filteredIdx = filteredAppList.indexOfFirst { it.key == appInfo.key }
                if (filteredIdx != -1) {
                    filteredAppList[filteredIdx] = appList[idx]
                }
            }
            
            appListAdapter.submitList(filteredAppList.toList())
            updateEmptyState()
        }
    }

    private fun saveFavoriteApps() {
        val favoritePackages = appList.filter { it.isFavorite }.map { it.key }.toSet()
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

        val baseList: List<AppInfo> = when (currentCategory) {
            Category.NONE -> appList
            Category.FAVORITES -> appList.filter { it.isFavorite }
            Category.SYSTEM -> appList.filter { it.isSystem }
            Category.USER -> appList.filter { !it.isSystem }
            Category.SELECTED -> appList.filter { it.isSelected }
            Category.UNSELECTED -> appList.filter { !it.isSelected }
            Category.PROFILE -> appList.filter { it.userId == currentProfileUserId }
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
    }

    private fun showSortDialog() {
        val sheetView = LayoutInflater.from(this).inflate(R.layout.sheet_sort, null)
        val sheet = InlineBottomSheet(this, sheetView)

        val radioGroup = sheetView.findViewById<RadioGroup>(R.id.radioGroupSort)
        val checkboxShowSystem = sheetView.findViewById<MaterialCheckBox>(R.id.checkboxShowSystemApps)
        val checkboxShowOtherProfiles = sheetView.findViewById<MaterialCheckBox>(R.id.checkboxShowOtherProfiles)

        when (currentSortOrder) {
            SortOrder.INSTALL_TIME -> radioGroup.check(R.id.radioInstallTime)
            SortOrder.NAME_ASC -> radioGroup.check(R.id.radioNameAsc)
            SortOrder.NAME_DESC -> radioGroup.check(R.id.radioNameDesc)
        }

        checkboxShowSystem.isChecked = showSystemApps
        
        checkboxShowOtherProfiles.isChecked = showOtherProfiles

        checkboxShowOtherProfiles.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked && showOtherProfiles && appList.any { it.userId != 0 && it.isSelected }) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.show_other_profiles)
                    .setMessage(R.string.show_other_profiles_uncheck_confirm)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        checkboxShowOtherProfiles.isChecked = true
                    }
                    .setOnCancelListener {
                        checkboxShowOtherProfiles.isChecked = true
                    }
                    .show()
            }
        }

        if (isFirewallEnabled) {
            checkboxShowSystem.isEnabled = false
            checkboxShowOtherProfiles.isEnabled = false
        }

        checkboxShowSystem.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked && showSystemApps && appList.any { it.isSystem && it.isSelected }) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.show_system_apps)
                    .setMessage(R.string.show_system_apps_uncheck_confirm)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        checkboxShowSystem.isChecked = true
                    }
                    .setOnCancelListener {
                        checkboxShowSystem.isChecked = true
                    }
                    .show()
            }
        }

        sheetView.findViewById<MaterialButton>(R.id.sortCancelButton).setOnClickListener { sheet.dismiss() }

        var pendingApply: (() -> Unit)? = null
        sheet.setOnDismissListener {
            val action = pendingApply
            pendingApply = null
            action?.invoke()
        }

        sheetView.findViewById<MaterialButton>(R.id.sortApplyButton).setOnClickListener {
            pendingApply = {
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

                val newShowSystem = checkboxShowSystem.isChecked
                val showSystemChanged = newShowSystem != showSystemApps
                if (showSystemChanged) {
                    applyShowSystemAppsChange(newShowSystem)
                    sortAndFilterApps(preserveScrollPosition = false, scrollToTop = true, animate = false)
                } else if (sortChanged) {
                    sortAndFilterApps(preserveScrollPosition = false, scrollToTop = true, animate = true)
                }

                val newShowOtherProfiles = checkboxShowOtherProfiles.isChecked
                if (newShowOtherProfiles != showOtherProfiles) {
                    applyShowOtherProfilesChange(newShowOtherProfiles)
                    loadInstalledApps(showLoadingIfListEmpty = true)
                }
            }
            sheet.dismiss()
        }

        sheet.show()
    }

    private fun applyShowSystemAppsChange(newValue: Boolean) {
        showSystemApps = newValue
        sharedPreferences.edit().putBoolean(KEY_SHOW_SYSTEM_APPS, showSystemApps).apply()
        reconcileActiveProfile()

        if (!showSystemApps) {
            val hadSelection = appList.any { it.isSystem && it.isSelected }
            for (i in appList.indices) {
                if (appList[i].isSystem && appList[i].isSelected) {
                    appList[i] = appList[i].copy(isSelected = false)
                }
            }
            if (hadSelection) {
                saveSelectedApps()
                updateSelectedCount()
            }
        }

        updateCategoryChips()
    }

    private fun applyShowOtherProfilesChange(newValue: Boolean) {
        showOtherProfiles = newValue
        sharedPreferences.edit().putBoolean(KEY_SHOW_OTHER_PROFILES, showOtherProfiles).apply()

        if (!showOtherProfiles) {
            val hadSelection = appList.any { it.userId != 0 && it.isSelected }
            for (i in appList.indices) {
                if (appList[i].userId != 0 && appList[i].isSelected) {
                    appList[i] = appList[i].copy(isSelected = false)
                }
            }
            if (hadSelection) {
                saveSelectedApps()
                updateSelectedCount()
            }

            MultiUserApps.invalidate()
        }
    }

    private fun syncAppVisibilityPreferences(): Boolean {
        val prefShowSystem = sharedPreferences.getBoolean(KEY_SHOW_SYSTEM_APPS, false)
        val showSystemChanged = prefShowSystem != showSystemApps
        if (showSystemChanged) {
            applyShowSystemAppsChange(prefShowSystem)
        }

        val prefShowOtherProfiles = sharedPreferences.getBoolean(KEY_SHOW_OTHER_PROFILES, false)
        val showOtherProfilesChanged = prefShowOtherProfiles != showOtherProfiles
        if (showOtherProfilesChanged) {
            applyShowOtherProfilesChange(prefShowOtherProfiles)
        }

        val prefMoveSelectedTop = sharedPreferences.getBoolean(KEY_MOVE_SELECTED_TOP, false)
        val moveSelectedTopChanged = prefMoveSelectedTop != moveSelectedTop
        if (moveSelectedTopChanged) {
            moveSelectedTop = prefMoveSelectedTop
            updateCategoryChips()
        }

        if (showOtherProfilesChanged) {
            loadInstalledApps(showLoadingIfListEmpty = true)
            return true
        }
        if (showSystemChanged || moveSelectedTopChanged) {
            sortAndFilterApps(preserveScrollPosition = false, scrollToTop = true, animate = false)
        }

        return false
    }

    private fun sortAndFilterApps(preserveScrollPosition: Boolean = false, scrollToTop: Boolean = false, animate: Boolean = false, smoothTransition: Boolean = false) {
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
            recyclerView.itemAnimator = null
            appListAdapter.submitList(filteredAppList.toList()) {
                recyclerView.itemAnimator = defaultItemAnimator

                if (preserveScrollPosition && moveSelectedTop && firstVisible != RecyclerView.NO_POSITION) {
                    layoutManager.scrollToPositionWithOffset(firstVisible, offset)
                } else if (scrollToTop) {
                    layoutManager.scrollToPosition(0)
                }

                updateSelectedCount()
                updateEmptyState()

                if (animate || smoothTransition) {
                    val fadeInDelay = if (smoothTransition) 0L else 400L
                    val fadeInDuration = if (smoothTransition) 260L else 200L
                    recyclerView.post {
                        val targetAlpha = if (listDimmed) 0.5f else 1f
                        recyclerView.animate()
                            .alpha(targetAlpha)
                            .setStartDelay(fadeInDelay)
                            .setDuration(fadeInDuration)
                            .start()
                    }
                }
            }
        }

        if (animate || smoothTransition) {
            appList.sortWith(finalComparator)
            filterApps(currentQuery)
            val fadeOutDuration = if (smoothTransition) 180L else 200L
            recyclerView.removeCallbacks(sortFilterRunnable)
            sortFilterRunnable = Runnable { updateList() }
            recyclerView.animate().cancel()
            recyclerView.animate()
                .alpha(0f)
                .setDuration(fadeOutDuration)
                .start()
            recyclerView.postDelayed(sortFilterRunnable, fadeOutDuration)
        } else {
            appList.sortWith(finalComparator)
            filterApps(currentQuery)
            recyclerView.animate().cancel()
            val targetAlpha = if (listDimmed) 0.5f else 1f
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
            if (isFirewallProcessRunning) {
                setToggleChecked(isEnablingProcess)
                return@setOnCheckedChangeListener
            }
            if (isChecked) {
                profileEnableActive = profileEnableRequested
                profileEnableRequested = false
                val selectedApps = appList.filter { it.isSelected }
                val whitelistAllowPkgs = mutableListOf<String>()
                val targetAppPkgs = if (firewallMode == FirewallMode.WHITELIST) {
                    val selectedPkgs = selectedApps.map { it.key }
                    val result = WhitelistFilter.compute(this, selectedPkgs, showSystemApps)
                    whitelistAllowPkgs.addAll(result.toAllow)
                    result.toBlock
                } else {
                    selectedApps.map { it.key }
                }
                
                if (targetAppPkgs.isEmpty() && !firewallMode.allowsDynamicSelection()) {
                    Toast.makeText(this, getString(R.string.select_at_least_one_app), Toast.LENGTH_SHORT).show()
                    setToggleChecked(false)
                    return@setOnCheckedChangeListener
                }

                if (!checkPermission(SHIZUKU_PERMISSION_REQUEST_CODE)) {
                    pendingToggleEnable = true
                    pendingEnableSelectedApps = targetAppPkgs
                    setToggleChecked(false)
                    return@setOnCheckedChangeListener
                }
                pendingToggleEnable = false
                showFirewallConfirmDialog(selectedApps, targetAppPkgs, whitelistAllowPkgs)
            } else {
                if (!isFirewallEnabled) {
                    return@setOnCheckedChangeListener
                }

                if (!checkPermission(SHIZUKU_PERMISSION_REQUEST_CODE)) {
                    pendingToggleDisable = true
                    pendingDisableActivePackages = activeFirewallPackages.toList()
                    setToggleChecked(true)
                    return@setOnCheckedChangeListener
                }
                pendingToggleDisable = false
                applyFirewallState(false, activeFirewallPackages.toList())
            }
        }
    }

    private fun setToggleChecked(checked: Boolean) {
        suppressToggleListener = true
        firewallToggle.isChecked = checked
        suppressToggleListener = false
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

    private fun showLadbDaemonDialog(onProceed: () -> Unit): Boolean {
        val daemonManager = PersistentDaemonManager(this)
        if (daemonManager.isDaemonRunning()) {
            onProceed()
            return true
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.working_mode_ladb))
            .setMessage(getString(R.string.daemon_not_running))
            .setPositiveButton(getString(R.string.open_daemon_setup)) { _, _ ->
                try { startActivity(Intent(this, com.arslan.shizuwall.LadbSetupActivity::class.java)) } catch (_: Exception) {}
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
        return false
    }

    private fun showShizukuNotRunningDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.shizuku_not_running_title))
            .setMessage(getString(R.string.shizuku_not_running_message))
            .setPositiveButton(getString(R.string.open_shizuku)) { _, _ ->
                launchShizukuApp()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun launchShizukuApp() {
        val pm = packageManager
        val candidates = ShizukuPackageResolver.getLaunchCandidates(this)
        for (pkg in candidates) {
            val launch = pm.getLaunchIntentForPackage(pkg)
            if (launch != null) { startActivity(launch); return }
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/thedjchi/Shizuku/releases")))
        } catch (_: Exception) {}
    }

    private fun showFirewallConfirmDialog(selectedApps: List<AppInfo>, blockPkgs: List<String> = selectedApps.map { it.key }, whitelistAllowApps: List<String> = emptyList()) {
        if (sharedPreferences.getBoolean(KEY_SKIP_ENABLE_CONFIRM, false)) {
            applyFirewallState(true, blockPkgs, whitelistAllowApps)
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_firewall_confirm, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.enable)) { _, _ ->
                applyFirewallState(true, blockPkgs, whitelistAllowApps)
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                setToggleChecked(false)
                pendingEnableSelectedApps = null
            }
            .create()
        val dialogMessage = dialogView.findViewById<TextView>(R.id.dialogMessage)
        val selectedAppsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.selectedAppsRecyclerView)

        dialogMessage.text = getString(R.string.enable_firewall_confirm, selectedApps.size)

        selectedAppsRecyclerView.layoutManager = LinearLayoutManager(this)
        selectedAppsRecyclerView.adapter = SelectedAppsAdapter(selectedApps)

        sizeAppList(selectedAppsRecyclerView, selectedApps.size)

        dialog.show()
    }

    private fun sizeAppList(recycler: RecyclerView, count: Int) {
        val displayMetrics = resources.displayMetrics
        val maxHeight = (displayMetrics.heightPixels * 0.4).toInt()
        val lp = recycler.layoutParams
        lp.height = if ((72 * displayMetrics.density).toInt() * count > maxHeight) maxHeight else ViewGroup.LayoutParams.WRAP_CONTENT
        recycler.layoutParams = lp
        recycler.isNestedScrollingEnabled = false
    }

    private fun showChangelogDialogIfNeeded() {
        val id = changelogId(this)
        if (sharedPreferences.getString(KEY_LAST_SEEN_CHANGELOG, null) == id) return
        sharedPreferences.edit().putString(KEY_LAST_SEEN_CHANGELOG, id).apply()

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.changelog_title, com.arslan.shizuwall.BuildConfig.VERSION_NAME))
            .setMessage(getString(R.string.changelog_message))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showAndroid11WarningDialog() {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.R) return

        if (sharedPreferences.getBoolean(KEY_SKIP_ANDROID11_INFO, false)) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_android11_info, null)
        val checkbox = dialogView.findViewById<CheckBox>(R.id.checkboxDontShowAgain)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                if (checkbox.isChecked) {
                    sharedPreferences.edit().putBoolean(KEY_SKIP_ANDROID11_INFO, true).apply()
                }
            }
            .create()

        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val dialogMessage = dialogView.findViewById<TextView>(R.id.dialogMessage)

        dialogTitle.text = getString(R.string.android11_unsupported_title)
        dialogMessage.text = getString(R.string.android11_unsupported_message)

        dialog.show()
    }

    private fun syncSelectionFromState(state: com.arslan.shizuwall.repo.FirewallState) {
        if (isFirewallProcessRunning) return

        val stateMode = FirewallMode.fromName(state.firewallMode)
        var structureChanged = false

        if (firewallMode != stateMode) {
            firewallMode = stateMode
            appListAdapter.setHybridModeEnabled(stateMode == FirewallMode.HYBRID)
            structureChanged = true
        }
        if (showSystemApps != state.showSystemApps) {
            showSystemApps = state.showSystemApps
            structureChanged = true
        }

        val modes = try { JSONObject(state.appModesJson) } catch (e: Exception) { JSONObject() }
        var selectionChanged = false
        for (i in appList.indices) {
            val info = appList[i]
            val selected = state.selectedApps.contains(info.key)
            val mode = modes.optInt(info.key, 0)
            if (info.isSelected != selected || info.appFirewallMode != mode) {
                appList[i] = info.copy(isSelected = selected, appFirewallMode = mode)
                selectionChanged = true
            }
        }

        if (!selectionChanged && !structureChanged) return

        updateProfileButtonIcon()
        profilesBottomSheet?.notifyActivated(state.activeProfileId)
        updateCategoryChips()
        sortAndFilterApps(preserveScrollPosition = true)
    }

    private fun updateSelectedCount() {
        val count = appList.count { it.isSelected }
        selectedCountText.text = count.toString()

        if (::firewallToggle.isInitialized) {
            firewallToggle.isEnabled = isFirewallEnabled || count > 0 || firewallMode.allowsDynamicSelection()
        }
        
        updateInteractiveViews()
    }

    private fun updateInteractiveViews() {
        val dimmed = listDimmed
        if (::selectedCountText.isInitialized) {
            selectedCountText.isEnabled = !dimmed
            selectedCountText.alpha = if (dimmed) 0.4f else 1.0f
        }
        if (::firewallToggle.isInitialized) {
            firewallToggle.isClickable = !isFirewallProcessRunning
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadInstalledApps(showLoadingIfListEmpty: Boolean = false) {
        lifecycleScope.launch {
            if (showLoadingIfListEmpty && appList.isEmpty()) {
                setAppListLoadingVisible(true)
            }

            if (!MultiUserApps.isEnabled(this@MainActivity)) {
                lifecycleScope.launch(Dispatchers.IO) {
                    MultiUserApps.snapshot(this@MainActivity)
                }
            }

            val result = withContext(Dispatchers.IO) {
                val pm = packageManager
                val rememberDisabled = sharedPreferences.getBoolean(KEY_REMEMBER_DISABLED_APPS, true)
                val queryFlags = if (rememberDisabled) {
                    PackageManager.GET_PERMISSIONS or
                        PackageManager.MATCH_DISABLED_COMPONENTS or
                        PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                } else {
                    PackageManager.GET_PERMISSIONS
                }
                val packages = pm.getInstalledPackages(queryFlags)

                val secondarySnapshot = if (MultiUserApps.isEnabled(this@MainActivity)) {
                    MultiUserApps.snapshot(this@MainActivity)
                } else {
                    MultiUserApps.Snapshot.EMPTY
                }

                val installedPackageNames = packages.mapTo(HashSet(packages.size)) { it.packageName }
                secondarySnapshot.apps.forEach { installedPackageNames.add(it.key) }

                val secondaryScanUsable = secondarySnapshot.apps.isNotEmpty()
                val isKnown = { key: String ->
                    installedPackageNames.contains(key) ||
                        (AppKey.isSecondary(key) && !secondaryScanUsable)
                }

                val savedSelected = loadSelectedApps().toMutableSet()
                val selectedToRemove = if (rememberDisabled) emptyList() else savedSelected.filterNot(isKnown)
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
                
                val chunkSize = 50
                val chunks = packages.chunked(chunkSize)
                val results = chunks.map { chunk ->
                    async(Dispatchers.Default) {
                        val chunkResult = mutableListOf<AppInfo>()
                        for (packageInfo in chunk) {
                            val appInfo = packageInfo.applicationInfo ?: continue
                            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                            if (!appInfo.enabled) continue

                            val packageName = packageInfo.packageName

                            if (ShizukuPackageResolver.isShizukuPackage(this@MainActivity, packageName)) continue
                            if (packageName == selfPkg) continue

                            val hasInternetPermission = packageInfo.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
                            if (!hasInternetPermission) continue

                            val appName = appInfo.loadLabel(pm).toString()
                            val isSelected = savedSelected.contains(packageName)
                            val isFavorite = favoritePackages.contains(packageName)
                            val installTime = packageInfo.firstInstallTime
                            val appMode = modesJson.optInt(packageName, 0)

                            chunkResult.add(AppInfo(appName, packageName, isSelected, isSystemApp, isFavorite, installTime, appMode, uid = appInfo.uid))
                        }
                        chunkResult
                    }
                }.awaitAll().flatten()

                val internetPackages = packages.asSequence()
                    .filter { it.requestedPermissions?.contains(Manifest.permission.INTERNET) == true }
                    .mapTo(HashSet()) { it.packageName }
                val primaryPackageNames = packages.mapTo(HashSet(packages.size)) { it.packageName }

                val secondaryApps = buildSecondaryAppList(
                    snapshot = secondarySnapshot,
                    primaryApps = results,
                    internetPackages = internetPackages,
                    primaryPackageNames = primaryPackageNames,
                    savedSelected = savedSelected,
                    favoritePackages = favoritePackages,
                    modesJson = modesJson
                )

                Pair(results + secondaryApps, isKnown)
            }

            val builtList = result.first
            val isKnownKey = result.second

            var appsWereRemoved = false
            if (!isFirewallProcessRunning) {
                val currentActive = loadActivePackages().toMutableSet()
                val activeToRemove = currentActive.filterNot(isKnownKey)
                appsWereRemoved = activeToRemove.isNotEmpty()
                if (appsWereRemoved) {
                    currentActive.removeAll(activeToRemove.toSet())
                    saveActivePackages(currentActive)
                }
                activeFirewallPackages.clear()
                activeFirewallPackages.addAll(currentActive)
            }

            if (isFirewallEnabled && !isFirewallProcessRunning && activeFirewallPackages.isEmpty() && !firewallMode.allowsDynamicSelection()) {
                isFirewallEnabled = false
                saveFirewallEnabled(false)
                setToggleChecked(false)
                applyListInteractionState()
                
                if (appsWereRemoved) {
                    Toast.makeText(this@MainActivity, getString(R.string.firewall_disabled_active_uninstalled), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.firewall_disabled_no_apps), Toast.LENGTH_SHORT).show()
                }
            }

            if (appList != builtList) {
                appList.clear()
                appList.addAll(builtList)
                dropUnsupportedSelections()
                sortAndFilterApps(preserveScrollPosition = false)
            }
            updateCategoryChips()

            saveAppsCache(builtList)

            if (isAppListLoadingVisible) {
                setAppListLoadingVisible(false)
            }
            
            updateSelectedCount()
        }
    }

    private fun buildSecondaryAppList(
        snapshot: MultiUserApps.Snapshot,
        primaryApps: List<AppInfo>,
        internetPackages: Set<String>,
        primaryPackageNames: Set<String>,
        savedSelected: Set<String>,
        favoritePackages: Set<String>,
        modesJson: JSONObject
    ): List<AppInfo> {
        if (snapshot.apps.isEmpty()) return emptyList()
        val primaryByPackage = primaryApps.associateBy { it.packageName }

        return snapshot.apps.filter { app ->
            app.packageName in internetPackages || app.packageName !in primaryPackageNames
        }.map { app ->
            val base = primaryByPackage[app.packageName]
            val key = app.key
            AppInfo(
                appName = base?.appName
                    ?: CrossUserAppInfo.label(this, app.packageName, app.userId)
                    ?: app.packageName,
                packageName = app.packageName,
                isSelected = savedSelected.contains(key),
                isSystem = app.isSystem,
                isFavorite = favoritePackages.contains(key),
                installTime = base?.installTime ?: 0L,
                appFirewallMode = modesJson.optInt(key, 0),
                userId = app.userId,
                uid = app.uid
            )
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
                val userId = obj.optInt("userId", 0)
                val key = AppKey.of(userId, packageName)

                parsed.add(
                    AppInfo(
                        appName = obj.optString("appName", packageName),
                        packageName = packageName,
                        isSelected = selectedPackages.contains(key),
                        isSystem = obj.optBoolean("isSystem", false),
                        isFavorite = favoritePackages.contains(key),
                        installTime = obj.optLong("installTime", 0L),
                        appFirewallMode = modesJson.optInt(key, 0),
                        userId = userId,
                        uid = obj.optInt("uid", -1)
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
        dropUnsupportedSelections()
        updateCategoryChips()
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
                    put("userId", app.userId)
                    put("uid", app.uid)
                }
            )
        }
        sharedPreferences.edit().putString(KEY_APPS_CACHE_JSON, arr.toString()).apply()
    }

    private inner class DelayedFader(private val view: View) {
        private var shownAt = 0L

        private val showRunnable = Runnable {
            shownAt = SystemClock.uptimeMillis()
            fadeVisibility(view, true)
        }

        private val hideRunnable = Runnable { fadeVisibility(view, false) }

        fun set(visible: Boolean) {
            view.removeCallbacks(showRunnable)
            view.removeCallbacks(hideRunnable)
            if (visible) {
                if (view.visibility == View.VISIBLE) return
                view.postDelayed(showRunnable, PROGRESS_SHOW_DELAY_MS)
                return
            }
            if (view.visibility != View.VISIBLE) return
            val remaining = PROGRESS_MIN_VISIBLE_MS - (SystemClock.uptimeMillis() - shownAt)
            if (remaining <= 0L) {
                fadeVisibility(view, false)
            } else {
                view.postDelayed(hideRunnable, remaining)
            }
        }
    }

    private val firewallProgressFader by lazy { DelayedFader(firewallProgress) }

    private val appListLoadingFader by lazy { DelayedFader(appListLoadingContainer) }

    private fun fadeVisibility(view: View, visible: Boolean) {
        view.animate().cancel()
        if (visible) {
            if (view.visibility != View.VISIBLE) {
                view.alpha = 0f
                view.scaleX = 0.7f
                view.scaleY = 0.7f
                view.visibility = View.VISIBLE
            }
            view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(FADE_IN_DURATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
            return
        }
        if (view.visibility != View.VISIBLE) {
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        view.animate()
            .alpha(0f)
            .scaleX(0.7f)
            .scaleY(0.7f)
            .setDuration(FADE_OUT_DURATION_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                view.visibility = View.GONE
                view.alpha = 1f
                view.scaleX = 1f
                view.scaleY = 1f
            }
            .start()
    }

    private fun setAppListLoadingVisible(visible: Boolean) {
        if (!::appListLoadingContainer.isInitialized) return
        if (visible == isAppListLoadingVisible) return

        isAppListLoadingVisible = visible
        appListLoadingFader.set(visible)
        updateEmptyState()
    }

    private fun refreshBackendBanner() {
        if (!::backendBanner.isInitialized) return

        lifecycleScope.launch {
            val ready = withContext(Dispatchers.IO) {
                try {
                    com.arslan.shizuwall.utils.FirewallUtils.checkBackendReady(this@MainActivity, showToast = false)
                } catch (_: Throwable) {
                    false
                }
            }
            if (!::backendBanner.isInitialized) return@launch

            backendBanner.visibility = if (ready) View.GONE else View.VISIBLE
            if (ready) return@launch

            backendBannerMessage.setText(
                when (currentWorkingMode()) {
                    WorkingMode.LADB -> R.string.backend_banner_message_ladb
                    WorkingMode.ROOT -> R.string.backend_banner_message_root
                    else -> R.string.backend_banner_message_shizuku
                }
            )
        }
    }

    private fun currentWorkingMode(): WorkingMode =
        WorkingMode.fromName(sharedPreferences.getString(KEY_WORKING_MODE, WorkingMode.SHIZUKU.name))

    private fun openBackendSetup() {
        val intent = when (currentWorkingMode()) {
            WorkingMode.LADB -> Intent(this, com.arslan.shizuwall.LadbSetupActivity::class.java)
            WorkingMode.ROOT -> Intent(this, BackendSettingsActivity::class.java)
            else -> Intent(this, ShizukuSetupActivity::class.java)
        }
        try { startActivity(intent) } catch (_: Exception) { }
    }

    private fun updateEmptyState() {
        if (!::appListEmptyContainer.isInitialized) return

        val show = filteredAppList.isEmpty() && !isAppListLoadingVisible
        appListEmptyContainer.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return

        val searching = currentQuery.isNotEmpty()
        val offerSystemApps = !searching && !showSystemApps && currentCategory != Category.SYSTEM

        appListEmptyTitle.setText(
            if (searching) R.string.empty_app_list_search_title else R.string.empty_app_list_title
        )
        appListEmptyMessage.setText(
            if (searching) R.string.empty_app_list_search_message else R.string.empty_app_list_message
        )
        appListEmptyMessage.visibility = if (searching || offerSystemApps) View.VISIBLE else View.GONE
        appListEmptyAction.visibility = if (offerSystemApps) View.VISIBLE else View.GONE
    }

    private fun enableShowSystemAppsFromEmptyState() {
        if (showSystemApps) return
        showSystemApps = true
        sharedPreferences.edit().putBoolean(KEY_SHOW_SYSTEM_APPS, showSystemApps).apply()
        reconcileActiveProfile()
        updateCategoryChips()
        sortAndFilterApps(preserveScrollPosition = false, scrollToTop = true, animate = false)
    }

    private fun dropUnsupportedSelections() {
        val dropped = mutableSetOf<String>()
        appList.forEachIndexed { index, app ->
            if (app.isSelected && !AppIds.isBlockable(app.uid)) {
                appList[index] = app.copy(isSelected = false)
                dropped.add(app.key)
            }
        }
        if (dropped.isEmpty()) return
        com.arslan.shizuwall.profiles.ProfilesStore.dropPackages(this, dropped)
        saveSelectedApps()
        updateSelectedCount()
    }

    private fun saveSelectedApps() {
        val selectedPackages = appList
            .filter { it.isSelected && !ShizukuPackageResolver.isShizukuPackage(this, it.packageName) }
            .map { it.key }
            .toSet()

        val modesJson = JSONObject()
        appList.filter { it.appFirewallMode != 0 }.forEach {
            modesJson.put(it.key, it.appFirewallMode)
        }
            
        sharedPreferences.edit()
            .putStringSet(KEY_SELECTED_APPS, selectedPackages)
            .putInt(KEY_SELECTED_COUNT, selectedPackages.size)
            .putString(KEY_APP_MODES, modesJson.toString())
            .apply()

        reconcileActiveProfile()
    }

    private fun reconcileActiveProfile() {
        val activeId = com.arslan.shizuwall.profiles.ProfilesStore.activeProfileId(this) ?: return
        val profile = com.arslan.shizuwall.profiles.ProfilesStore.getById(this, activeId)
        if (profile == null) {
            com.arslan.shizuwall.profiles.ProfilesStore.setActiveProfileId(this, null)
            updateProfileButtonIcon()
            com.arslan.shizuwall.utils.FirewallUtils.notifyProfileSurfaces(this)
            return
        }
        val matches = com.arslan.shizuwall.profiles.ProfilesStore.matches(
            profile,
            com.arslan.shizuwall.profiles.ProfilesStore.captureCurrent(this)
        )
        if (!matches) {
            com.arslan.shizuwall.profiles.ProfilesStore.setActiveProfileId(this, null)
            updateProfileButtonIcon()
            com.arslan.shizuwall.utils.FirewallUtils.notifyProfileSurfaces(this)
        }
    }

    private fun loadSelectedApps(): Set<String> {
        return sharedPreferences.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
    }

    private fun saveFirewallEnabled(enabled: Boolean) = FirewallUtils.saveFirewallEnabled(this, sharedPreferences, enabled)

    private fun loadFirewallEnabled(): Boolean = FirewallUtils.loadFirewallEnabled(sharedPreferences)

    private fun saveActivePackages(packages: Set<String>) = FirewallUtils.saveActivePackages(sharedPreferences, packages.toSet())

    private fun loadActivePackages(): Set<String> = FirewallUtils.loadActivePackages(sharedPreferences)

    private fun applyFirewallState(enable: Boolean, packageNames: List<String>, whitelistAllowApps: List<String> = emptyList()) {
        if (enable && packageNames.isEmpty() && !firewallMode.allowsDynamicSelection()) return

        @Suppress("NAME_SHADOWING")
        val packageNames = if (sharedPreferences.getBoolean(KEY_REMEMBER_DISABLED_APPS, true)) {
            packageNames.filter { isPackageInstalledIncludingDisabled(AppKey.packageOf(it)) }
        } else {
            packageNames
        }

        val effectivePackageNames = if (enable) {
            FirewallTargets.effectiveBlockList(
                firewallMode,
                packageNames,
                ScreenLockModeReceiver.isDeviceLocked(this),
                FirewallTargets.parseAppModes(sharedPreferences.getString(KEY_APP_MODES, "{}"))
            )
        } else {
            packageNames
        }

        isFirewallProcessRunning = true
        isEnablingProcess = enable
        lifecycleScope.launch {
            firewallProgressFader.set(true)
            applyListInteractionState()
            
            if (enable && firewallMode.requiresForegroundDetection()) {
                warnIfForegroundDetectionUnavailable()
            }
            
            try {
                val (installed, missing) = withContext(Dispatchers.IO) {
                    filterInstalledPackages(effectivePackageNames)
                }

                if (enable && installed.isEmpty() && !firewallMode.allowsDynamicSelection()) {
                    Toast.makeText(this@MainActivity, getString(R.string.none_selected_apps_installed), Toast.LENGTH_SHORT).show()
                    setToggleChecked(false)
                    appListAdapter.setSelectionEnabled(true)
                    updateInteractiveViews()
                    return@launch
                }

                if (missing.isNotEmpty()) {
                    Toast.makeText(this@MainActivity, getString(R.string.selected_apps_not_installed, missing.size), Toast.LENGTH_SHORT).show()
                }

                val (successful, failed) = withContext(Dispatchers.IO) {
                    if (enable) {
                        enableFirewall(installed, whitelistAllowApps)
                    } else {
                        disableFirewall(installed)
                    }
                }

                if (enable) {
                    if (successful.isNotEmpty() || firewallMode.allowsDynamicSelection()) {
                        isFirewallEnabled = true
                        activeFirewallPackages.clear()
                        activeFirewallPackages.addAll(successful)
                        saveActivePackages(activeFirewallPackages)
                        saveFirewallEnabled(true)

                        setToggleChecked(true)
                        
                        if (sharedPreferences.getBoolean(com.arslan.shizuwall.services.FloatingButtonService.KEY_FLOATING_BUTTON_ENABLED, false)) {
                            com.arslan.shizuwall.services.FloatingButtonService.start(this@MainActivity)
                        }
                        

                } else {
                    setToggleChecked(false)
                    Toast.makeText(this@MainActivity, getString(R.string.failed_to_enable_firewall), Toast.LENGTH_SHORT).show()
                    applyListInteractionState()
                }
            } else {
                if (successful.isNotEmpty()) {
                    activeFirewallPackages.removeAll(successful)
                    saveActivePackages(activeFirewallPackages)
                }

                    if (successful.isNotEmpty() || installed.isEmpty()) {
                    isFirewallEnabled = false
                    profileEnableActive = false
                    saveFirewallEnabled(false)
                    setToggleChecked(false)
                    applyListInteractionState()

                    if (installed.isEmpty()) {
                        activeFirewallPackages.clear()
                        saveActivePackages(activeFirewallPackages)
                    }
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.failed_to_disable_firewall), Toast.LENGTH_SHORT).show()
                }
            }

            val showedLadbRecoveryDialog = enable && maybeShowLadbDaemonRecoveryDialog(failed, lastOperationErrorDetails)

            if (failed.isNotEmpty()) {
                val skipErrorDialog = sharedPreferences.getBoolean(KEY_SKIP_ERROR_DIALOG, false)
                val keepErrorAppsSelected = sharedPreferences.getBoolean(KEY_KEEP_ERROR_APPS_SELECTED, false)
                
                if (!(skipErrorDialog && keepErrorAppsSelected)) {
                    for (pkg in failed) {
                        val idx = appList.indexOfFirst { it.key == pkg }
                        if (idx != -1) {
                            if (firewallMode == FirewallMode.WHITELIST) {
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
                firewallProgressFader.set(false)
                isFirewallProcessRunning = false
                applyListInteractionState()
            }
        }
    }

    private suspend fun warnIfForegroundDetectionUnavailable() {
        if (ForegroundTaskProbe.query(this) != null || ForegroundTaskProbe.isAvailable) return
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.foreground_detection_unavailable))
            .setMessage(getString(R.string.foreground_detection_unavailable_message))
            .setPositiveButton(getString(R.string.ok), null)
            .create()
            .show()
    }

    private fun filterInstalledPackages(packageNames: List<String>): Pair<List<String>, List<String>> {
        val installed = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val pm = packageManager
        val secondaryKeys = MultiUserApps.cachedSnapshot(this).apps.mapTo(HashSet()) { it.key }
        for (pkg in packageNames) {
            if (ShizukuPackageResolver.isShizukuPackage(this, AppKey.packageOf(pkg))) {
                missing.add(pkg)
                continue
            }

            if (AppKey.isSecondary(pkg)) {
                if (secondaryKeys.contains(pkg)) installed.add(pkg) else missing.add(pkg)
                continue
            }
            try {
                pm.getPackageInfo(pkg, 0)
                installed.add(pkg)
            } catch (e: PackageManager.NameNotFoundException) {
                missing.add(pkg)
            } catch (e: Exception) {
                missing.add(pkg)
            }
        }
        return Pair(installed, missing)
    }

    private suspend fun enableFirewall(
        packageNames: List<String>,
        whitelistAllowApps: List<String> = emptyList(),
        preUnblockApps: List<String> = emptyList()
    ): Pair<List<String>, List<String>> {
        val successful = mutableListOf<String>()
        val failed = mutableListOf<String>()
        lastOperationErrorDetails.clear()

        val chain3Result = runCommandDetailed(FirewallCommands.CHAIN3_ENABLE)
        if (!chain3Result.success) {
            val msg = chain3Result.stderr.ifEmpty { chain3Result.stdout }
            runCommandsDetailed(FirewallCommands.unblockAll(preUnblockApps))
            if (packageNames.isEmpty() && whitelistAllowApps.isEmpty()) {
                lastOperationErrorDetails["_chain3"] = msg
                return Pair(successful, failed)
            }
            for (pkg in packageNames) {
                lastOperationErrorDetails[pkg] = msg
                failed.add(pkg)
            }
            return Pair(successful, failed)
        }

        val toBlock = if (FirewallTargets.skipsBlockingAtEnable(firewallMode)) emptyList() else packageNames
        val allowCommands = FirewallCommands.unblockAll(preUnblockApps + whitelistAllowApps)
        val commands = allowCommands + FirewallCommands.blockAll(toBlock)
        if (commands.isEmpty()) return Pair(successful, failed)

        val results = runCommandsDetailed(commands)
        for ((index, packageName) in toBlock.withIndex()) {
            val res = results[allowCommands.size + index]
            if (res.isEffectivelySuccess) {
                successful.add(packageName)
            } else {
                failed.add(packageName)
                lastOperationErrorDetails[packageName] = res.stderr.ifEmpty { res.stdout }
            }
        }

        return Pair(successful, failed)
    }

    private suspend fun disableFirewall(packageNames: List<String>): Pair<List<String>, List<String>> {
        val successful = mutableListOf<String>()
        val failed = mutableListOf<String>()
        lastOperationErrorDetails.clear()

        val toUnblock = packageNames.sortedBy { AppKey.isSecondary(it) }.toMutableList()
        if (firewallMode.requiresForegroundDetection()) {
            val currentFgApp = sharedPreferences.getString(MainActivity.KEY_SMART_FOREGROUND_APP, null)
            if (!currentFgApp.isNullOrEmpty() && !toUnblock.contains(currentFgApp)) {
                toUnblock.add(currentFgApp)
            }
        }

        val restoreResults = runCommandsDetailed(FirewallCommands.unblockAll(toUnblock))
        for ((index, packageName) in toUnblock.withIndex()) {
            val res = restoreResults[index]
            if (res.isEffectivelySuccess) {
                successful.add(packageName)
            } else {
                failed.add(packageName)
                lastOperationErrorDetails[packageName] = res.stderr.ifEmpty { res.stdout }
            }
        }
        runCommandDetailed(FirewallCommands.CHAIN3_DISABLE)

        if (firewallMode.requiresForegroundDetection()) {
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

    private suspend fun runCommandsDetailed(commands: List<String>): List<com.arslan.shizuwall.shell.ShellResult> {
        return if (commands.isEmpty()) emptyList()
        else ShellExecutorProvider.forContext(this).execBatch(commands)
    }

    private fun applyListInteractionState() {
        val dimmed = listDimmed
        recyclerView.alpha = if (dimmed) 0.5f else 1f
        recyclerView.isEnabled = !dimmed
        recyclerView.isClickable = !dimmed
        appListAdapter.setSelectionEnabled(!dimmed)
        appListAdapter.setFavoriteEnabled(!(dimmed && isFirewallProcessRunning))
        updateInteractiveViews()
    }

    private fun handlePackageRemoved(pkg: String) {
        runOnUiThread {
            var changed = false
            val it = appList.iterator()
            while (it.hasNext()) {
                val ai = it.next()
                if (ai.userId == 0 && ai.packageName == pkg) {
                    it.remove()
                    changed = true
                }
            }
            if (changed) {
                filteredAppList.removeAll { it.userId == 0 && it.packageName == pkg }

                val keepSelection = sharedPreferences.getBoolean(KEY_REMEMBER_DISABLED_APPS, true)

                if (!isPackageInstalledIncludingDisabled(pkg)) {
                    activeFirewallPackages.remove(pkg)
                    saveActivePackages(activeFirewallPackages)
                }

                if (!keepSelection) {
                    val currentSelected = sharedPreferences.getStringSet(KEY_SELECTED_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
                    if (currentSelected.remove(pkg)) {
                        sharedPreferences.edit().apply {
                            putStringSet(KEY_SELECTED_APPS, currentSelected)
                            putInt(KEY_SELECTED_COUNT, currentSelected.size)
                            apply()
                        }
                    }
                }

                updateSelectedCount()
                sortAndFilterApps(preserveScrollPosition = false)
            }
        }
    }

    private fun isPackageInstalledIncludingDisabled(pkg: String): Boolean = try {
        packageManager.getApplicationInfo(
            pkg,
            PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
        )
        true
    } catch (e: Exception) {
        false
    }

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
                    AppInfo(appName, pkg, isSelected, isSystemApp, isFavorite, installTime, uid = ai.uid)
                } catch (e: Exception) {
                    null
                }
            }
            maybeApp?.let { appInfo ->
                if (ShizukuPackageResolver.isShizukuPackage(this@MainActivity, appInfo.packageName)) return@let

                if (appList.any { it.key == appInfo.key }) return@let
                appList.add(appInfo)
                sortAndFilterApps(preserveScrollPosition = false)
                updateSelectedCount()
            }
        }
    }

    private fun updateCategoryChips() {
        val categoryGroup = findViewById<ChipGroup?>(R.id.categoryChipGroup) ?: return
        val chipSystem = findViewById<Chip?>(R.id.chip_system)
        val chipUser = findViewById<Chip?>(R.id.chip_user)
        val chipSelected = findViewById<Chip?>(R.id.chip_selected)

        chipSystem?.visibility = if (showSystemApps) View.VISIBLE else View.GONE
        chipUser?.visibility = if (showSystemApps) View.VISIBLE else View.GONE

        chipSelected?.visibility = if (moveSelectedTop) View.GONE else View.VISIBLE

        syncProfileChips(categoryGroup)

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

    private fun syncProfileChips(categoryGroup: ChipGroup) {
        val userIds = if (showOtherProfiles) {
            appList.asSequence()
                .map { it.userId }
                .filter { it != 0 }
                .distinct()
                .sorted()
                .toList()
        } else {
            emptyList()
        }

        if (userIds == profileChipIds.keys.toList()) {
            profileChipIds.forEach { (userId, chipId) ->
                categoryGroup.findViewById<Chip?>(chipId)?.text =
                    MultiUserApps.userLabel(this, userId)
            }
            return
        }

        val checkedUserId = profileChipIds.entries
            .firstOrNull { it.value == categoryGroup.checkedChipId }?.key

        profileChipIds.values.forEach { chipId ->
            categoryGroup.findViewById<Chip?>(chipId)?.let { categoryGroup.removeView(it) }
        }
        profileChipIds.clear()

        userIds.forEach { userId ->
            val chip = Chip(categoryGroup.context).apply {
                id = View.generateViewId()
                isCheckable = true
                text = MultiUserApps.userLabel(this@MainActivity, userId)
            }
            categoryGroup.addView(chip)
            profileChipIds[userId] = chip.id
        }

        val restoreId = checkedUserId?.let { profileChipIds[it] }
        if (restoreId != null) {
            categoryGroup.check(restoreId)
        } else if (currentCategory == Category.PROFILE) {
            categoryGroup.clearCheck()
            currentCategory = Category.NONE
            currentProfileUserId = -1
            sortAndFilterApps(preserveScrollPosition = false)
        }
    }

    private fun showOperationErrorsDialog(failedPackages: List<String>, errorDetails: Map<String, String> = emptyMap()) {
        val failedApps = appList.filter { it.key in failedPackages }
        if (failedApps.isEmpty()) return

        if (sharedPreferences.getBoolean(KEY_SKIP_ERROR_DIALOG, false)) {
            Toast.makeText(this, getString(R.string.operation_failed_for_apps, failedApps.size), Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_firewall_confirm, null)
        val builder = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton(getString(R.string.ok), null)

        val hasDetails = failedPackages.any { errorDetails[it]?.isNotEmpty() == true } ||
            errorDetails.containsKey("_chain3") ||
            errorDetails.containsKey("_daemon_log")
        if (hasDetails) {
            builder.setNeutralButton(getString(R.string.details)) { _, _ ->
                showErrorDetailsDialog(failedPackages, errorDetails)
            }
        }
        val dialog = builder.create()

        val dialogMessage = dialogView.findViewById<TextView>(R.id.dialogMessage)
        val selectedAppsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.selectedAppsRecyclerView)

        dialogMessage.text = getString(R.string.operation_failed_message, failedApps.size)

        val chain3Msg = errorDetails["_chain3"] ?: errorDetails.values.firstOrNull { it.contains("no command found set chain 3", ignoreCase = true) }
        if (chain3Msg != null) {
            dialogMessage.append("\n\n${getString(R.string.android11_unsupported_hint)}")
        }

        if (!errorDetails["_daemon_log"].isNullOrBlank()) {
            dialogMessage.append("\n\n${getString(R.string.ladb_daemon_log_detected_hint)}")
        }

        selectedAppsRecyclerView.layoutManager = LinearLayoutManager(this)
        selectedAppsRecyclerView.adapter = SelectedAppsAdapter(failedApps)

        sizeAppList(selectedAppsRecyclerView, failedApps.size)

        dialog.show()
    }

    private suspend fun maybeShowLadbDaemonRecoveryDialog(
        failedPackages: List<String>,
        errorDetails: MutableMap<String, String>
    ): Boolean {
        if (sharedPreferences.getString(KEY_WORKING_MODE, null) != WorkingMode.LADB.name) return false
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
        val failedApps = appList.filter { it.key in failedPackages }
        if (failedApps.isEmpty()) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_error_details, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val messageView = dialogView.findViewById<TextView>(R.id.dialogErrorDetailsMessage)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.errorDetailsRecyclerView)
        val btnCopy = dialogView.findViewById<android.widget.Button>(R.id.btnCopy)
        val btnClose = dialogView.findViewById<android.widget.Button>(R.id.btnClose)

        messageView.text = getString(R.string.operation_failed_message, failedApps.size)

        val list = failedApps.map { ai ->
            val err = errorDetails[ai.key]
                ?: errorDetails["_chain3"]
                ?: errorDetails["_daemon_log"]
                ?: getString(R.string.no_error_details)
            val name = if (ai.userId != 0) {
                "${ai.appName} \u00b7 ${MultiUserApps.userLabel(this, ai.userId)}"
            } else {
                ai.appName
            }
            ErrorEntry(name, ai.packageName, err, ai.userId)
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

        dialog.show()
    }

    private var profilesBottomSheet: ProfilesBottomSheet? = null

    private fun updateProfileButtonIcon() {
        val button = findViewById<com.google.android.material.button.MaterialButton>(R.id.profileButton) ?: return
        val active = com.arslan.shizuwall.profiles.ProfilesStore.activeProfile(this)
        button.setIconResource(
            if (active == null) R.drawable.ic_profiles_24px
            else com.arslan.shizuwall.profiles.ProfileIcons.resFor(active.icon)
        )
    }

    private fun showProfilesSheet() {
        val sheet = ProfilesBottomSheet(this, object : ProfilesBottomSheet.Listener {
            override fun onActivateProfile(profile: com.arslan.shizuwall.model.Profile) {
                activateProfile(profile)
            }

            override fun onProfilesChanged() {
                updateProfileButtonIcon()
            }
        })
        profilesBottomSheet = sheet
        sheet.show()
    }

    private fun activateProfile(profile: com.arslan.shizuwall.model.Profile) {
        val oldActive = activeFirewallPackages.toList()

        com.arslan.shizuwall.profiles.ProfilesStore.writeSelectionFromProfile(this, profile)
        updateProfileButtonIcon()

        firewallMode = FirewallMode.fromName(profile.firewallMode)
        showSystemApps = profile.showSystemApps

        val appModes = try { JSONObject(profile.appModesJson) } catch (e: Exception) { JSONObject() }
        for (i in appList.indices) {
            val info = appList[i]
            val selected = profile.packages.contains(info.key)
            val mode = appModes.optInt(info.key, 0)
            if (info.isSelected != selected || info.appFirewallMode != mode) {
                appList[i] = info.copy(isSelected = selected, appFirewallMode = mode)
            }
        }

        appListAdapter.setHybridModeEnabled(firewallMode == FirewallMode.HYBRID)
        updateSelectedCount()
        updateCategoryChips()

        if (!isFirewallEnabled) {
            profilesBottomSheet?.notifyActivated(profile.id)
            if (sharedPreferences.getBoolean(KEY_AUTO_ENABLE_ON_PROFILE_ACTIVATE, false) &&
                !isFirewallProcessRunning
            ) {
                profilesBottomSheet?.dismiss()
                profileEnableRequested = true
                firewallToggle.isChecked = true
            }
            sortAndFilterApps(preserveScrollPosition = false, scrollToTop = true, smoothTransition = true)
            return
        }

        reapplyForProfileSwitch(profile, oldActive, FirewallTargets.parseAppModes(profile.appModesJson))
        sortAndFilterApps(preserveScrollPosition = false, scrollToTop = true)
    }

    private fun reapplyForProfileSwitch(
        profile: com.arslan.shizuwall.model.Profile,
        oldActive: List<String>,
        appModes: Map<String, Int>
    ) {
        val selectedPkgs = profile.packages.toList()
        val target = getTargetPackagesToBlock(selectedPkgs)
        val whitelistAllow = getWhitelistAllowPackages(selectedPkgs)

        isFirewallProcessRunning = true
        isEnablingProcess = true
        lifecycleScope.launch {
            firewallProgressFader.set(true)
            applyListInteractionState()
            try {
                val (installedTarget, _) = withContext(Dispatchers.IO) { filterInstalledPackages(target) }

                val effectiveTarget = FirewallTargets.effectiveBlockList(
                    firewallMode,
                    installedTarget,
                    ScreenLockModeReceiver.isDeviceLocked(this@MainActivity),
                    appModes
                )

                val toUnblock = oldActive.filterNot { effectiveTarget.contains(it) }

                val successful = withContext(Dispatchers.IO) {
                    val (ok, _) = enableFirewall(effectiveTarget, whitelistAllow, toUnblock)
                    ok
                }

                activeFirewallPackages.clear()
                activeFirewallPackages.addAll(successful)
                saveActivePackages(activeFirewallPackages)
                saveFirewallEnabled(true)

                profilesBottomSheet?.notifyActivated(profile.id)
            } catch (t: Throwable) {

            } finally {
                firewallProgressFader.set(false)
                isFirewallProcessRunning = false
                applyListInteractionState()
                sortAndFilterApps(preserveScrollPosition = false, scrollToTop = true)
            }
        }
    }

    private fun getTargetPackagesToBlock(selectedPkgs: List<String>): List<String> {
        val mode = FirewallMode.fromName(sharedPreferences.getString(KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name))
        return if (mode == FirewallMode.WHITELIST) {
            com.arslan.shizuwall.utils.WhitelistFilter.compute(this, selectedPkgs, showSystemApps).toBlock
        } else {
            selectedPkgs
        }
    }

    private fun getWhitelistAllowPackages(selectedPkgs: List<String>): List<String> {
        val mode = FirewallMode.fromName(sharedPreferences.getString(KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name))
        return if (mode == FirewallMode.WHITELIST) {
            com.arslan.shizuwall.utils.WhitelistFilter.compute(this, selectedPkgs, showSystemApps).toAllow
        } else {
            emptyList()
        }
    }
}
