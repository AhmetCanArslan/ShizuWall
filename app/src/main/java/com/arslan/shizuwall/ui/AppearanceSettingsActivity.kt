package com.arslan.shizuwall.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.arslan.shizuwall.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.util.TypedValue

class AppearanceSettingsActivity : BaseActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var switchMoveSelectedTop: SwitchCompat
    private lateinit var switchUseDynamicColor: SwitchCompat
    private lateinit var switchUseAmoledBlack: SwitchCompat
    private lateinit var layoutChangeFont: LinearLayout
    private lateinit var tvCurrentFont: TextView
    private lateinit var layoutChangeLanguage: LinearLayout
    private lateinit var tvCurrentLanguage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        enableEdgeToEdge()
        setContentView(R.layout.activity_settings_appearance)

        if (sharedPreferences.getBoolean(MainActivity.KEY_USE_AMOLED_BLACK, false)) {
            findViewById<View>(R.id.appearanceSettingsRoot).setBackgroundColor(Color.BLACK)
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appearanceSettingsRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val toolbarParams = toolbar.layoutParams as ViewGroup.MarginLayoutParams
            toolbarParams.topMargin = systemBars.top
            toolbar.layoutParams = toolbarParams
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        switchMoveSelectedTop = findViewById(R.id.switchMoveSelectedTop)
        switchUseDynamicColor = findViewById(R.id.switchUseDynamicColor)
        switchUseAmoledBlack = findViewById(R.id.switchUseAmoledBlack)
        layoutChangeFont = findViewById(R.id.layoutChangeFont)
        tvCurrentFont = findViewById(R.id.tvCurrentFont)
        layoutChangeLanguage = findViewById(R.id.layoutChangeLanguage)
        tvCurrentLanguage = findViewById(R.id.tvCurrentLanguage)

        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        switchMoveSelectedTop.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_MOVE_SELECTED_TOP, true)
        switchUseDynamicColor.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_USE_DYNAMIC_COLOR, true)
        switchUseAmoledBlack.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_USE_AMOLED_BLACK, false)

        val currentFont = sharedPreferences.getString(MainActivity.KEY_SELECTED_FONT, "default") ?: "default"
        tvCurrentFont.text = if (currentFont == "ndot") getString(R.string.font_ndot) else getString(R.string.font_default)
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
        layoutChangeLanguage.setOnClickListener { showLanguageSelectorDialog() }

        makeCardClickableForSwitch(switchMoveSelectedTop)
        makeCardClickableForSwitch(switchUseDynamicColor)
        makeCardClickableForSwitch(switchUseAmoledBlack)
    }

    private fun makeCardClickableForSwitch(switch: SwitchCompat) {
        try {
            val parent = switch.parent as? View ?: return
            val typedValue = TypedValue()
            if (theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)) {
                parent.setBackgroundResource(typedValue.resourceId)
            }
            parent.isClickable = true
            parent.isFocusable = true
            parent.setOnClickListener {
                if (switch.isEnabled) {
                    switch.isChecked = !switch.isChecked
                }
            }
        } catch (e: Exception) {
            // Don't crash if layout assumptions differ; silently ignore
        }
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
