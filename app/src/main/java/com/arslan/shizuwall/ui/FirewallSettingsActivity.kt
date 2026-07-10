package com.arslan.shizuwall.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.text.Selection
import android.text.Spannable
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.arslan.shizuwall.FirewallMode
import com.arslan.shizuwall.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FirewallSettingsActivity : BaseActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var switchSkipConfirm: androidx.appcompat.widget.SwitchCompat
    private lateinit var switchSkipErrorDialog: androidx.appcompat.widget.SwitchCompat
    private lateinit var cardKeepErrorApps: com.google.android.material.card.MaterialCardView
    private lateinit var switchKeepErrorAppsSelected: androidx.appcompat.widget.SwitchCompat
    private lateinit var cardSkipConfirm: com.google.android.material.card.MaterialCardView
    private var suppressFirewallModeListener = false

    private lateinit var radioGroupFirewallMode: RadioGroup
    private lateinit var radioModeDefault: RadioButton
    private lateinit var radioModeAdaptive: RadioButton
    private lateinit var radioModeScreenLock: RadioButton
    private lateinit var radioModeSmartForeground: RadioButton
    private lateinit var radioModeFocusTracker: RadioButton
    private lateinit var radioModeWhitelist: RadioButton
    private lateinit var radioModeHybrid: RadioButton
    private lateinit var cardScreenLockDelay: com.google.android.material.card.MaterialCardView
    private lateinit var layoutScreenLockDelay: android.widget.LinearLayout
    private lateinit var tvScreenLockDelayValue: TextView
    private lateinit var tvFirewallModeDisabledWarning: TextView
    private lateinit var layoutAdbBroadcastUsage: android.widget.LinearLayout
    private lateinit var rootView: ViewGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        enableEdgeToEdge()
        setContentView(R.layout.activity_settings_firewall)

        rootView = findViewById(R.id.firewallSettingsRoot)
        if (sharedPreferences.getBoolean(MainActivity.KEY_USE_AMOLED_BLACK, false)) {
            rootView.setBackgroundColor(Color.BLACK)
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val toolbarParams = toolbar.layoutParams as ViewGroup.MarginLayoutParams
            toolbarParams.topMargin = systemBars.top
            toolbar.layoutParams = toolbarParams
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        initializeViews()
        loadSettings()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        val mode = FirewallMode.fromName(sharedPreferences.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name))
        updateFirewallModeUI(mode)
        val isFirewallEnabled = sharedPreferences.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
        updateFirewallModeSelectorState(isFirewallEnabled)
    }

    private fun initializeViews() {
        cardSkipConfirm = findViewById(R.id.cardSkipConfirm)
        switchSkipConfirm = findViewById(R.id.switchSkipConfirm)
        switchSkipErrorDialog = findViewById(R.id.switchSkipErrorDialog)
        cardKeepErrorApps = findViewById(R.id.cardKeepErrorApps)
        switchKeepErrorAppsSelected = findViewById(R.id.switchKeepErrorAppsSelected)
        layoutAdbBroadcastUsage = findViewById(R.id.layoutAdbBroadcastUsage)

        radioGroupFirewallMode = findViewById(R.id.radioGroupFirewallMode)
        radioModeDefault = findViewById(R.id.radioModeDefault)
        radioModeAdaptive = findViewById(R.id.radioModeAdaptive)
        radioModeScreenLock = findViewById(R.id.radioModeScreenLock)
        radioModeSmartForeground = findViewById(R.id.radioModeSmartForeground)
        radioModeFocusTracker = findViewById(R.id.radioModeFocusTracker)
        radioModeWhitelist = findViewById(R.id.radioModeWhitelist)
        radioModeHybrid = findViewById(R.id.radioModeHybrid)
        cardScreenLockDelay = findViewById(R.id.cardScreenLockDelay)
        layoutScreenLockDelay = findViewById(R.id.layoutScreenLockDelay)
        tvScreenLockDelayValue = findViewById(R.id.tvScreenLockDelayValue)
        tvFirewallModeDisabledWarning = findViewById(R.id.tvFirewallModeDisabledWarning)
    }

    private fun loadSettings() {
        switchSkipConfirm.isChecked = sharedPreferences.getBoolean("skip_enable_confirm", false)
        switchSkipErrorDialog.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_SKIP_ERROR_DIALOG, false)
        switchKeepErrorAppsSelected.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_KEEP_ERROR_APPS_SELECTED, false)
        cardKeepErrorApps.visibility = if (switchSkipErrorDialog.isChecked) View.VISIBLE else View.GONE

        migrateAdaptiveModeToFirewallMode(sharedPreferences)
        val firewallMode = FirewallMode.fromName(sharedPreferences.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name))

        when (firewallMode) {
            FirewallMode.ADAPTIVE -> radioGroupFirewallMode.check(R.id.radioModeAdaptive)
            FirewallMode.SCREEN_LOCK_MODE -> radioGroupFirewallMode.check(R.id.radioModeScreenLock)
            FirewallMode.SMART_FOREGROUND -> radioGroupFirewallMode.check(R.id.radioModeSmartForeground)
            FirewallMode.FOCUS_TRACKER -> radioGroupFirewallMode.check(R.id.radioModeFocusTracker)
            FirewallMode.WHITELIST -> radioGroupFirewallMode.check(R.id.radioModeWhitelist)
            FirewallMode.HYBRID -> radioGroupFirewallMode.check(R.id.radioModeHybrid)
            else -> radioGroupFirewallMode.check(R.id.radioModeDefault)
        }
        updateScreenLockDelaySummary()
        updateFirewallModeUI(firewallMode)

        val isFirewallEnabled = sharedPreferences.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
        updateFirewallModeSelectorState(isFirewallEnabled)
    }

    private fun migrateAdaptiveModeToFirewallMode(sharedPreferences: SharedPreferences) {
        if (sharedPreferences.contains(MainActivity.KEY_ADAPTIVE_MODE) && !sharedPreferences.contains(MainActivity.KEY_FIREWALL_MODE)) {
            val adaptiveMode = sharedPreferences.getBoolean(MainActivity.KEY_ADAPTIVE_MODE, false)
            val newMode = if (adaptiveMode) FirewallMode.ADAPTIVE else FirewallMode.DEFAULT
            sharedPreferences.edit()
                .putString(MainActivity.KEY_FIREWALL_MODE, newMode.name)
                .remove(MainActivity.KEY_ADAPTIVE_MODE)
                .apply()
        }
    }

    private fun commitFirewallMode(newMode: FirewallMode) {
        sharedPreferences.edit().putString(MainActivity.KEY_FIREWALL_MODE, newMode.name).apply()
        setResult(RESULT_OK)

        TransitionManager.beginDelayedTransition(rootView, AutoTransition())
        updateFirewallModeUI(newMode)

        if (newMode == FirewallMode.WHITELIST) {
            showWhitelistInfoDialog()
        }
    }

    private fun revertFirewallModeSelection() {
        val currentMode = FirewallMode.fromName(
            sharedPreferences.getString(MainActivity.KEY_FIREWALL_MODE, FirewallMode.DEFAULT.name)
        )
        val targetId = when (currentMode) {
            FirewallMode.ADAPTIVE -> R.id.radioModeAdaptive
            FirewallMode.SCREEN_LOCK_MODE -> R.id.radioModeScreenLock
            FirewallMode.SMART_FOREGROUND -> R.id.radioModeSmartForeground
            FirewallMode.FOCUS_TRACKER -> R.id.radioModeFocusTracker
            FirewallMode.WHITELIST -> R.id.radioModeWhitelist
            FirewallMode.HYBRID -> R.id.radioModeHybrid
            else -> R.id.radioModeDefault
        }
        suppressFirewallModeListener = true
        radioGroupFirewallMode.check(targetId)
        suppressFirewallModeListener = false
    }

    private fun updateFirewallModeUI(mode: FirewallMode) {
        val showSkipConfirm = mode == FirewallMode.DEFAULT
        cardSkipConfirm.visibility = if (showSkipConfirm) View.VISIBLE else View.GONE

        cardScreenLockDelay.visibility = if (mode == FirewallMode.SCREEN_LOCK_MODE || mode == FirewallMode.HYBRID) View.VISIBLE else View.GONE
        if (mode == FirewallMode.SCREEN_LOCK_MODE || mode == FirewallMode.HYBRID) {
            updateScreenLockDelaySummary()
        }

        if (mode != FirewallMode.DEFAULT && !switchSkipConfirm.isChecked) {
            switchSkipConfirm.isChecked = true
            sharedPreferences.edit().putBoolean("skip_enable_confirm", true).apply()
        }
    }

    private fun updateFirewallModeSelectorState(isFirewallEnabled: Boolean) {
        radioGroupFirewallMode.isEnabled = !isFirewallEnabled
        radioModeDefault.isEnabled = !isFirewallEnabled
        radioModeAdaptive.isEnabled = !isFirewallEnabled
        radioModeScreenLock.isEnabled = !isFirewallEnabled
        radioModeSmartForeground.isEnabled = !isFirewallEnabled
        radioModeFocusTracker.isEnabled = !isFirewallEnabled
        radioModeWhitelist.isEnabled = !isFirewallEnabled
        radioModeHybrid.isEnabled = !isFirewallEnabled

        val targetAlpha = if (isFirewallEnabled) 0.5f else 1f
        radioGroupFirewallMode.alpha = targetAlpha

        tvFirewallModeDisabledWarning.visibility = if (isFirewallEnabled) View.VISIBLE else View.GONE
    }

    private fun setupListeners() {
        radioGroupFirewallMode.setOnCheckedChangeListener { _, checkedId ->
            if (suppressFirewallModeListener) return@setOnCheckedChangeListener

            val isFirewallEnabled = sharedPreferences.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)
            if (isFirewallEnabled) {
                Toast.makeText(this, R.string.firewall_mode_change_disabled, Toast.LENGTH_LONG).show()
                revertFirewallModeSelection()
                return@setOnCheckedChangeListener
            }

            val newMode = when (checkedId) {
                R.id.radioModeAdaptive -> FirewallMode.ADAPTIVE
                R.id.radioModeScreenLock -> FirewallMode.SCREEN_LOCK_MODE
                R.id.radioModeSmartForeground -> FirewallMode.SMART_FOREGROUND
                R.id.radioModeFocusTracker -> FirewallMode.FOCUS_TRACKER
                R.id.radioModeWhitelist -> FirewallMode.WHITELIST
                R.id.radioModeHybrid -> FirewallMode.HYBRID
                else -> FirewallMode.DEFAULT
            }

            commitFirewallMode(newMode)
        }

        switchSkipConfirm.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("skip_enable_confirm", isChecked).apply()
        }

        switchSkipErrorDialog.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(MainActivity.KEY_SKIP_ERROR_DIALOG, isChecked).apply()

            TransitionManager.beginDelayedTransition(rootView, AutoTransition())
            cardKeepErrorApps.visibility = if (isChecked) View.VISIBLE else View.GONE

            if (!isChecked) {
                switchKeepErrorAppsSelected.isChecked = false
                sharedPreferences.edit().putBoolean(MainActivity.KEY_KEEP_ERROR_APPS_SELECTED, false).apply()
            }
        }

        switchKeepErrorAppsSelected.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(MainActivity.KEY_KEEP_ERROR_APPS_SELECTED, isChecked).apply()
        }

        layoutScreenLockDelay.setOnClickListener { showScreenLockDelayDialog() }
        layoutAdbBroadcastUsage.setOnClickListener { showAdbBroadcastDialog() }

        makeCardClickableForSwitch(switchSkipConfirm)
        makeCardClickableForSwitch(switchSkipErrorDialog)
        makeCardClickableForSwitch(switchKeepErrorAppsSelected)
    }

    private fun makeCardClickableForSwitch(switch: androidx.appcompat.widget.SwitchCompat) {
        try {
            val parent = switch.parent as? View ?: return
            val typedValue = android.util.TypedValue()
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

    private fun updateScreenLockDelaySummary() {
        val delay = sharedPreferences
            .getInt(
                MainActivity.KEY_SCREEN_LOCK_DELAY_SECONDS,
                MainActivity.DEFAULT_SCREEN_LOCK_DELAY_SECONDS
            )
            .coerceIn(2, 10)
        tvScreenLockDelayValue.text = getString(R.string.screen_lock_delay_value, delay)
    }

    private fun showScreenLockDelayDialog() {
        val picker = NumberPicker(this).apply {
            minValue = 2
            maxValue = 10
            value = sharedPreferences
                .getInt(
                    MainActivity.KEY_SCREEN_LOCK_DELAY_SECONDS,
                    MainActivity.DEFAULT_SCREEN_LOCK_DELAY_SECONDS
                )
                .coerceIn(2, 10)
            wrapSelectorWheel = false
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.screen_lock_delay_dialog_title)
            .setView(picker)
            .setPositiveButton(R.string.apply) { _, _ ->
                val selectedDelay = picker.value.coerceIn(2, 10)
                sharedPreferences.edit()
                    .putInt(MainActivity.KEY_SCREEN_LOCK_DELAY_SECONDS, selectedDelay)
                    .apply()
                updateScreenLockDelaySummary()
                setResult(RESULT_OK)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showWhitelistInfoDialog() {
        val showPrompt = sharedPreferences.getBoolean("show_whitelist_prompt", true)
        if (!showPrompt) return

        val promptView = layoutInflater.inflate(R.layout.dialog_shizuku_prompt, null)
        val messageText: TextView = promptView.findViewById(R.id.shizuku_prompt_message_text)
        val checkbox: android.widget.CheckBox = promptView.findViewById(R.id.shizuku_prompt_do_not_show)

        messageText.text = getString(R.string.whitelist_info_system_apps_warning)
        checkbox.text = getString(R.string.dont_show_again)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.firewall_mode_whitelist)
            .setView(promptView)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (checkbox.isChecked) {
                    sharedPreferences.edit().putBoolean("show_whitelist_prompt", false).apply()
                }
            }
            .show()
    }

    private fun showAdbBroadcastDialog() {
        val pkg = "com.arslan.shizuwall"
        val action = MainActivity.ACTION_FIREWALL_CONTROL
        val extraEnabled = MainActivity.EXTRA_FIREWALL_ENABLED
        val extraCsv = MainActivity.EXTRA_PACKAGES_CSV
        val component = "$pkg/.receivers.FirewallControlReceiver"

        val cmdEnableSelected = "adb shell am broadcast -a $action -n $component --ez $extraEnabled true"
        val cmdDisableSelected = "adb shell am broadcast -a $action -n $component --ez $extraEnabled false"
        val cmdEnableCsv = "adb shell am broadcast -a $action -n $component --ez $extraEnabled true --es $extraCsv \"com.example.app1,com.example.app2\""
        val cmdDisableCsv = "adb shell am broadcast -a $action -n $component --ez $extraEnabled false --es $extraCsv \"com.example.app1,com.example.app2\""

        val dialogView = layoutInflater.inflate(R.layout.dialog_adb_broadcast_usage, null)

        val tvBroadcastAction = dialogView.findViewById<TextView>(R.id.tvBroadcastActionValue)
        val tvBroadcastComponent = dialogView.findViewById<TextView>(R.id.tvBroadcastComponentValue)
        val tvCmdEnableSelected = dialogView.findViewById<TextView>(R.id.tvCmdEnableSelected)
        val tvCmdDisableSelected = dialogView.findViewById<TextView>(R.id.tvCmdDisableSelected)
        val tvCmdEnableCsv = dialogView.findViewById<TextView>(R.id.tvCmdEnableCsv)
        val tvCmdDisableCsv = dialogView.findViewById<TextView>(R.id.tvCmdDisableCsv)
        val tvBroadcastExtras = dialogView.findViewById<TextView>(R.id.tvBroadcastExtrasValue)
        val tvDevCmdEnableFramework = dialogView.findViewById<TextView>(R.id.tvDevCmdEnableFramework)
        val tvDevCmdBlockPackage = dialogView.findViewById<TextView>(R.id.tvDevCmdBlockPackage)
        val tvDevCmdUnblockPackage = dialogView.findViewById<TextView>(R.id.tvDevCmdUnblockPackage)
        val tvDevCmdDisableFramework = dialogView.findViewById<TextView>(R.id.tvDevCmdDisableFramework)
        val btnCopyBroadcastAction = dialogView.findViewById<View>(R.id.btnCopyBroadcastAction)
        val btnCopyBroadcastComponent = dialogView.findViewById<View>(R.id.btnCopyBroadcastComponent)
        val btnCopyCmdEnableSelected = dialogView.findViewById<View>(R.id.btnCopyCmdEnableSelected)
        val btnCopyCmdDisableSelected = dialogView.findViewById<View>(R.id.btnCopyCmdDisableSelected)
        val btnCopyCmdEnableCsv = dialogView.findViewById<View>(R.id.btnCopyCmdEnableCsv)
        val btnCopyCmdDisableCsv = dialogView.findViewById<View>(R.id.btnCopyCmdDisableCsv)
        val btnCopyDevCmdEnableFramework = dialogView.findViewById<View>(R.id.btnCopyDevCmdEnableFramework)
        val btnCopyDevCmdBlockPackage = dialogView.findViewById<View>(R.id.btnCopyDevCmdBlockPackage)
        val btnCopyDevCmdUnblockPackage = dialogView.findViewById<View>(R.id.btnCopyDevCmdUnblockPackage)
        val btnCopyDevCmdDisableFramework = dialogView.findViewById<View>(R.id.btnCopyDevCmdDisableFramework)

        tvBroadcastAction.text = action
        tvBroadcastComponent.text = component
        tvCmdEnableSelected.text = cmdEnableSelected
        tvCmdDisableSelected.text = cmdDisableSelected
        tvCmdEnableCsv.text = cmdEnableCsv
        tvCmdDisableCsv.text = cmdDisableCsv

        tvBroadcastExtras.text = getString(
            R.string.adb_broadcast_extras_summary,
            extraEnabled,
            extraCsv
        )

        val selectableTextViews = listOf(
            tvBroadcastAction,
            tvBroadcastComponent,
            tvCmdEnableSelected,
            tvCmdDisableSelected,
            tvCmdEnableCsv,
            tvCmdDisableCsv,
            tvDevCmdEnableFramework,
            tvDevCmdBlockPackage,
            tvDevCmdUnblockPackage,
            tvDevCmdDisableFramework
        )

        val clipboardManager = getSystemService(ClipboardManager::class.java)

        fun View.enableCopyFrom(source: TextView, copyLabel: String) {
            setOnClickListener {
                val value = source.text?.toString().orEmpty()
                if (value.isNotEmpty()) {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText(copyLabel, value))
                    Toast.makeText(this@FirewallSettingsActivity, getString(R.string.copied), Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnCopyBroadcastAction.enableCopyFrom(tvBroadcastAction, "adb_action")
        btnCopyBroadcastComponent.enableCopyFrom(tvBroadcastComponent, "adb_component")
        btnCopyCmdEnableSelected.enableCopyFrom(tvCmdEnableSelected, "adb_enable_selected")
        btnCopyCmdDisableSelected.enableCopyFrom(tvCmdDisableSelected, "adb_disable_selected")
        btnCopyCmdEnableCsv.enableCopyFrom(tvCmdEnableCsv, "adb_enable_csv")
        btnCopyCmdDisableCsv.enableCopyFrom(tvCmdDisableCsv, "adb_disable_csv")
        btnCopyDevCmdEnableFramework.enableCopyFrom(tvDevCmdEnableFramework, "dev_enable_firewall_framework")
        btnCopyDevCmdBlockPackage.enableCopyFrom(tvDevCmdBlockPackage, "dev_block_specific_app")
        btnCopyDevCmdUnblockPackage.enableCopyFrom(tvDevCmdUnblockPackage, "dev_unblock_specific_app")
        btnCopyDevCmdDisableFramework.enableCopyFrom(tvDevCmdDisableFramework, "dev_disable_firewall_framework")

        dialogView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                selectableTextViews.forEach { textView ->
                    (textView.text as? Spannable)?.let { Selection.removeSelection(it) }
                    textView.clearFocus()
                }
                dialogView.clearFocus()
            }
            false
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.adb_broadcast_usage_title)
            .setView(dialogView)
            .setPositiveButton(R.string.ok, null)
            .create()
            .show()
    }
}
