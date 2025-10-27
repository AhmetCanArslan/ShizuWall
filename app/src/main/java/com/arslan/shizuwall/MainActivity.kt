package com.arslan.shizuwall

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import android.view.View
import android.view.ViewGroup

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
        private const val KEY_FIREWALL_ENABLED = "firewall_enabled"
        private const val KEY_ACTIVE_PACKAGES = "active_packages"
        private const val KEY_FIREWALL_SAVED_ELAPSED = "firewall_saved_elapsed"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1002
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
        
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
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

        loadInstalledApps()
        updateSelectedCount()
        
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
            // Restart notification service if firewall was enabled
            FirewallNotificationService.startService(this, activeFirewallPackages.size)
        } else {
            hideDimOverlay()
        }

        // Check permission on startup
        checkShizukuPermission()

        // Check notification permission on startup
        checkNotificationPermission()
    }
    
    override fun onResume() {
        super.onResume()
        // Re-sync toggle with saved state without triggering listener
        suppressToggleListener = true
        firewallToggle.isChecked = loadFirewallEnabled()
        suppressToggleListener = false

        // Reflect current firewall state in UI
        val enabled = loadFirewallEnabled()
        appListAdapter.setSelectionEnabled(!enabled)
        if (enabled) showDimOverlay() else hideDimOverlay()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        // Don't stop the service here - it should persist even when MainActivity is destroyed
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
        if (query.isEmpty()) {
            filteredAppList.addAll(appList)
        } else {
            val searchQuery = query.lowercase()
            filteredAppList.addAll(appList.filter { 
                it.appName.lowercase().contains(searchQuery) || 
                it.packageName.lowercase().contains(searchQuery)
            })
        }
        // Removed submitList from here; handled in callers
    }
    
    private fun sortAndFilterApps() {
        val turkishCollator = java.text.Collator.getInstance(java.util.Locale.forLanguageTag("tr-TR"))
        appList.sortWith(compareByDescending<AppInfo> { it.isSelected }.thenBy(turkishCollator) { it.appName })
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
                    if ((packageInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                        val appName = pm.getApplicationLabel(packageInfo).toString()
                        val packageName = packageInfo.packageName
                        val drawable = pm.getApplicationIcon(packageInfo)
                        val bitmap = try {
                            drawableToBitmap(drawable)
                        } catch (e: Exception) {
                            null
                        }
                        val isSelected = selectedPackages.contains(packageName)
                        temp.add(AppInfo(appName, packageName, bitmap, isSelected))
                    }
                }
                val turkishCollator = java.text.Collator.getInstance(java.util.Locale.forLanguageTag("tr-TR"))
                temp.sortWith(compareByDescending<AppInfo> { it.isSelected }.thenBy(turkishCollator) { it.appName })
                temp
            }

            appList.clear()
            appList.addAll(builtList)
            filteredAppList.clear()
            filteredAppList.addAll(appList)

            // Disable animator for initial load to prevent any scrolling
            val animator = recyclerView.itemAnimator
            recyclerView.itemAnimator = null
            appListAdapter.submitList(filteredAppList.toList()) { recyclerView.itemAnimator = animator }
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
            .apply()
    }
    
    private fun loadSelectedApps(): Set<String> {
        return sharedPreferences.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
    }
    
    private fun saveFirewallEnabled(enabled: Boolean) {
        // store a boot-relative timestamp when enabling so we can detect reboots
        val editor = sharedPreferences.edit()
        editor.putBoolean(KEY_FIREWALL_ENABLED, enabled)
        if (enabled) {
            editor.putLong(KEY_FIREWALL_SAVED_ELAPSED, SystemClock.elapsedRealtime())
        } else {
            editor.remove(KEY_FIREWALL_SAVED_ELAPSED)
        }
        editor.apply()
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
            val success = withContext(Dispatchers.IO) {
                if (enable) enableFirewall(packageNames) else disableFirewall(packageNames)
            }
            firewallToggle.isEnabled = true
            if (success) {
                isFirewallEnabled = enable
                saveFirewallEnabled(enable)

                if (enable) {
                    activeFirewallPackages.clear()
                    activeFirewallPackages.addAll(packageNames)
                    saveActivePackages(activeFirewallPackages)
                    appListAdapter.setSelectionEnabled(false)
                    showDimOverlay()
                    // Start notification service
                    FirewallNotificationService.startService(this@MainActivity, packageNames.size)
                    // Send enable notification
                    FirewallNotificationService.sendFirewallEnabledNotification(this@MainActivity, packageNames.size)
                    Toast.makeText(this@MainActivity, "Firewall activated for ${packageNames.size} apps", Toast.LENGTH_SHORT).show()
                } else {
                    appListAdapter.setSelectionEnabled(true)
                    hideDimOverlay()
                    // Stop notification service
                    FirewallNotificationService.stopService(this@MainActivity)
                    // Cancel enable notification
                    FirewallNotificationService.cancelFirewallEnabledNotification(this@MainActivity)
                    Toast.makeText(this@MainActivity, "Firewall disabled", Toast.LENGTH_SHORT).show()
                    activeFirewallPackages.clear()
                    saveActivePackages(activeFirewallPackages)
                }
            } else {
                suppressToggleListener = true
                firewallToggle.isChecked = !enable
                suppressToggleListener = false
                Toast.makeText(this@MainActivity, "Failed to apply firewall changes", Toast.LENGTH_SHORT).show()
            }
        }
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
}