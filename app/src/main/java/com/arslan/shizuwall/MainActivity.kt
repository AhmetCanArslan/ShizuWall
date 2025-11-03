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
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var appListAdapter: AppListAdapter
    private lateinit var firewallToggle: SwitchMaterial
    private lateinit var searchView: SearchView
    private lateinit var selectedCountText: TextView
    private val appList = mutableListOf<AppInfo>()
    private val filteredAppList = mutableListOf<AppInfo>()
    private var isFirewallEnabled = false
    private var currentQuery = ""
    private var showSystemApps = false // NEW: whether to include system apps in the list

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
        // preference key to persist "show system apps" state
        const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
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
    private val activeFirewallPackages = mutableSetOf<String>()
    private enum class Category { DEFAULT, SYSTEM, SELECTED, UNSELECTED, USER }
    private var currentCategory: Category = Category.DEFAULT

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        // GitHub icon
        val openGithub = {
            val url = getString(R.string.github_url)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        val githubIcon: ImageView = findViewById(R.id.githubIcon)
        githubIcon.setOnClickListener { openGithub() }

        // Overflow (three-dot) menu
        val overflowButton: ImageButton? = findViewById(R.id.overflowMenu)
        overflowButton?.setOnClickListener { btn ->
            val popup = PopupMenu(this, btn)
            val menuItemIdShowSystem = 1
            val menuItemIdSkipConfirm = 2
            popup.menu.add(0, menuItemIdShowSystem, 0, getString(R.string.show_system_apps)).apply {
                isCheckable = true
                isChecked = showSystemApps
            }

            // menu item to allow skipping the enable-confirm dialog
            val prefsLocal = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            popup.menu.add(0, menuItemIdSkipConfirm, 1, getString(R.string.skip_enable_dialog)).apply {
                isCheckable = true
                isChecked = prefsLocal.getBoolean(KEY_SKIP_ENABLE_CONFIRM, false)
            }

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    menuItemIdShowSystem -> {
                        showSystemApps = !showSystemApps
                        item.isChecked = showSystemApps
                        // persist the user's choice
                        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean(KEY_SHOW_SYSTEM_APPS, showSystemApps)
                            .apply()
                        loadInstalledApps() // refresh list
                        true
                    }
                    menuItemIdSkipConfirm -> {
                        val newVal = !item.isChecked
                        item.isChecked = newVal
                        // persist preference
                        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean(KEY_SKIP_ENABLE_CONFIRM, newVal)
                            .apply()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        showSystemApps = sharedPreferences.getBoolean(KEY_SHOW_SYSTEM_APPS, false)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Check if Shizuku is available
        if (!checkShizukuAvailable()) {
            return
        }

        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        setupFirewallToggle()
        setupSearchView()
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
       
        loadInstalledApps()

        // Load and display saved selected count
        val savedCount = sharedPreferences.getInt(KEY_SELECTED_COUNT, 0)
        selectedCountText.text = "Selected: $savedCount"

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

        // Check permission on startup
        checkShizukuPermission()

        // Check notification permission on startup
        checkNotificationPermission()
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
            .setPositiveButton("OK") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()

        return false
    }

    private fun checkShizukuPermission() {
        if (Shizuku.isPreV11()) {
            AlertDialog.Builder(this)
                .setTitle("Shizuku Update Required")
                .setMessage("Your Shizuku version is too old. Please update Shizuku to the latest version.")
                .setPositiveButton("OK") { _, _ ->
                    finish()
                }
                .setCancelable(false)
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
                .setPositiveButton("OK") { _, _ ->
                    finish()
                }
                .setCancelable(false)
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
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Permission Denied")
                        .setMessage("Shizuku permission is required for this app to work. The app will now close.")
                        .setPositiveButton("OK") { _, _ ->
                            finish()
                        }
                        .setCancelable(false)
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
        if (Shizuku.isPreV11()) {
            // Pre-v11 is unsupported
            Toast.makeText(this, "Shizuku version is too old, please update", Toast.LENGTH_SHORT).show()
            return false
        }

        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            // Granted
            true
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            // Users choose "Deny and don't ask again"
            Toast.makeText(this, "Please grant Shizuku permission in settings", Toast.LENGTH_LONG).show()
            false
        } else {
            // Request the permission
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
                }
                return true
            }
        })
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        appListAdapter = AppListAdapter { appInfo ->
            // when an app is clicked
            updateSelectedCount()
            saveSelectedApps()
            sortAndFilterApps()
        }
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
        // Removed submitList from here; handled in callers
    }

    private fun sortAndFilterApps() {
        val turkishCollator = java.text.Collator.getInstance(java.util.Locale.forLanguageTag("tr-TR"))
        appList.sortWith(
            compareByDescending<AppInfo> { it.isSelected }
                .thenBy { it.isSystem } // false (user apps) before true (system apps)
                .thenBy(turkishCollator) { it.appName }
        )
        filterApps(currentQuery)

        // Capture scroll position and disable animator to prevent scrolling during selection updates
        val firstVisible = (recyclerView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        val animator = recyclerView.itemAnimator
        recyclerView.itemAnimator = null
        appListAdapter.submitList(filteredAppList.toList()) {
            recyclerView.itemAnimator = animator
            recyclerView.scrollToPosition(firstVisible)
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
                    suppressToggleListener = true
                    firewallToggle.isChecked = false
                    suppressToggleListener = false
                    return@setOnCheckedChangeListener
                }
                showFirewallConfirmDialog(selectedApps)
            } else {
                if (!isFirewallEnabled) {
                    return@setOnCheckedChangeListener
                }
                if (!checkPermission(SHIZUKU_PERMISSION_REQUEST_CODE)) {
                    suppressToggleListener = true
                    firewallToggle.isChecked = true
                    suppressToggleListener = false
                    return@setOnCheckedChangeListener
                }
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
        selectedAppsRecyclerView.adapter = SelectedAppsAdapter(selectedApps)

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
        selectedCountText.text = "Selected: $count"

        // enable the firewall toggle if firewall is currently active (so user can disable),
        // or if there is at least one selected app (so user can enable).
        if (::firewallToggle.isInitialized) {
            firewallToggle.isEnabled = isFirewallEnabled || count > 0
        }
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
            val (success, installed, missing) = withContext(Dispatchers.IO) {
                val (installedList, missingList) = filterInstalledPackages(packageNames)
                if (enable) {
                    if (installedList.isEmpty()) {
                        // nothing to enable
                        return@withContext Triple(false, installedList, missingList)
                    }
                    val ok = enableFirewall(installedList)
                    return@withContext Triple(ok, installedList, missingList)
                } else {
                    // disabling: ignore missing ones but still disable chain3 framework
                    val ok = disableFirewall(installedList)
                    return@withContext Triple(ok, installedList, missingList)
                }
            }

            firewallToggle.isEnabled = true

            // If enabling and none of the chosen packages remain installed -> abort
            if (enable && installed.isEmpty()) {
                Toast.makeText(this@MainActivity, "None of the selected apps are installed. Aborting.", Toast.LENGTH_SHORT).show()
                suppressToggleListener = true
                firewallToggle.isChecked = false
                suppressToggleListener = false
                return@launch
            }

            // Inform about ignored (missing) packages when appropriate
            if (missing.isNotEmpty()) {
                Toast.makeText(this@MainActivity, "${missing.size} selected apps are not installed and were ignored.", Toast.LENGTH_SHORT).show()
            }

            if (success) {
                if (enable) {
                    isFirewallEnabled = true
                    // persist only installed ones as active
                    activeFirewallPackages.clear()
                    activeFirewallPackages.addAll(installed)
                    saveActivePackages(activeFirewallPackages)
                    saveFirewallEnabled(true)

                    appListAdapter.setSelectionEnabled(false)
                    showDimOverlay()
                    Toast.makeText(this@MainActivity, "Firewall activated for ${installed.size} apps", Toast.LENGTH_SHORT).show()
                } else {
                    activeFirewallPackages.clear()
                    saveActivePackages(activeFirewallPackages)

                    isFirewallEnabled = false
                    appListAdapter.setSelectionEnabled(true)
                    hideDimOverlay()
                    saveFirewallEnabled(false)
                    Toast.makeText(this@MainActivity, "Firewall disabled", Toast.LENGTH_SHORT).show()
                }
            } else {
                // operation failed -> revert toggle and notify
                suppressToggleListener = true
                firewallToggle.isChecked = !enable
                suppressToggleListener = false
                Toast.makeText(this@MainActivity, "Failed to apply firewall changes", Toast.LENGTH_SHORT).show()

                // If disabling failed, still clean stored active packages from missing entries
                if (!enable && missing.isNotEmpty()) {
                    activeFirewallPackages.removeAll(missing)
                    saveActivePackages(activeFirewallPackages)
                }
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

    private fun enableFirewall(packageNames: List<String>): Boolean {
        if (!runShizukuShellCommand("cmd connectivity set-chain3-enabled true")) {
            return false
        }
        for (packageName in packageNames) {
            if (!runShizukuShellCommand("cmd connectivity set-package-networking-enabled false $packageName")) {
                runShizukuShellCommand("cmd connectivity set-chain3-enabled false")
                return false
            }
        }
        return true
    }

    private fun disableFirewall(packageNames: List<String>): Boolean {
        var success = true
        for (packageName in packageNames) {
            if (!runShizukuShellCommand("cmd connectivity set-package-networking-enabled true $packageName")) {
                success = false
            }
        }
        if (!runShizukuShellCommand("cmd connectivity set-chain3-enabled false")) {
            success = false
        }
        return success
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
}