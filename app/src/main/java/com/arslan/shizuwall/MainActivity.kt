package com.arslan.shizuwall

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
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
    
    companion object {
        private const val PREF_NAME = "ShizuWallPrefs"
        private const val KEY_SELECTED_APPS = "selected_apps"
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
        
        setupFirewallToggle()
        setupSearchView()
        setupRecyclerView()
        loadInstalledApps()
        updateSelectedCount()
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