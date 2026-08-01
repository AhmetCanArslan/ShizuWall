package com.arslan.shizuwall.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.arslan.shizuwall.R
import com.arslan.shizuwall.model.Profile
import com.arslan.shizuwall.profiles.ProfileIcons
import com.arslan.shizuwall.profiles.ProfilesStore
import com.arslan.shizuwall.widgets.FirewallWidgetProvider
import com.arslan.shizuwall.widgets.ProfileWidgetProvider
import com.arslan.shizuwall.widgets.WidgetTheme
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.arslan.shizuwall.utils.FirewallUtils

abstract class WidgetConfigActivity : BaseActivity() {

    protected abstract val titleRes: Int
    protected abstract val subtitleRes: Int
    protected abstract val previewLayoutRes: Int
    protected open val picksProfile: Boolean = false

    protected var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        private set

    private lateinit var previewContainer: FrameLayout
    private lateinit var themeChips: ChipGroup
    private lateinit var profileList: LinearLayout

    private var profiles: List<Profile> = emptyList()
    private var selectedProfile: Profile? = null
    private var selectedTheme: WidgetTheme = WidgetTheme.DEFAULT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        if (picksProfile) {
            profiles = ProfilesStore.getProfiles(this)
            if (profiles.isEmpty()) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.profiles)
                    .setMessage(R.string.profile_widget_no_profiles)
                    .setPositiveButton(R.string.ok) { _, _ -> finish() }
                    .setOnCancelListener { finish() }
                    .show()
                return
            }
            selectedProfile = profiles.first()
        }

        setContentView(R.layout.activity_widget_config)
        applyWindowInsets()

        findViewById<TextView>(R.id.widgetConfigTitle).setText(titleRes)
        findViewById<TextView>(R.id.widgetConfigSubtitle).setText(subtitleRes)
        previewContainer = findViewById(R.id.widgetPreviewContainer)
        themeChips = findViewById(R.id.widgetThemeChips)
        profileList = findViewById(R.id.widgetProfileList)

        selectedTheme = WidgetTheme.of(this, appWidgetId)

        buildProfileList()
        buildThemeChips()
        renderPreview()

        findViewById<MaterialButton>(R.id.widgetConfigCancel).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.widgetConfigConfirm).setOnClickListener { confirm() }
    }

    private fun applyWindowInsets() {
        val root = findViewById<LinearLayout>(R.id.widgetConfigRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun buildProfileList() {
        if (!picksProfile) {
            profileList.visibility = View.GONE
            findViewById<TextView>(R.id.widgetProfileHeader).visibility = View.GONE
            return
        }
        val inflater = LayoutInflater.from(this)
        profiles.forEach { profile ->
            val row = inflater.inflate(R.layout.item_widget_profile_choice, profileList, false)
            row.findViewById<ImageView>(R.id.widgetChoiceIcon)
                .setImageResource(ProfileIcons.resFor(profile.icon))
            row.findViewById<TextView>(R.id.widgetChoiceName).text = profile.name
            row.setOnClickListener {
                selectedProfile = profile
                paintProfileRows()
                renderPreview()
            }
            row.tag = profile.id
            profileList.addView(row)
        }
        paintProfileRows()
    }

    private fun paintProfileRows() {
        val selectedId = selectedProfile?.id
        for (index in 0 until profileList.childCount) {
            val row = profileList.getChildAt(index)
            val card = row as? MaterialCardView ?: continue
            val isSelected = row.tag == selectedId
            val strokeAttr = if (isSelected) {
                androidx.appcompat.R.attr.colorPrimary
            } else {
                com.google.android.material.R.attr.colorOutlineVariant
            }
            val surfaceAttr = if (isSelected) {
                com.google.android.material.R.attr.colorSecondaryContainer
            } else {
                com.google.android.material.R.attr.colorSurface
            }
            val contentAttr = if (isSelected) {
                com.google.android.material.R.attr.colorOnSecondaryContainer
            } else {
                com.google.android.material.R.attr.colorOnSurface
            }
            card.strokeWidth = if (isSelected) dp(2) else dp(1)
            card.strokeColor = MaterialColors.getColor(card, strokeAttr)
            card.setCardBackgroundColor(MaterialColors.getColor(card, surfaceAttr))

            val contentColor = MaterialColors.getColor(card, contentAttr)
            card.findViewById<ImageView>(R.id.widgetChoiceIcon).setColorFilter(contentColor)
            card.findViewById<TextView>(R.id.widgetChoiceName).setTextColor(contentColor)
            card.findViewById<ImageView>(R.id.widgetChoiceCheck).apply {
                setColorFilter(contentColor)
                visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildThemeChips() {
        WidgetTheme.entries.forEach { theme ->
            val chip = Chip(this).apply {
                setText(theme.labelRes)
                isCheckable = true
                isChecked = theme == selectedTheme
                setOnClickListener {
                    selectedTheme = theme
                    renderPreview()
                }
            }
            themeChips.addView(chip)
        }
    }

    private fun renderPreview() {
        previewContainer.removeAllViews()
        val view = LayoutInflater.from(this).inflate(previewLayoutRes, previewContainer, false)
        view.layoutParams = FrameLayout.LayoutParams(
            resources.getDimensionPixelSize(R.dimen.widget_preview_size),
            resources.getDimensionPixelSize(R.dimen.widget_preview_size),
            Gravity.CENTER
        )
        view.setBackgroundResource(selectedTheme.backgroundRes)

        val contentColor = selectedTheme.contentColor(this)
        val profile = selectedProfile

        view.findViewById<ImageView>(R.id.widget_profile_icon)?.let { icon ->
            icon.setImageResource(
                if (profile == null) R.drawable.ic_profiles_24px else ProfileIcons.resFor(profile.icon)
            )
            icon.setColorFilter(contentColor)
        }
        view.findViewById<TextView>(R.id.widget_profile_name)?.let { label ->
            label.text = profile?.name ?: getString(R.string.profile_tile_unassigned)
            label.setTextColor(contentColor)
        }
        view.findViewById<ImageView>(R.id.widget_icon)?.let { icon ->
            val enabled = FirewallUtils.loadFirewallEnabled(
                getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE)
            )
            icon.setImageResource(
                if (enabled) R.drawable.ic_widget_network_blocked else R.drawable.ic_widget_network_allowed
            )
            icon.setColorFilter(contentColor)
        }

        previewContainer.addView(view)
    }

    private fun confirm() {
        WidgetTheme.set(this, appWidgetId, selectedTheme)
        onConfirm(selectedProfile)
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )
        finish()
    }

    protected abstract fun onConfirm(profile: Profile?)
}

class ProfileWidgetConfigActivity : WidgetConfigActivity() {
    override val titleRes = R.string.widget_profile_label
    override val subtitleRes = R.string.widget_config_profile_subtitle
    override val previewLayoutRes = R.layout.widget_profile
    override val picksProfile = true

    override fun onConfirm(profile: Profile?) {
        val chosen = profile ?: return
        ProfileWidgetProvider.setProfileId(this, appWidgetId, chosen.id)
        ProfileWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), appWidgetId)
    }
}

class FirewallWidgetConfigActivity : WidgetConfigActivity() {
    override val titleRes = R.string.widget_firewall_label
    override val subtitleRes = R.string.widget_config_firewall_subtitle
    override val previewLayoutRes = R.layout.widget_firewall

    override fun onConfirm(profile: Profile?) {
        FirewallWidgetProvider.updateAppWidget(this, AppWidgetManager.getInstance(this), appWidgetId)
    }
}
