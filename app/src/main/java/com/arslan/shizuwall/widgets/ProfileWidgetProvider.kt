package com.arslan.shizuwall.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.arslan.shizuwall.R
import com.arslan.shizuwall.profiles.ProfileActivator
import com.arslan.shizuwall.profiles.ProfileIcons
import com.arslan.shizuwall.profiles.ProfilesStore
import com.arslan.shizuwall.ui.MainActivity

class ProfileWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val editor = prefs(context).edit()
        for (appWidgetId in appWidgetIds) editor.remove(keyFor(appWidgetId))
        editor.apply()
        WidgetTheme.clear(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_CLICK -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                val profile = profileFor(context, appWidgetId) ?: return
                ProfileActivator.activateAndEnable(context, profile)
            }
            MainActivity.ACTION_FIREWALL_STATE_CHANGED -> refreshAll(context)
        }
    }

    companion object {
        const val ACTION_WIDGET_CLICK = "com.arslan.shizuwall.ACTION_PROFILE_WIDGET_CLICK"

        private fun prefs(context: Context) =
            context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        private fun keyFor(appWidgetId: Int) = "profile_widget_$appWidgetId"

        fun setProfileId(context: Context, appWidgetId: Int, profileId: String) {
            prefs(context).edit().putString(keyFor(appWidgetId), profileId).apply()
        }

        private fun profileFor(context: Context, appWidgetId: Int) =
            prefs(context).getString(keyFor(appWidgetId), null)
                ?.let { ProfilesStore.getById(context, it) }

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ProfileWidgetProvider::class.java))
            for (id in ids) updateWidget(context, manager, id)
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val profile = profileFor(context, appWidgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_profile)
            val theme = WidgetTheme.of(context, appWidgetId)
            val contentColor = theme.contentColor(context)

            views.setInt(R.id.widget_profile_layout, "setBackgroundResource", theme.backgroundRes)
            views.setImageViewResource(
                R.id.widget_profile_icon,
                if (profile == null) R.drawable.ic_profiles_24px else ProfileIcons.resFor(profile.icon)
            )
            views.setInt(R.id.widget_profile_icon, "setColorFilter", contentColor)
            views.setTextViewText(
                R.id.widget_profile_name,
                profile?.name ?: context.getString(R.string.profile_tile_unassigned)
            )
            views.setTextColor(R.id.widget_profile_name, contentColor)

            val intent = Intent(context, ProfileWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_CLICK
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_profile_layout, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
