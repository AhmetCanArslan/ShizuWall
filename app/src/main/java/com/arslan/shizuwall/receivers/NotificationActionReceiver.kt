package com.arslan.shizuwall.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.arslan.shizuwall.R
import com.arslan.shizuwall.ui.MainActivity

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIREWALL_APP = "com.arslan.shizuwall.ACTION_FIREWALL_APP"
        const val ACTION_ADD_TO_LIST = "com.arslan.shizuwall.ACTION_ADD_TO_LIST"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
        val notificationId = intent.getIntExtra("notification_id", -1)

        when (intent.action) {
            ACTION_FIREWALL_APP -> {
                firewallApp(context, packageName)
            }
            ACTION_ADD_TO_LIST -> {
                addToList(context, packageName)
            }
        }

        // Dismiss notification
        if (notificationId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        } else {
            // Fallback: cancel by hashcode if ID not passed (though we should pass it)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(3000 + packageName.hashCode())
        }
    }

    private fun firewallApp(context: Context, packageName: String) {
        // Add to selected list first
        addToList(context, packageName, showToast = false)

        // Then trigger firewall control for this specific app
        val controlIntent = Intent(context, FirewallControlReceiver::class.java).apply {
            action = MainActivity.ACTION_FIREWALL_CONTROL
            putExtra(MainActivity.EXTRA_FIREWALL_ENABLED, true)
            putExtra(MainActivity.EXTRA_PACKAGES_CSV, packageName)
        }
        context.sendBroadcast(controlIntent)
        Toast.makeText(context, context.getString(R.string.firewalling_app, packageName), Toast.LENGTH_SHORT).show()
    }

    private fun addToList(context: Context, packageName: String, showToast: Boolean = true) {
        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val selectedApps = prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
        
        if (selectedApps.add(packageName)) {
            prefs.edit().putStringSet(MainActivity.KEY_SELECTED_APPS, selectedApps).apply()
            if (showToast) {
                Toast.makeText(context, context.getString(R.string.added_to_selected_list, packageName), Toast.LENGTH_SHORT).show()
            }
        } else {
            if (showToast) {
                Toast.makeText(context, context.getString(R.string.already_in_list, packageName), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
