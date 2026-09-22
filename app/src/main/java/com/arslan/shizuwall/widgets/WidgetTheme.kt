package com.arslan.shizuwall.widgets

import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.arslan.shizuwall.R
import com.arslan.shizuwall.ui.MainActivity

enum class WidgetTheme(
    val key: String,
    val labelRes: Int,
    val backgroundRes: Int,
    private val contentColorRes: Int
) {
    SYSTEM("system", R.string.widget_theme_system, R.drawable.widget_bg_system, R.color.widget_content_system),
    LIGHT("light", R.string.widget_theme_light, R.drawable.widget_bg_light, R.color.widget_content_light),
    DARK("dark", R.string.widget_theme_dark, R.drawable.widget_bg_dark, R.color.widget_content_dark),
    TRANSPARENT("transparent", R.string.widget_theme_transparent, R.drawable.widget_bg_transparent, R.color.widget_content_dark);

    fun contentColor(context: Context): Int = ContextCompat.getColor(context, contentColorRes)

    fun applyContent(context: Context, views: RemoteViews, iconId: Int, textId: Int = 0) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(iconId, "setImageTintList", contentColorRes)
            if (textId != 0) views.setColorStateList(textId, "setTextColor", contentColorRes)
            return
        }
        val color = contentColor(context)
        views.setInt(iconId, "setColorFilter", color)
        if (textId != 0) views.setTextColor(textId, color)
    }

    companion object {
        val DEFAULT = SYSTEM

        private fun prefs(context: Context) =
            context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        private fun keyFor(appWidgetId: Int) = "widget_theme_$appWidgetId"

        fun of(context: Context, appWidgetId: Int): WidgetTheme {
            val key = prefs(context).getString(keyFor(appWidgetId), null)
            return entries.firstOrNull { it.key == key } ?: DEFAULT
        }

        fun set(context: Context, appWidgetId: Int, theme: WidgetTheme) {
            prefs(context).edit().putString(keyFor(appWidgetId), theme.key).apply()
        }

        fun clear(context: Context, appWidgetIds: IntArray) {
            val editor = prefs(context).edit()
            for (id in appWidgetIds) editor.remove(keyFor(id))
            editor.apply()
        }
    }
}
