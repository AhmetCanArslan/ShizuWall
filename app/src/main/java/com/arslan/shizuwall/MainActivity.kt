package com.arslan.shizuwall

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var appListAdapter: AppListAdapter
    private lateinit var firewallToggle: SwitchMaterial
    private lateinit var searchView: SearchView
    private lateinit var selectedCountText: TextView
    private val appList = mutableListOf<AppInfo>()
    private val filteredAppList = mutableListOf<AppInfo>()
    private var isFirewallEnabled = false
    
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
        private const val PREF_NAME = "ShizuWallPrefs"
        private const val KEY_SELECTED_APPS = "selected_apps"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
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
        
        // Check permission on startup
        checkShizukuPermission()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
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
    
    private fun setupSearchView() {
        searchView = findViewById(R.id.searchView)
        searchView.queryHint = "Search app"
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterApps(newText ?: "")
                return true
            }
        })
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
        appListAdapter.notifyDataSetChanged()
    }
    
    private fun setupFirewallToggle() {
        firewallToggle = findViewById(R.id.firewallToggle)
        selectedCountText = findViewById(R.id.selectedCountText)
        
        firewallToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val selectedApps = appList.filter { it.isSelected }
                if (selectedApps.isEmpty()) {
                    Toast.makeText(this, "Please select at least one app", Toast.LENGTH_SHORT).show()
                    firewallToggle.isChecked = false
                    return@setOnCheckedChangeListener
                }
                
                // Check Shizuku permission before proceeding
                if (!checkPermission(SHIZUKU_PERMISSION_REQUEST_CODE)) {
                    firewallToggle.isChecked = false
                    return@setOnCheckedChangeListener
                }
                
                showFirewallConfirmDialog(selectedApps)
            } else {
                isFirewallEnabled = false
                Toast.makeText(this, "Firewall disabled", Toast.LENGTH_SHORT).show()
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
            isFirewallEnabled = true
            Toast.makeText(this, "Firewall activated for ${selectedApps.size} apps", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        btnCancel.setOnClickListener {
            firewallToggle.isChecked = false
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun updateSelectedCount() {
        val count = appList.count { it.isSelected }
        selectedCountText.text = "Selected: $count"
    }
    
    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        appListAdapter = AppListAdapter(filteredAppList) { appInfo ->
            // when an app is clicked
            updateSelectedCount()
            saveSelectedApps()
        }
        recyclerView.adapter = appListAdapter
    }
    
    @SuppressLint("NotifyDataSetChanged")
    private fun loadInstalledApps() {
        val packageManager = packageManager
        val packages = packageManager.getInstalledApplications(0)
        
        // Load previously selected apps
        val selectedPackages = loadSelectedApps()
        
        appList.clear()
        for (packageInfo in packages) {
            // only show user apps (exclude system apps)
            if ((packageInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                val appName = packageManager.getApplicationLabel(packageInfo).toString()
                val packageName = packageInfo.packageName
                val icon = packageManager.getApplicationIcon(packageInfo)
                
                val isSelected = selectedPackages.contains(packageName)
                appList.add(AppInfo(appName, packageName, icon, isSelected))
            }
        }
        
        val turkishCollator = java.text.Collator.getInstance(java.util.Locale.forLanguageTag("tr-TR"))
        appList.sortWith(compareBy(turkishCollator) { it.appName })
        
        filteredAppList.clear()
        filteredAppList.addAll(appList)
        
        appListAdapter.notifyDataSetChanged()
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
}