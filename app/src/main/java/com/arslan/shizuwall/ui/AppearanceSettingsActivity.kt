package com.arslan.shizuwall.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import com.arslan.shizuwall.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
class AppearanceSettingsActivity : BaseActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var switchMoveSelectedTop: SwitchCompat
    private lateinit var switchUseDynamicColor: SwitchCompat
    private lateinit var switchUseAmoledBlack: SwitchCompat
    private lateinit var layoutChangeFont: LinearLayout
    private lateinit var tvCurrentFont: TextView
    private lateinit var tvCurrentTheme: TextView
    private lateinit var layoutChangeLanguage: LinearLayout
    private lateinit var tvCurrentLanguage: TextView
    private val themeModes = listOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)
    private val themeLabels = listOf(R.string.widget_theme_system, R.string.widget_theme_light, R.string.widget_theme_dark)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        enableEdgeToEdge()
        setContentView(R.layout.activity_settings_appearance)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupSettingsChrome(R.id.appearanceSettingsRoot, toolbar)

        switchMoveSelectedTop = findViewById(R.id.switchMoveSelectedTop)
        switchUseDynamicColor = findViewById(R.id.switchUseDynamicColor)
        switchUseAmoledBlack = findViewById(R.id.switchUseAmoledBlack)
        layoutChangeFont = findViewById(R.id.layoutChangeFont)
        tvCurrentFont = findViewById(R.id.tvCurrentFont)
        tvCurrentTheme = findViewById(R.id.tvCurrentTheme)
        layoutChangeLanguage = findViewById(R.id.layoutChangeLanguage)
        tvCurrentLanguage = findViewById(R.id.tvCurrentLanguage)

        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        switchMoveSelectedTop.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_MOVE_SELECTED_TOP, false)
        switchUseDynamicColor.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_USE_DYNAMIC_COLOR, true)
        switchUseAmoledBlack.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_USE_AMOLED_BLACK, false)

        val currentFont = sharedPreferences.getString(MainActivity.KEY_SELECTED_FONT, "default") ?: "default"
        tvCurrentFont.text = if (currentFont == "ndot") getString(R.string.font_ndot) else getString(R.string.font_default)
        tvCurrentTheme.setText(themeLabels[themeModes.indexOf(AppCompatDelegate.getDefaultNightMode()).coerceAtLeast(0)])
        updateCurrentLanguageDisplay()
    }

    private fun setupListeners() {
        switchMoveSelectedTop.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(MainActivity.KEY_MOVE_SELECTED_TOP, isChecked).apply()
            setResult(RESULT_OK)
        }

        switchUseDynamicColor.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(MainActivity.KEY_USE_DYNAMIC_COLOR, isChecked).apply()
            recreateWithAnimation()
        }

        switchUseAmoledBlack.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(MainActivity.KEY_USE_AMOLED_BLACK, isChecked).apply()
            recreateWithAnimation()
        }

        layoutChangeFont.setOnClickListener { showFontSelectorDialog() }
        findViewById<View>(R.id.layoutChangeTheme).setOnClickListener { showThemeSelectorDialog() }
        layoutChangeLanguage.setOnClickListener { showLanguageSelectorDialog() }

        makeCardClickableForSwitch(switchMoveSelectedTop)
        makeCardClickableForSwitch(switchUseDynamicColor)
        makeCardClickableForSwitch(switchUseAmoledBlack)
    }

    private fun showThemeSelectorDialog() {
        val checked = themeModes.indexOf(AppCompatDelegate.getDefaultNightMode()).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.theme)
            .setSingleChoiceItems(themeLabels.map { getString(it) }.toTypedArray(), checked) { dialog, which ->
                dialog.dismiss()
                sharedPreferences.edit().putInt(MainActivity.KEY_NIGHT_MODE, themeModes[which]).apply()
                AppCompatDelegate.setDefaultNightMode(themeModes[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showFontSelectorDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_font_selector, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val radioGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.fontRadioGroup)
        val btnApply = dialogView.findViewById<MaterialButton>(R.id.btnApplyFont)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelFont)

        val currentFont = sharedPreferences.getString(MainActivity.KEY_SELECTED_FONT, "default") ?: "default"
        when (currentFont) {
            "ndot" -> radioGroup.check(R.id.radio_ndot)
            else -> radioGroup.check(R.id.radio_default)
        }

        btnApply.setOnClickListener {
            val selectedId = radioGroup.checkedRadioButtonId
            val fontKey = when (selectedId) {
                R.id.radio_ndot -> "ndot"
                else -> "default"
            }

            sharedPreferences.edit().putString(MainActivity.KEY_SELECTED_FONT, fontKey).apply()
            tvCurrentFont.text = if (fontKey == "ndot") getString(R.string.font_ndot) else getString(R.string.font_default)

            dialog.dismiss()
            recreateWithAnimation()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun buildSupportedLocales(): LinkedHashMap<String, String> {
        val map = linkedMapOf("" to getString(R.string.language_system_default))
        try {
            val parser = resources.getXml(R.xml.locales_config)
            var eventType = parser.eventType
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "locale") {
                    val tag = parser.getAttributeValue(
                        "http://schemas.android.com/apk/res/android", "name"
                    )
                    if (!tag.isNullOrEmpty()) {
                        val locale = java.util.Locale.forLanguageTag(tag)
                        val displayName = locale.getDisplayName(locale)
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        map[tag] = displayName.ifEmpty { tag }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            map["en"] = "English"
        }
        return map
    }

    private fun updateCurrentLanguageDisplay() {
        val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        if (currentLocales.isEmpty) {
            tvCurrentLanguage.text = getString(R.string.language_system_default)
        } else {
            val tag = currentLocales.get(0)?.toLanguageTag() ?: ""
            val locale = java.util.Locale.forLanguageTag(tag)
            tvCurrentLanguage.text = locale.getDisplayName(locale)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                .ifEmpty { getString(R.string.language_system_default) }
        }
    }

    private fun showLanguageSelectorDialog() {
        val supportedLocales = buildSupportedLocales()
        val localeKeys = supportedLocales.keys.toList()
        val localeNames = supportedLocales.values.toTypedArray()

        val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        val currentTag = if (currentLocales.isEmpty) "" else (currentLocales.get(0)?.toLanguageTag() ?: "")
        val checkedItem = localeKeys.indexOf(currentTag).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.language)
            .setSingleChoiceItems(localeNames, checkedItem) { dialog, which ->
                val selectedTag = localeKeys[which]
                val newLocales = if (selectedTag.isEmpty()) {
                    androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                } else {
                    androidx.core.os.LocaleListCompat.forLanguageTags(selectedTag)
                }
                dialog.dismiss()
                val rootView = findViewById<android.view.View>(android.R.id.content)
                if (rootView != null) {
                    rootView.animate()
                        .alpha(0f)
                        .setDuration(400)
                        .withEndAction {
                            BaseActivity.requestFadeInAnimation()
                            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(newLocales)
                        }
                        .start()
                } else {
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(newLocales)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
