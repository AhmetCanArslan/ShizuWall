package com.arslan.shizuwall.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.arslan.shizuwall.ui.main.MainActivity
import com.arslan.shizuwall.util.ShizukuUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra("package_name") ?: return
        Log.d("ActionReceiver", "Add to selected list clicked for: $packageName")

        // Dismiss the notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(packageName.hashCode())

        val pendingResult = goAsync()
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val selectedApps = prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
                
                if (selectedApps.add(packageName)) {
                    prefs.edit()
                        .putStringSet(MainActivity.KEY_SELECTED_APPS, selectedApps)
                        .putInt(MainActivity.KEY_SELECTED_COUNT, selectedApps.size)
                        .apply()
                    
                    // If firewall is ON, block the app immediately even if MainActivity is closed
                    val isFirewallEnabled = prefs.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
                    if (isFirewallEnabled) {
                        val res = ShizukuUtils.runCommand("cmd connectivity set-package-networking-enabled false $packageName")
                        if (res.success) {
                            val activePackages = prefs.getStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
                            activePackages.add(packageName)
                            prefs.edit().putStringSet(MainActivity.KEY_ACTIVE_PACKAGES, activePackages).apply()
                        }
                    }

                    // Notify MainActivity to refresh if it's running
                    val refreshIntent = Intent("com.arslan.shizuwall.ACTION_REFRESH_LIST")
                    refreshIntent.putExtra("package_name", packageName)
                    context.sendBroadcast(refreshIntent)
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Added $packageName to selected list", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
