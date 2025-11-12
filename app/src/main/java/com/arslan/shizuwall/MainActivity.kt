package com.arslan.shizuwall

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.content.ActivityNotFoundException
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var appListAdapter: AppListAdapter
    private lateinit var firewallToggle: SwitchMaterial
    private lateinit var searchView: SearchView
    private lateinit var selectedCountText: TextView
    private lateinit var selectAllCheckbox: CheckBox
    private val appList = mutableListOf<AppInfo>()
    private val filteredAppList = mutableListOf<AppInfo>()
    private var isFirewallEnabled = false
    private var currentQuery = ""
    private var showSystemApps = false // NEW: whether to include system apps in the list
    private var moveSelectedTop = true // whether selected apps move to top

    private lateinit var sharedPreferences: SharedPreferences

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        onRequestPermissionsResult(requestCode, grantResult)
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkShizukuPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Toast.makeText(this, "Shizuku service is dead", Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val PREF_NAME = "ShizuWallPrefs"
        private const val KEY_SELECTED_APPS = "selected_apps"
        private const val KEY_SELECTED_COUNT = "selected_count"
        const val KEY_FIREWALL_ENABLED = "firewall_enabled"          // made public
        private const val KEY_ACTIVE_PACKAGES = "active_packages"
        const val KEY_FIREWALL_SAVED_ELAPSED = "firewall_saved_elapsed" // made public
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1002
        private const val KEY_SKIP_ENABLE_CONFIRM = "skip_enable_confirm" 
        const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
        const val KEY_MOVE_SELECTED_TOP = "move_selected_top"
        const val KEY_SELECTED_FONT = "selected_font"
        const val KEY_USE_DYNAMIC_COLOR = "use_dynamic_color"

        private val SHIZUKU_NEW_PROCESS_METHOD by lazy {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
        }
    }

    private var suppressToggleListener = false
    private var suppressSelectAllListener = false
    private val activeFirewallPackages = mutableSetOf<String>()
    private enum class Category { DEFAULT, SYSTEM, SELECTED, UNSELECTED, USER }
    private var currentCategory: Category = Category.DEFAULT
    
    // Track if we're waiting for Shizuku permission due to toggle attempt
    private var pendingToggleEnable = false
    private var pendingToggleDisable = false

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
            loadInstalledApps()
            updateCategoryChips()
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

        applyFontToViews(findViewById(android.R.id.content))

        // GitHub icon
        val openGithub = {
            val url = getString(R.string.github_url)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        val githubIcon: ImageView = findViewById(R.id.githubIcon)
        githubIcon.setOnClickListener { openGithub() }

        val settingsButton: FloatingActionButton? = findViewById(R.id.settingsButton)
        settingsButton?.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            settingsLauncher.launch(intent)
        }

        showSystemApps = sharedPreferences.getBoolean(KEY_SHOW_SYSTEM_APPS, false)
        moveSelectedTop = sharedPreferences.getBoolean(KEY_MOVE_SELECTED_TOP, true)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        setupFirewallToggle()
        setupSearchView()
        setupSelectAllCheckbox()
        setupRecyclerView()

        // wire category bar AFTER views are created
        val categoryGroup = findViewById<ChipGroup>(R.id.categoryChipGroup)
        categoryGroup.setOnCheckedChangeListener { _, checkedId ->
            currentCategory = when (checkedId) {
                R.id.chip_default -> Category.DEFAULT
                R.id.chip_system -> Category.SYSTEM
                R.id.chip_selected -> Category.SELECTED
                R.id.chip_unselected -> Category.UNSELECTED
                R.id.chip_user -> Category.USER
                else -> Category.DEFAULT
            }
            sortAndFilterApps()
        }

        // ensure the category chips reflect the saved "show system apps" preference
        updateCategoryChips()
       
        loadInstalledApps()

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
        appListAdapter.setSelectionEnabled(!enabled)
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
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        // Background service removed, nothing to stop here.
    }

    private fun checkShizukuAvailable(): Boolean {
        try {
            if (Shizuku.pingBinder()) {
                return true
            }
        } catch (e: Exception) {
            // Shizuku is not available
        }

        AlertDialog.Builder(this)
            .setTitle("Shizuku Required")
            .setMessage("This app requires Shizuku to be installed and running. Please install Shizuku and start the service.")
            .setPositiveButton("OK", null) // just dismiss dialog, do not close app
            .setCancelable(true)
            .show()

        return false
    }

    private fun checkShizukuPermission() {
        if (Shizuku.isPreV11()) {
            AlertDialog.Builder(this)
                .setTitle("Shizuku Update Required")
                .setMessage("Your Shizuku version is too old. Please update Shizuku to the latest version.")
                .setPositiveButton("OK", null) // do not close app, just dismiss
                .setCancelable(true)
                .show()
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            // Permission already granted
            return
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            // User denied permission permanently
            AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Shizuku permission is required for this app to work. Please grant the permission in Shizuku settings.")
                .setPositiveButton("OK", null) // do not close app
                .setCancelable(true)
                .show()
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
                    Toast.makeText(this, "Shizuku permission granted", Toast.LENGTH_SHORT).show()
                    // If permission was requested due to toggle attempt, resume the enable flow
                    if (pendingToggleEnable) {
                        pendingToggleEnable = false
                        val selectedApps = appList.filter { it.isSelected }
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
                        // Proceed with disabling the firewall
                        applyFirewallState(false, activeFirewallPackages.toList())
                    }
                } else {
                    // Permission denied, revert toggle to its previous state
                    pendingToggleEnable = false
                    pendingToggleDisable = false
                    suppressToggleListener = true
                    // If we were trying to enable, revert to off; if trying to disable, revert to on
                    firewallToggle.isChecked = isFirewallEnabled
                    suppressToggleListener = false
                    AlertDialog.Builder(this)
                        .setTitle("Permission Denied")
                        .setMessage("Shizuku permission is required for this app to work. Please grant the permission in Shizuku settings.")
                        .setPositiveButton("OK", null) // just dismiss dialog
                        .setCancelable(true)
                        .show()
                }
            }
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (granted) {
                    Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Notification permission denied. You won't see firewall status notifications.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun checkPermission(code: Int): Boolean {
        // First ensure Shizuku binder is reachable. If it's not running, show a friendly dialog prompting the user to start/install Shizuku.
        try {
            if (!Shizuku.pingBinder()) {
                AlertDialog.Builder(this)
                    .setTitle("Shizuku not running")
                    .setMessage("Shizuku is not currently running. Start the Shizuku service (or install it) before enabling the firewall.")
                    .setPositiveButton("Open Shizuku") { _, _ ->
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
                    .setNegativeButton("Cancel", null)
                    .show()
                return false
            }
        } catch (e: Exception) {
            // If ping fails unexpectedly, fall back to permission flow below.
        }

        if (Shizuku.isPreV11()) {
            // Pre-v11 is unsupported
            Toast.makeText(this, "Shizuku version is too old, please update", Toast.LENGTH_SHORT).show()
            return false
        }

        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            // Granted
            true
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            // Users chose "Deny and don't ask again"
            Toast.makeText(this, "Please grant Shizuku permission in settings", Toast.LENGTH_LONG).show()
            false
        } else {
            // Request the permission (this will show the Shizuku permission dialog)
            Shizuku.requestPermission(code)
            false
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun setupSearchView() {
        searchView = findViewById(R.id.searchView)
        searchView.queryHint = "Search app"
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentQuery = newText ?: ""
                filterApps(currentQuery)

                // Capture scroll position and disable animator to prevent scrolling during search updates
                val firstVisible = (recyclerView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                val animator = recyclerView.itemAnimator
                recyclerView.itemAnimator = null
                appListAdapter.submitList(filteredAppList.toList()) { 
                    recyclerView.itemAnimator = animator
                    recyclerView.scrollToPosition(firstVisible)
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
            
            // Select/deselect all apps in the current filtered list
            val changed = filteredAppList.any { it.isSelected != isChecked }
            if (changed) {
                for (app in filteredAppList) {
                    app.isSelected = isChecked
                }
                updateSelectedCount()
                saveSelectedApps()
                sortAndFilterApps()
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        appListAdapter = AppListAdapter(
            onAppClick = { appInfo ->
                updateSelectedCount()
                saveSelectedApps()
                sortAndFilterApps()
            },
            typeface = getSelectedTypeface()
        )
        recyclerView.adapter = appListAdapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun filterApps(query: String) {
        filteredAppList.clear()

        // Apply category filter first
        val baseList: List<AppInfo> = when (currentCategory) {
            Category.DEFAULT -> if (showSystemApps) appList else appList.filter { !it.isSystem }
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

    private fun sortAndFilterApps() {
        val turkishCollator = java.text.Collator.getInstance(java.util.Locale.forLanguageTag("tr-TR"))
        if (moveSelectedTop) {
            appList.sortWith(
                compareByDescending<AppInfo> { it.isSelected }
                    .thenBy { it.isSystem } // false (user apps) before true (system apps)
                    .thenBy(turkishCollator) { it.appName }
            )
        } else {
            // Do not prioritize selected apps; sort by user/system then name
            appList.sortWith(
                compareBy<AppInfo> { it.isSystem } // false (user apps) before true (system apps)
                    .thenBy(turkishCollator) { it.appName }
            )
        }

        filterApps(currentQuery)

        // Capture scroll position and disable animator to prevent scrolling during selection updates
        val firstVisible = (recyclerView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        val animator = recyclerView.itemAnimator
        recyclerView.itemAnimator = null
        appListAdapter.submitList(filteredAppList.toList()) {
            recyclerView.itemAnimator = animator
            recyclerView.scrollToPosition(firstVisible)
            updateSelectedCount()
            updateSelectAllCheckbox()
        }
    }

    private fun setupFirewallToggle() {
        firewallToggle = findViewById(R.id.firewallToggle)
        selectedCountText = findViewById(R.id.selectedCountText)

        firewallToggle.setOnCheckedChangeListener { _, isChecked ->
            if (suppressToggleListener) return@setOnCheckedChangeListener
            if (isChecked) {
                val selectedApps = appList.filter { it.isSelected }
                if (selectedApps.isEmpty()) {
                    Toast.makeText(this, "Please select at least one app", Toast.LENGTH_SHORT).show()
                    suppressToggleListener = true
                    firewallToggle.isChecked = false
                    suppressToggleListener = false
                    return@setOnCheckedChangeListener
                }
                if (!checkPermission(SHIZUKU_PERMISSION_REQUEST_CODE)) {
                    // Permission not granted, mark that we're waiting for it
                    pendingToggleEnable = true
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
                if (!checkPermission(SHIZUKU_PERMISSION_REQUEST_CODE)) {
                    // Permission not granted, mark that we're waiting for it
                    pendingToggleDisable = true
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
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val dialogMessage = dialogView.findViewById<TextView>(R.id.dialogMessage)
        val selectedAppsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.selectedAppsRecyclerView)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btnConfirm)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)

        dialogMessage.text = "Do you want to enable firewall for ${selectedApps.size} apps listed below?"

        selectedAppsRecyclerView.layoutManager = LinearLayoutManager(this)
        selectedAppsRecyclerView.adapter = SelectedAppsAdapter(selectedApps, getSelectedTypeface())

        // Limit the RecyclerView height to a fraction of the screen
        val displayHeight = resources.displayMetrics.heightPixels
        val maxRecyclerHeight = (displayHeight * 0.5).toInt() // 50% of screen height
        selectedAppsRecyclerView.isNestedScrollingEnabled = true
        selectedAppsRecyclerView.post {
            val lp = selectedAppsRecyclerView.layoutParams
            // If layout params are wrap_content or match_parent, enforce the max height; otherwise cap the current height.
            lp.height = when (lp.height) {
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT -> maxRecyclerHeight
                else -> minOf(lp.height, maxRecyclerHeight)
            }
            selectedAppsRecyclerView.layoutParams = lp
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            applyFirewallState(true, selectedApps.map { it.packageName })
        }

        btnCancel.setOnClickListener {
            suppressToggleListener = true
            firewallToggle.isChecked = false
            suppressToggleListener = false
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateSelectedCount() {
        val count = appList.count { it.isSelected }
        selectedCountText.text = count.toString()

        // enable the firewall toggle if firewall is currently active (so user can disable),
        // or if there is at least one selected app (so user can enable).
        if (::firewallToggle.isInitialized) {
            firewallToggle.isEnabled = isFirewallEnabled || count > 0
        }
        
        updateSelectAllCheckbox()
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
            val builtList = withContext(Dispatchers.IO) {
                val pm = packageManager
                val packages = pm.getInstalledApplications(0)
                val selectedPackages = loadSelectedApps()
                val temp = mutableListOf<AppInfo>()
                for (packageInfo in packages) {
                    val isSystemApp = (packageInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    // skip apps that are disabled (treated as "offline" — don't show them even if system apps are enabled)
                    if (!packageInfo.enabled) continue

                    // include if user requested system apps, or it's a user-installed app
                    if (!showSystemApps && isSystemApp) continue

                    val packageName = packageInfo.packageName

                    // skip apps that do not have internet permission (offline apps)
                    val hasInternetPermission = pm.checkPermission(
                        Manifest.permission.INTERNET,
                        packageName
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasInternetPermission) continue

                    val appName = pm.getApplicationLabel(packageInfo).toString()
                    val drawable = pm.getApplicationIcon(packageInfo)
                    val bitmap = try {
                        drawableToBitmap(drawable)
                    } catch (e: Exception) {
                        null
                    }
                    val isSelected = selectedPackages.contains(packageName)
                    temp.add(AppInfo(appName, packageName, bitmap, isSelected, isSystemApp))
                }
                temp
            }

            appList.clear()
            appList.addAll(builtList)
            // Use sortAndFilterApps so the current category + search are applied and animator handling is reused
            sortAndFilterApps()
        }
    }

    // convert Drawable to Bitmap (used once per app)
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            drawable.bitmap?.let { return it }
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun saveSelectedApps() {
        val selectedPackages = appList.filter { it.isSelected }.map { it.packageName }.toSet()
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
                    apply()
                }
            } catch (e: Exception) {
                // ignore device-protected write failures
            }
        }
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
        sharedPreferences.edit()
            .putStringSet(KEY_ACTIVE_PACKAGES, packages)
            .apply()
    }

    private fun loadActivePackages(): Set<String> {
        return sharedPreferences.getStringSet(KEY_ACTIVE_PACKAGES, emptySet()) ?: emptySet()
    }

    private fun applyFirewallState(enable: Boolean, packageNames: List<String>) {
        if (enable && packageNames.isEmpty()) return
        firewallToggle.isEnabled = false
        lifecycleScope.launch {
            // perform package existence checks and run enable/disable on IO thread
            val (installed, missing) = withContext(Dispatchers.IO) {
                filterInstalledPackages(packageNames)
            }

            // If enabling and none of the chosen packages remain installed -> abort
            if (enable && installed.isEmpty()) {
                Toast.makeText(this@MainActivity, "None of the selected apps are installed. Aborting.", Toast.LENGTH_SHORT).show()
                suppressToggleListener = true
                firewallToggle.isChecked = false
                suppressToggleListener = false
                firewallToggle.isEnabled = true
                return@launch
            }

            // Inform about ignored (missing) packages when appropriate
            if (missing.isNotEmpty()) {
                Toast.makeText(this@MainActivity, "${missing.size} selected apps are not installed and were ignored.", Toast.LENGTH_SHORT).show()
            }

            val (successful, failed) = withContext(Dispatchers.IO) {
                if (enable) {
                    enableFirewall(installed)
                } else {
                    disableFirewall(installed)
                }
            }

            firewallToggle.isEnabled = true

            // Handle successes
            if (enable) {
                if (successful.isNotEmpty()) {
                    isFirewallEnabled = true
                    activeFirewallPackages.clear()
                    activeFirewallPackages.addAll(successful)
                    saveActivePackages(activeFirewallPackages)
                    saveFirewallEnabled(true)
                    // Ensure toggle stays ON
                    suppressToggleListener = true
                    firewallToggle.isChecked = true
                    suppressToggleListener = false
                    appListAdapter.setSelectionEnabled(false)
                    showDimOverlay()
                    Toast.makeText(this@MainActivity, "Firewall activated for ${successful.size} apps", Toast.LENGTH_SHORT).show()
                } else {
                    // None succeeded, revert toggle
                    suppressToggleListener = true
                    firewallToggle.isChecked = false
                    suppressToggleListener = false
                    Toast.makeText(this@MainActivity, "Failed to enable firewall for any apps", Toast.LENGTH_SHORT).show()
                }
            } else {
                if (successful.isNotEmpty()) {
                    activeFirewallPackages.removeAll(successful)
                    saveActivePackages(activeFirewallPackages)
                    if (activeFirewallPackages.isEmpty()) {
                        isFirewallEnabled = false
                        saveFirewallEnabled(false)
                        // Ensure toggle stays OFF
                        suppressToggleListener = true
                        firewallToggle.isChecked = false
                        suppressToggleListener = false
                        appListAdapter.setSelectionEnabled(true)
                        hideDimOverlay()
                    }
                    Toast.makeText(this@MainActivity, "Firewall disabled for ${successful.size} apps", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to disable firewall for any apps", Toast.LENGTH_SHORT).show()
                }
            }

            // Handle failures: unselect failed apps and show error dialog
            if (failed.isNotEmpty()) {
                for (pkg in failed) {
                    val idx = appList.indexOfFirst { it.packageName == pkg }
                    if (idx != -1) {
                        val ai = appList[idx]
                        try {
                            appList[idx] = ai.copy(isSelected = false)
                        } catch (e: Exception) {
                            ai.isSelected = false
                            appListAdapter.notifyDataSetChanged()
                        }
                    }
                }
                updateSelectedCount()
                saveSelectedApps()
                sortAndFilterApps()
                showOperationErrorsDialog(failed)
            }
        }
    }

    private fun filterInstalledPackages(packageNames: List<String>): Pair<List<String>, List<String>> {
        val installed = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val pm = packageManager
        for (pkg in packageNames) {
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

    private fun enableFirewall(packageNames: List<String>): Pair<List<String>, List<String>> {
        val successful = mutableListOf<String>()
        val failed = mutableListOf<String>()
        if (!runShizukuShellCommand("cmd connectivity set-chain3-enabled true")) {
            // If chain3 enable fails, all packages fail
            failed.addAll(packageNames)
            return Pair(successful, failed)
        }
        for (packageName in packageNames) {
            if (runShizukuShellCommand("cmd connectivity set-package-networking-enabled false $packageName")) {
                successful.add(packageName)
            } else {
                failed.add(packageName)
            }
        }
        // No rollback; allow partial success
        return Pair(successful, failed)
    }

    private fun disableFirewall(packageNames: List<String>): Pair<List<String>, List<String>> {
        val successful = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for (packageName in packageNames) {
            if (runShizukuShellCommand("cmd connectivity set-package-networking-enabled true $packageName")) {
                successful.add(packageName)
            } else {
                failed.add(packageName)
            }
        }
        // Disable chain3 regardless of individual results
        runShizukuShellCommand("cmd connectivity set-chain3-enabled false")
        return Pair(successful, failed)
    }

    private fun runShizukuShellCommand(command: String): Boolean {
        return try {
            val process = SHIZUKU_NEW_PROCESS_METHOD.invoke(
                null,
                arrayOf("/system/bin/sh", "-c", command),
                null,
                null
            ) as? ShizukuRemoteProcess ?: return false

            process.outputStream.close()
            process.inputStream.close()
            process.errorStream.close()
            val exitCode = process.waitFor()
            process.destroy()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    // dim only the RecyclerView and disable its interactions
    private fun showDimOverlay() {
        // visually dim RecyclerView and block interactions
        recyclerView.alpha = 0.5f
        recyclerView.isEnabled = false
        recyclerView.isClickable = false
        appListAdapter.setSelectionEnabled(false)
    }

    private fun hideDimOverlay() {
        recyclerView.alpha = 1.0f
        recyclerView.isEnabled = true
        recyclerView.isClickable = true
        appListAdapter.setSelectionEnabled(true)
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
                sortAndFilterApps()
            }
        }
    }

    // Called by packageBroadcastReceiver when a package is added/updated.
    private fun handlePackageAdded(pkg: String) {
        // Load the single package on IO and add it to the list if it matches current filters.
        lifecycleScope.launch {
            val maybeApp = withContext(Dispatchers.IO) {
                try {
                    val pm = packageManager
                    val ai = pm.getApplicationInfo(pkg, 0)
                    if (!ai.enabled) return@withContext null
                    val isSystemApp = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (!showSystemApps && isSystemApp) return@withContext null
                    val hasInternet = pm.checkPermission(Manifest.permission.INTERNET, pkg) == PackageManager.PERMISSION_GRANTED
                    if (!hasInternet) return@withContext null
                    val appName = pm.getApplicationLabel(ai).toString()
                    val drawable = pm.getApplicationIcon(ai)
                    val bitmap = try { drawableToBitmap(drawable) } catch (e: Exception) { null }
                    val isSelected = loadSelectedApps().contains(pkg)
                    AppInfo(appName, pkg, bitmap, isSelected, isSystemApp)
                } catch (e: Exception) {
                    null
                }
            }

            maybeApp?.let { appInfo ->
                // Avoid duplicates (in case it was already present)
                if (appList.any { it.packageName == appInfo.packageName }) return@let
                appList.add(appInfo)
                sortAndFilterApps()
                updateSelectedCount()
            }
        }
    }

    private fun updateCategoryChips() {
        // guard: views may not be initialized in some lifecycle flows
        val categoryGroup = findViewById<ChipGroup?>(R.id.categoryChipGroup) ?: return
        val chipSystem = findViewById<Chip?>(R.id.chip_system)
        chipSystem?.visibility = if (showSystemApps) View.VISIBLE else View.GONE

        // if we hid the system chip and it was selected, fall back to default
        if (!showSystemApps && categoryGroup.checkedChipId == R.id.chip_system) {
            categoryGroup.check(R.id.chip_default)
            currentCategory = Category.DEFAULT
            sortAndFilterApps()
        }
    }

    private fun showOperationErrorsDialog(failedPackages: List<String>) {
        val failedApps = appList.filter { it.packageName in failedPackages }
        if (failedApps.isEmpty()) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_firewall_confirm, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val dialogMessage = dialogView.findViewById<TextView>(R.id.dialogMessage)
        val selectedAppsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.selectedAppsRecyclerView)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btnConfirm)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)

        dialogMessage.text = "Operation failed for the following ${failedApps.size} apps. They have been unselected."

        selectedAppsRecyclerView.layoutManager = LinearLayoutManager(this)
        selectedAppsRecyclerView.adapter = SelectedAppsAdapter(failedApps, getSelectedTypeface())

        // Limit the RecyclerView height to a fraction of the screen
        val displayHeight = resources.displayMetrics.heightPixels
        val maxRecyclerHeight = (displayHeight * 0.5).toInt() // 50% of screen height
        selectedAppsRecyclerView.isNestedScrollingEnabled = true
        selectedAppsRecyclerView.post {
            val lp = selectedAppsRecyclerView.layoutParams
            lp.height = when (lp.height) {
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT -> maxRecyclerHeight
                else -> minOf(lp.height, maxRecyclerHeight)
            }
            selectedAppsRecyclerView.layoutParams = lp
        }

        btnConfirm.text = "OK"
        btnConfirm.setOnClickListener { dialog.dismiss() }
        btnCancel.visibility = View.GONE // Hide cancel button for error dialog

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