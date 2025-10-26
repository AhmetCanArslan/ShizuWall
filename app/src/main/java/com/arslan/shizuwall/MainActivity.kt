package com.arslan.shizuwall

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var appListAdapter: AppListAdapter
    private lateinit var firewallToggle: SwitchMaterial
    private val appList = mutableListOf<AppInfo>()
    private var isFirewallEnabled = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setupFirewallToggle()
        setupRecyclerView()
        loadInstalledApps()
    }
    
    private fun setupFirewallToggle() {
        firewallToggle = findViewById(R.id.firewallToggle)
        firewallToggle.setOnCheckedChangeListener { _, isChecked ->
            isFirewallEnabled = isChecked
            if (isChecked) {
                Toast.makeText(this, "Firewall Etkinleştirildi", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Firewall Devre Dışı", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        appListAdapter = AppListAdapter(appList) { appInfo ->
            // when an app is clicked
            Toast.makeText(this, "Seçilen: ${appInfo.appName}", Toast.LENGTH_SHORT).show()
        }
        recyclerView.adapter = appListAdapter
    }
    
    private fun loadInstalledApps() {
        val packageManager = packageManager
        val packages = packageManager.getInstalledApplications(0)
        
        appList.clear()
        for (packageInfo in packages) {
            // only show user apps (exclude system apps)
            if ((packageInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                val appName = packageManager.getApplicationLabel(packageInfo).toString()
                val packageName = packageInfo.packageName
                val icon = packageManager.getApplicationIcon(packageInfo)
                
                appList.add(AppInfo(appName, packageName, icon))
            }
        }
        appList.sortBy { it.appName.lowercase() }
        appListAdapter.notifyDataSetChanged()
    }
}