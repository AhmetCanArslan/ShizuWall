package com.arslan.shizuwall.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Selection
import android.text.Spannable
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.text.HtmlCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import com.arslan.shizuwall.FirewallMode
import com.arslan.shizuwall.R
import com.arslan.shizuwall.firewall.FirewallTargets
import com.arslan.shizuwall.receivers.FirewallControlReceiver
import com.arslan.shizuwall.services.AppMonitorService
import com.arslan.shizuwall.utils.AppKey
import com.arslan.shizuwall.utils.FirewallUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class FirewallSettingsActivity : BaseActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var switchSkipConfirm: androidx.appcompat.widget.SwitchCompat
    private lateinit var switchSkipErrorDialog: androidx.appcompat.widget.SwitchCompat
    private lateinit var cardKeepErrorApps: com.google.android.material.card.MaterialCardView
    private lateinit var switchKeepErrorAppsSelected: androidx.appcompat.widget.SwitchCompat
    private lateinit var switchRememberDeletedApps: androidx.appcompat.widget.SwitchCompat
    private lateinit var switchAutoFirewallNewApps: androidx.appcompat.widget.SwitchCompat
    private lateinit var switchShowSystemApps: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var switchShowOtherProfiles: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var cardShowSystemApps: com.google.android.material.card.MaterialCardView
    private lateinit var cardShowOtherProfiles: com.google.android.material.card.MaterialCardView
    private lateinit var tvAppVisibilityDisabledWarning: TextView
    private var suppressVisibilityListeners = false
    private lateinit var cardSkipConfirm: com.google.android.material.card.MaterialCardView
    private var suppressFirewallModeListener = false

    private lateinit var radioGroupFirewallMode: RadioGroup
    private lateinit var firewallModeProgress: View
    private val modeRadios = mapOf(
        R.id.radioModeDefault to FirewallMode.DEFAULT,
        R.id.radioModeAdaptive to FirewallMode.ADAPTIVE,
        R.id.radioModeScreenLock to FirewallMode.SCREEN_LOCK_MODE,
        R.id.radioModeSmartForeground to FirewallMode.SMART_FOREGROUND,
        R.id.radioModeFocusTracker to FirewallMode.FOCUS_TRACKER,
        R.id.radioModeWhitelist to FirewallMode.WHITELIST,
        R.id.radioModeHybrid to FirewallMode.HYBRID
    )
    private lateinit var cardScreenLockDelay: com.google.android.material.card.MaterialCardView
    private lateinit var tvScreenLockDelayValue: TextView
    private lateinit var tvFirewallModeDisabledWarning: TextView
    private lateinit var rootView: ViewGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        enableEdgeToEdge()
        setContentView(R.layout.activity_settings_firewall)

        rootView = findViewById(R.id.firewallSettingsRoot)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupSettingsChrome(R.id.firewallSettingsRoot, toolbar)

        initializeViews()
        loadSettings()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        val mode = FirewallUtils.firewallMode(sharedPreferences)
        updateFirewallModeUI(mode)
        val locked = mode == FirewallMode.WHITELIST && FirewallUtils.loadFirewallEnabled(sharedPreferences)
        radioGroupFirewallMode.alpha = if (locked || firewallModeProgress.isVisible) 0.5f else 1f
        tvFirewallModeDisabledWarning.visibility = if (locked) View.VISIBLE else View.GONE
        loadAppVisibilitySettings()
    }

    private fun initializeViews() {
        cardSkipConfirm = findViewById(R.id.cardSkipConfirm)
        switchSkipConfirm = findViewById(R.id.switchSkipConfirm)
        switchSkipErrorDialog = findViewById(R.id.switchSkipErrorDialog)
        cardKeepErrorApps = findViewById(R.id.cardKeepErrorApps)
        switchKeepErrorAppsSelected = findViewById(R.id.switchKeepErrorAppsSelected)
        switchRememberDeletedApps = findViewById(R.id.switchRememberDeletedApps)
        switchAutoFirewallNewApps = findViewById(R.id.switchAutoFirewallNewApps)
        switchShowSystemApps = findViewById(R.id.switchShowSystemApps)
        switchShowOtherProfiles = findViewById(R.id.switchShowOtherProfiles)
        cardShowSystemApps = findViewById(R.id.cardShowSystemApps)
        cardShowOtherProfiles = findViewById(R.id.cardShowOtherProfiles)
        tvAppVisibilityDisabledWarning = findViewById(R.id.tvAppVisibilityDisabledWarning)

        radioGroupFirewallMode = findViewById(R.id.radioGroupFirewallMode)
        firewallModeProgress = findViewById(R.id.firewallModeProgress)
        cardScreenLockDelay = findViewById(R.id.cardScreenLockDelay)
        tvScreenLockDelayValue = findViewById(R.id.tvScreenLockDelayValue)
        tvFirewallModeDisabledWarning = findViewById(R.id.tvFirewallModeDisabledWarning)
    }

    private fun showFirewallModeDetailsDialog() {
        val modes = listOf(
            R.string.firewall_mode_default to R.string.firewall_mode_default_details,
            R.string.firewall_mode_adaptive to R.string.firewall_mode_adaptive_details,
            R.string.firewall_mode_whitelist to R.string.firewall_mode_whitelist_details,
            R.string.firewall_mode_screen_lock to R.string.firewall_mode_screen_lock_details,
            R.string.firewall_mode_smart_foreground to R.string.firewall_mode_smart_foreground_details,
            R.string.firewall_mode_hybrid to R.string.firewall_mode_hybrid_details,
            R.string.firewall_mode_focus_tracker to R.string.firewall_mode_focus_tracker_details
        )

        val html = buildString {
            modes.forEach { (titleRes, detailsRes) ->
                append("<b>").append(getString(titleRes)).append("</b><br>")
                append(getString(detailsRes)).append("<br><br>")
            }
            append("<i>").append(getString(R.string.firewall_mode_details_foreground_note)).append("</i>")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.firewall_mode_details_title)
            .setMessage(HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun loadSettings() {
        switchSkipConfirm.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_SKIP_ENABLE_CONFIRM, false)
        switchSkipErrorDialog.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_SKIP_ERROR_DIALOG, false)
        switchKeepErrorAppsSelected.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_KEEP_ERROR_APPS_SELECTED, false)
        switchRememberDeletedApps.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_REMEMBER_DISABLED_APPS, true)
        switchAutoFirewallNewApps.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_AUTO_FIREWALL_NEW_APPS, false)
        cardKeepErrorApps.visibility = if (switchSkipErrorDialog.isChecked) View.VISIBLE else View.GONE
        checkModeRadio(FirewallUtils.firewallMode(sharedPreferences))
    }

    private fun loadAppVisibilitySettings() {
        val isFirewallEnabled = sharedPreferences.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)

        suppressVisibilityListeners = true
        switchShowSystemApps.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_SHOW_SYSTEM_APPS, false)
        switchShowOtherProfiles.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_SHOW_OTHER_PROFILES, false)
        suppressVisibilityListeners = false

        switchShowSystemApps.isEnabled = !isFirewallEnabled
        switchShowOtherProfiles.isEnabled = !isFirewallEnabled
        cardShowSystemApps.alpha = if (isFirewallEnabled) 0.5f else 1f
        cardShowOtherProfiles.alpha = if (isFirewallEnabled) 0.5f else 1f
        tvAppVisibilityDisabledWarning.visibility = if (isFirewallEnabled) View.VISIBLE else View.GONE
    }

    private fun selectedKeys(): Set<String> =
        sharedPreferences.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet()) ?: emptySet()

    private fun dropSelectionKeys(keys: Set<String>) {
        val remaining = selectedKeys() - keys
        val modes = try {
            JSONObject(sharedPreferences.getString(MainActivity.KEY_APP_MODES, "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        keys.forEach { modes.remove(it) }

        sharedPreferences.edit()
            .putStringSet(MainActivity.KEY_SELECTED_APPS, remaining)
            .putInt(MainActivity.KEY_SELECTED_COUNT, remaining.size)
            .putString(MainActivity.KEY_APP_MODES, modes.toString())
            .apply()
    }

    private fun selectedSystemAppKeys(): Set<String> = selectedKeys().filterTo(mutableSetOf()) { key ->
        val flags = runCatching { packageManager.getApplicationInfo(AppKey.packageOf(key), 0).flags }.getOrDefault(0)
        (flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }

    private fun setVisibilityPreference(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
        setResult(RESULT_OK)
        FirewallUtils.reapply(this, sharedPreferences)
    }

    private fun bindVisibilitySwitch(
        switch: com.google.android.material.materialswitch.MaterialSwitch,
        key: String,
        titleRes: Int,
        messageRes: Int,
        selected: () -> Set<String>
    ) = switch.setOnCheckedChangeListener { _, isChecked ->
        if (suppressVisibilityListeners) return@setOnCheckedChangeListener
        val keys = if (isChecked) emptySet() else selected()
        if (keys.isEmpty()) {
            setVisibilityPreference(key, isChecked)
            return@setOnCheckedChangeListener
        }
        var confirmed = false
        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                confirmed = true
                dropSelectionKeys(keys)
                setVisibilityPreference(key, false)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener {
                if (!confirmed) {
                    suppressVisibilityListeners = true
                    switch.isChecked = true
                    suppressVisibilityListeners = false
                }
            }
            .show()
    }

    private fun commitFirewallMode(oldMode: FirewallMode, newMode: FirewallMode) {
        sharedPreferences.edit().putString(MainActivity.KEY_FIREWALL_MODE, newMode.name).apply()
        setResult(RESULT_OK)
        if (FirewallUtils.loadFirewallEnabled(sharedPreferences)) {
            setModeBusy(true)
            CoroutineScope(Dispatchers.IO).launch {
                val ok = FirewallUtils.toggle(applicationContext, sharedPreferences, true)
                if (!ok) {
                    sharedPreferences.edit().putString(MainActivity.KEY_FIREWALL_MODE, oldMode.name).apply()
                    FirewallUtils.toggle(applicationContext, sharedPreferences, true)
                }
                withContext(Dispatchers.Main) {
                    if (isDestroyed) return@withContext
                    setModeBusy(false)
                    if (!ok) {
                        checkModeRadio(oldMode)
                        updateFirewallModeUI(oldMode)
                    }
                }
            }
        }

        TransitionManager.beginDelayedTransition(rootView, AutoTransition())
        updateFirewallModeUI(newMode)

        if (newMode == FirewallMode.WHITELIST) {
            showWhitelistInfoDialog()
        }
    }

    private fun setModeBusy(busy: Boolean) {
        radioGroupFirewallMode.alpha = if (busy) 0.5f else 1f
        radioGroupFirewallMode.children.forEach { it.isEnabled = !busy }
        firewallModeProgress.isVisible = busy
    }

    private fun checkModeRadio(mode: FirewallMode) {
        suppressFirewallModeListener = true
        radioGroupFirewallMode.check(modeRadios.entries.first { it.value == mode }.key)
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
            sharedPreferences.edit().putBoolean(MainActivity.KEY_SKIP_ENABLE_CONFIRM, true).apply()
        }
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btnFirewallModeDetails).setOnClickListener { showFirewallModeDetailsDialog() }

        radioGroupFirewallMode.setOnCheckedChangeListener { _, checkedId ->
            if (suppressFirewallModeListener) return@setOnCheckedChangeListener
            val newMode = modeRadios[checkedId] ?: return@setOnCheckedChangeListener
            val oldMode = FirewallUtils.firewallMode(sharedPreferences)
            val enabled = FirewallUtils.loadFirewallEnabled(sharedPreferences)
            val locked = enabled && !FirewallTargets.canSwitchWhileEnabled(oldMode, newMode, FirewallUtils.loadSelectedApps(this, sharedPreferences).isNotEmpty())
            if (locked) Toast.makeText(this, R.string.firewall_mode_change_disabled, Toast.LENGTH_LONG).show()
            if (locked || (enabled && !FirewallUtils.checkBackendReady(this))) {
                checkModeRadio(oldMode)
                return@setOnCheckedChangeListener
            }
            commitFirewallMode(oldMode, newMode)
        }

        switchSkipConfirm.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(MainActivity.KEY_SKIP_ENABLE_CONFIRM, isChecked).apply()
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

        switchRememberDeletedApps.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(MainActivity.KEY_REMEMBER_DISABLED_APPS, isChecked).apply()
        }

        switchAutoFirewallNewApps.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(MainActivity.KEY_AUTO_FIREWALL_NEW_APPS, isChecked).apply()
            setResult(RESULT_OK)
            AppMonitorService.sync(this)
        }

        switchKeepErrorAppsSelected.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(MainActivity.KEY_KEEP_ERROR_APPS_SELECTED, isChecked).apply()
        }

        bindVisibilitySwitch(
            switchShowSystemApps, MainActivity.KEY_SHOW_SYSTEM_APPS,
            R.string.show_system_apps, R.string.show_system_apps_uncheck_confirm, ::selectedSystemAppKeys
        )
        bindVisibilitySwitch(
            switchShowOtherProfiles, MainActivity.KEY_SHOW_OTHER_PROFILES,
            R.string.show_other_profiles, R.string.show_other_profiles_uncheck_confirm
        ) { selectedKeys().filterTo(mutableSetOf()) { AppKey.isSecondary(it) } }

        cardShowSystemApps.setOnClickListener {
            if (switchShowSystemApps.isEnabled) switchShowSystemApps.toggle()
        }
        cardShowOtherProfiles.setOnClickListener {
            if (switchShowOtherProfiles.isEnabled) switchShowOtherProfiles.toggle()
        }

        findViewById<View>(R.id.layoutScreenLockDelay).setOnClickListener { showScreenLockDelayDialog() }
        findViewById<View>(R.id.layoutAdbBroadcastUsage).setOnClickListener { showAdbBroadcastDialog() }

        listOf(switchSkipConfirm, switchSkipErrorDialog, switchKeepErrorAppsSelected, switchRememberDeletedApps, switchAutoFirewallNewApps)
            .forEach(::makeCardClickableForSwitch)
    }

    private fun screenLockDelay(): Int = sharedPreferences
        .getInt(MainActivity.KEY_SCREEN_LOCK_DELAY_SECONDS, MainActivity.DEFAULT_SCREEN_LOCK_DELAY_SECONDS)
        .coerceIn(2, 10)

    private fun updateScreenLockDelaySummary() {
        tvScreenLockDelayValue.text = getString(R.string.screen_lock_delay_value, screenLockDelay())
    }

    private fun showScreenLockDelayDialog() {
        val picker = NumberPicker(this).apply {
            minValue = 2
            maxValue = 10
            value = screenLockDelay()
            wrapSelectorWheel = false
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.screen_lock_delay_dialog_title)
            .setView(picker)
            .setPositiveButton(R.string.apply) { _, _ ->
                sharedPreferences.edit().putInt(MainActivity.KEY_SCREEN_LOCK_DELAY_SECONDS, picker.value).apply()
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
        val action = MainActivity.ACTION_FIREWALL_CONTROL
        val extraEnabled = MainActivity.EXTRA_FIREWALL_ENABLED
        val extraCsv = MainActivity.EXTRA_PACKAGES_CSV
        val component = "$packageName/${FirewallControlReceiver::class.java.name}"
        val control = "adb shell am broadcast -a $action -n $component --ez $extraEnabled"
        val csv = "--es $extraCsv \"com.example.app1,com.example.app2\""
        val installedComponent = "$packageName/${com.arslan.shizuwall.receivers.AppInstalledReceiver::class.java.name}"
        val installed = "-a ${MainActivity.ACTION_APP_INSTALLED} -n $installedComponent --es $extraCsv"

        val dialogView = layoutInflater.inflate(R.layout.dialog_adb_broadcast_usage, null)
        dialogView.findViewById<TextView>(R.id.tvBroadcastExtrasValue).text =
            getString(R.string.adb_broadcast_extras_summary, extraEnabled, extraCsv)

        val clipboardManager = getSystemService(ClipboardManager::class.java)

        fun row(textId: Int, copyId: Int, copyLabel: String, value: String? = null): TextView {
            val source = dialogView.findViewById<TextView>(textId)
            value?.let { source.text = it }
            dialogView.findViewById<View>(copyId).setOnClickListener {
                val text = source.text?.toString().orEmpty()
                if (text.isNotEmpty()) {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText(copyLabel, text))
                    Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
                }
            }
            return source
        }

        val selectableTextViews = listOf(
            row(R.id.tvBroadcastActionValue, R.id.btnCopyBroadcastAction, "adb_action", action),
            row(R.id.tvBroadcastComponentValue, R.id.btnCopyBroadcastComponent, "adb_component", component),
            row(R.id.tvCmdEnableSelected, R.id.btnCopyCmdEnableSelected, "adb_enable_selected", "$control true"),
            row(R.id.tvCmdDisableSelected, R.id.btnCopyCmdDisableSelected, "adb_disable_selected", "$control false"),
            row(R.id.tvCmdEnableCsv, R.id.btnCopyCmdEnableCsv, "adb_enable_csv", "$control true $csv"),
            row(R.id.tvCmdDisableCsv, R.id.btnCopyCmdDisableCsv, "adb_disable_csv", "$control false $csv"),
            row(R.id.tvCmdAppInstalled, R.id.btnCopyCmdAppInstalled, "adb_app_installed",
                "adb shell am broadcast $installed \"com.example.newapp\""),
            row(R.id.tvCmdAppInstalledProfile, R.id.btnCopyCmdAppInstalledProfile, "adb_app_installed_profile",
                "adb shell am broadcast --user 0 $installed \"150:com.example.newapp\""),
            row(R.id.tvDevCmdEnableFramework, R.id.btnCopyDevCmdEnableFramework, "dev_enable_firewall_framework"),
            row(R.id.tvDevCmdBlockPackage, R.id.btnCopyDevCmdBlockPackage, "dev_block_specific_app"),
            row(R.id.tvDevCmdUnblockPackage, R.id.btnCopyDevCmdUnblockPackage, "dev_unblock_specific_app"),
            row(R.id.tvDevCmdDisableFramework, R.id.btnCopyDevCmdDisableFramework, "dev_disable_firewall_framework")
        )

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
