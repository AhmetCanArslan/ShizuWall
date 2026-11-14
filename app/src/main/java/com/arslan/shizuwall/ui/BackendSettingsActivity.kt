package com.arslan.shizuwall.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ScaleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.content.res.AppCompatResources
import com.arslan.shizuwall.R
import com.arslan.shizuwall.WorkingMode
import com.arslan.shizuwall.shell.RootShellExecutor
import com.arslan.shizuwall.utils.FirewallUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.roundToInt

class BackendSettingsActivity : BaseActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var rootView: ViewGroup

    private lateinit var radioGroupWorkingMode: RadioGroup
    private lateinit var radioShizukuMode: RadioButton
    private lateinit var radioLadbMode: RadioButton
    private lateinit var radioRootMode: RadioButton
    private lateinit var cardSetLadb: com.google.android.material.card.MaterialCardView
    private lateinit var layoutSetLadb: android.widget.LinearLayout
    private lateinit var switchAutoEnableOnShizukuStart: androidx.appcompat.widget.SwitchCompat
    private lateinit var cardAutoEnableOnShizukuStart: com.google.android.material.card.MaterialCardView
    private lateinit var switchApplyRootRulesAfterReboot: androidx.appcompat.widget.SwitchCompat
    private lateinit var cardApplyRootRulesAfterReboot: com.google.android.material.card.MaterialCardView

    private var autoEnablePreviousState: Boolean = false
    private var rootReapplyPreviousState: Boolean = false
    private var suppressWorkingModeListener = false

    private val modeRadios = mapOf(
        WorkingMode.SHIZUKU to R.id.radioShizukuMode,
        WorkingMode.LADB to R.id.radioLadbMode,
        WorkingMode.ROOT to R.id.radioRootMode,
        WorkingMode.VPN to R.id.radioVpnMode
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        enableEdgeToEdge()
        setContentView(R.layout.activity_settings_backend)

        rootView = findViewById(R.id.backendSettingsRoot)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupSettingsChrome(R.id.backendSettingsRoot, toolbar)

        radioGroupWorkingMode = findViewById(R.id.radioGroupWorkingMode)
        radioShizukuMode = findViewById(R.id.radioShizukuMode)
        radioLadbMode = findViewById(R.id.radioLadbMode)
        radioRootMode = findViewById(R.id.radioRootMode)
        cardSetLadb = findViewById(R.id.cardSetLadb)
        layoutSetLadb = findViewById(R.id.layoutSetLadb)
        switchAutoEnableOnShizukuStart = findViewById(R.id.switchAutoEnableOnShizukuStart)
        cardAutoEnableOnShizukuStart = findViewById(R.id.cardAutoEnableOnShizukuStart)
        switchApplyRootRulesAfterReboot = findViewById(R.id.switchApplyRootRulesAfterReboot)
        cardApplyRootRulesAfterReboot = findViewById(R.id.cardApplyRootRulesAfterReboot)

        loadSettings()
        setupModeIcons()
        setupListeners()

        if (savedInstanceState == null && intent.getBooleanExtra(CoachMarks.EXTRA_TOUR, false)) {
            CoachMarks.show(this, listOf(
                CoachMarks.Step({ radioGroupWorkingMode }, getString(R.string.onboarding_mode_selection_title), getString(R.string.onboarding_mode_selection_message), onTarget = {
                    startActivity(Intent(this, FirewallSettingsActivity::class.java).putExtra(CoachMarks.EXTRA_TOUR, true))
                    finish()
                })
            ))
        }
    }

    private fun loadSettings() {
        switchAutoEnableOnShizukuStart.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START, false)
        switchApplyRootRulesAfterReboot.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_APPLY_ROOT_RULES_AFTER_REBOOT, false)

        val workingMode = WorkingMode.of(this)
        checkWorkingModeRadio(workingMode)

        updateWorkingModeDependentUi(workingMode, restoreAutoEnable = false)
    }

    private fun setupListeners() {
        switchAutoEnableOnShizukuStart.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START, isChecked).apply()
            setResult(RESULT_OK)
        }

        switchApplyRootRulesAfterReboot.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(MainActivity.KEY_APPLY_ROOT_RULES_AFTER_REBOOT, isChecked).apply()
            setResult(RESULT_OK)
        }

        radioGroupWorkingMode.setOnCheckedChangeListener { _, checkedId ->
            if (suppressWorkingModeListener) return@setOnCheckedChangeListener

            val currentMode = WorkingMode.of(this)
            val mode = modeRadios.entries.first { it.value == checkedId }.key
            if (mode == currentMode) return@setOnCheckedChangeListener

            if (FirewallUtils.loadFirewallEnabled(sharedPreferences)) {
                Toast.makeText(this, R.string.app_visibility_change_disabled, Toast.LENGTH_LONG).show()
                checkWorkingModeRadio(currentMode)
                return@setOnCheckedChangeListener
            }

            if (mode == WorkingMode.ROOT && !RootShellExecutor.hasRootAccess()) {
                showRootNotFoundDialog()
                checkWorkingModeRadio(currentMode)
                return@setOnCheckedChangeListener
            }

            commitWorkingMode(mode)
        }

        layoutSetLadb.setOnClickListener {
            if (!radioLadbMode.isChecked) {
                Toast.makeText(this, getString(R.string.working_mode_select_ladb_prompt), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                startActivity(Intent(this, com.arslan.shizuwall.LadbSetupActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.open_ladb_setup), Toast.LENGTH_SHORT).show()
            }
        }

        makeCardClickableForSwitch(switchAutoEnableOnShizukuStart)
        makeCardClickableForSwitch(switchApplyRootRulesAfterReboot)
    }

    private fun setupModeIcons() {
        val iconSize = (24 * resources.displayMetrics.density).roundToInt()
        modeRadios.forEach { (mode, id) ->
            val radio = findViewById<RadioButton>(id)
            val radioDrawable = radio.buttonDrawable ?: return@forEach
            val dx = ((radioDrawable.intrinsicWidth - iconSize) / 2).coerceAtLeast(0)
            val dy = ((radioDrawable.intrinsicHeight - iconSize) / 2).coerceAtLeast(0)
            val icon = AppCompatResources.getDrawable(this, mode.icon)!!.mutate()
            val sizedIcon = LayerDrawable(arrayOf(icon)).apply {
                setLayerSize(0, iconSize, iconSize)
                setLayerInset(0, dx, dy, dx, dy)
            }
            val indicator = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_checked), ScaleDrawable(sizedIcon, Gravity.CENTER, 1f, 1f))
                addState(intArrayOf(), radioDrawable)
                setEnterFadeDuration(MORPH_DURATION)
                setExitFadeDuration(MORPH_DURATION)
                level = MAX_LEVEL
            }
            radio.buttonDrawable = indicator
            radio.setOnCheckedChangeListener { _, isChecked ->
                if (!isChecked) return@setOnCheckedChangeListener
                ValueAnimator.ofInt(0, MAX_LEVEL).apply {
                    duration = MORPH_DURATION.toLong()
                    interpolator = OvershootInterpolator()
                    addUpdateListener { indicator.level = it.animatedValue as Int }
                }.start()
            }
        }
    }

    private fun checkWorkingModeRadio(mode: WorkingMode) {
        suppressWorkingModeListener = true
        radioGroupWorkingMode.check(modeRadios.getValue(mode))
        suppressWorkingModeListener = false
    }

    private fun commitWorkingMode(mode: WorkingMode) {
        FirewallUtils.setWorkingMode(sharedPreferences, mode)
        if (mode != WorkingMode.ROOT) RootShellExecutor.shutdown()
        if (mode == WorkingMode.VPN) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.working_mode_vpn)
                .setMessage(R.string.vpn_mode_info)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
        setResult(RESULT_OK)

        TransitionManager.beginDelayedTransition(rootView, AutoTransition())
        updateWorkingModeDependentUi(mode, restoreAutoEnable = true)
    }

    private fun showRootNotFoundDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.working_mode_root)
            .setMessage(R.string.root_not_found_message)
            .setPositiveButton(R.string.ok, null)
            .setCancelable(true)
            .show()
    }

    private fun updateWorkingModeDependentUi(mode: WorkingMode, restoreAutoEnable: Boolean) {
        val isLadb = mode == WorkingMode.LADB
        val isShizuku = mode == WorkingMode.SHIZUKU
        val isRoot = mode == WorkingMode.ROOT

        cardSetLadb.visibility = if (isLadb) View.VISIBLE else View.GONE
        cardAutoEnableOnShizukuStart.visibility = if (isShizuku) View.VISIBLE else View.GONE
        switchAutoEnableOnShizukuStart.isEnabled = isShizuku
        cardApplyRootRulesAfterReboot.visibility = if (isRoot) View.VISIBLE else View.GONE
        switchApplyRootRulesAfterReboot.isEnabled = isRoot

        if (!isShizuku) {
            autoEnablePreviousState = switchAutoEnableOnShizukuStart.isChecked
            if (switchAutoEnableOnShizukuStart.isChecked) {
                switchAutoEnableOnShizukuStart.isChecked = false
                sharedPreferences.edit().putBoolean(MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START, false).apply()
            }
        } else if (restoreAutoEnable && autoEnablePreviousState) {
            switchAutoEnableOnShizukuStart.isChecked = true
            sharedPreferences.edit().putBoolean(MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START, true).apply()
        }

        if (!isRoot) {
            rootReapplyPreviousState = switchApplyRootRulesAfterReboot.isChecked
            if (switchApplyRootRulesAfterReboot.isChecked) {
                switchApplyRootRulesAfterReboot.isChecked = false
                sharedPreferences.edit().putBoolean(MainActivity.KEY_APPLY_ROOT_RULES_AFTER_REBOOT, false).apply()
            }
        } else if (restoreAutoEnable && rootReapplyPreviousState) {
            switchApplyRootRulesAfterReboot.isChecked = true
            sharedPreferences.edit().putBoolean(MainActivity.KEY_APPLY_ROOT_RULES_AFTER_REBOOT, true).apply()
        }
    }

    companion object {
        private const val MORPH_DURATION = 250
        private const val MAX_LEVEL = 10000
    }
}
