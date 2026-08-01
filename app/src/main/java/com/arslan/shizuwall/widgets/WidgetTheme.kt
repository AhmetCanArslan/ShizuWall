package com.arslan.shizuwall.widgets

import android.content.Context
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

    companion object {
        val DEFAULT = SYSTEM

        private fun prefs(context: Context) =
            context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        private fun keyFor(appWidgetId: Int) = "widget_theme_$appWidgetId"

        fun fromKey(key: String?): WidgetTheme =
            entries.firstOrNull { it.key == key } ?: DEFAULT

        fun of(context: Context, appWidgetId: Int): WidgetTheme =
            fromKey(prefs(context).getString(keyFor(appWidgetId), null))

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
