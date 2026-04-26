package com.arslan.shizuwall.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.widget.RemoteViews
import android.widget.Toast
import com.arslan.shizuwall.FirewallMode
import com.arslan.shizuwall.R
import com.arslan.shizuwall.receivers.FirewallControlReceiver
import com.arslan.shizuwall.ui.MainActivity
import com.arslan.shizuwall.utils.FirewallUtils

class FirewallWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_CLICK) {
            // Get current state
            val sharedPreferences = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
            val isEnabled = loadFirewallEnabled(sharedPreferences)
            val newState = !isEnabled

            // Check Shizuku permission first
            if (!checkShizukuPermission(context)) {
                return
            }

            // Check constraints before enabling
            var selectedApps: List<String> = emptyList()
            if (newState) {
                selectedApps = loadSelectedApps(context, sharedPreferences)
                val firewallMode = FirewallMode.fromName(sharedPreferences.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name))

                if (selectedApps.isEmpty() && !firewallMode.allowsDynamicSelection()) {
                    Toast.makeText(context, context.getString(R.string.no_apps_selected), Toast.LENGTH_SHORT).show()
                    return
                }
            }

            // Optimistically update widget immediately
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, FirewallWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (appWidgetId in appWidgetIds) {
                updateAppWidgetOptimistic(context, appWidgetManager, appWidgetId, newState)
            }

            // Send toggle broadcast
            val toggleIntent = Intent(context, FirewallControlReceiver::class.java).apply {
                action = MainActivity.ACTION_FIREWALL_CONTROL
                putExtra(MainActivity.EXTRA_FIREWALL_ENABLED, newState)
                if (newState) {
                    putExtra(MainActivity.EXTRA_PACKAGES_CSV, selectedApps.joinToString(","))
                }
            }
            context.sendBroadcast(toggleIntent)
        } else if (intent.action == MainActivity.ACTION_FIREWALL_STATE_CHANGED) {
            // Update widgets when state changes (corrects optimistic update if needed)
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, FirewallWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    companion object {
        const val ACTION_WIDGET_CLICK = "com.arslan.shizuwall.ACTION_WIDGET_CLICK"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val sharedPreferences = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
            val isEnabled = loadFirewallEnabled(sharedPreferences)
            updateAppWidgetOptimistic(context, appWidgetManager, appWidgetId, isEnabled)
        }

        private fun updateAppWidgetOptimistic(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, isEnabled: Boolean) {
            val views = RemoteViews(context.packageName, R.layout.widget_firewall)
            views.setImageViewResource(R.id.widget_icon, if (isEnabled) R.drawable.ic_firewall_enabled else R.drawable.ic_quick_tile)

            val intent = Intent(context, FirewallWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_CLICK
            }
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_layout, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun loadFirewallEnabled(sharedPreferences: SharedPreferences): Boolean = FirewallUtils.loadFirewallEnabled(sharedPreferences)

        private fun loadSelectedApps(context: Context, sharedPreferences: SharedPreferences): List<String> = FirewallUtils.loadSelectedApps(context, sharedPreferences)

        private fun checkShizukuPermission(context: Context): Boolean = FirewallUtils.checkBackendReady(context)
    }
}