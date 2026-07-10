package com.arslan.shizuwall.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.arslan.shizuwall.R
import com.arslan.shizuwall.WorkingMode
import com.arslan.shizuwall.shell.RootShellExecutor
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        enableEdgeToEdge()
        setContentView(R.layout.activity_settings_backend)

        rootView = findViewById(R.id.backendSettingsRoot)
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
        setupListeners()
    }

    private fun loadSettings() {
        switchAutoEnableOnShizukuStart.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START, false)
        switchApplyRootRulesAfterReboot.isChecked = sharedPreferences.getBoolean(MainActivity.KEY_APPLY_ROOT_RULES_AFTER_REBOOT, false)

        val workingModeName = sharedPreferences.getString(MainActivity.KEY_WORKING_MODE, WorkingMode.SHIZUKU.name)
        val workingMode = WorkingMode.fromName(workingModeName)
        when (workingMode) {
            WorkingMode.LADB -> radioGroupWorkingMode.check(R.id.radioLadbMode)
            WorkingMode.ROOT -> radioGroupWorkingMode.check(R.id.radioRootMode)
            else -> radioGroupWorkingMode.check(R.id.radioShizukuMode)
        }

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

            val currentMode = WorkingMode.fromName(sharedPreferences.getString(MainActivity.KEY_WORKING_MODE, WorkingMode.SHIZUKU.name))
            val mode = when (checkedId) {
                R.id.radioLadbMode -> WorkingMode.LADB
                R.id.radioRootMode -> WorkingMode.ROOT
                else -> WorkingMode.SHIZUKU
            }
            if (mode == currentMode) return@setOnCheckedChangeListener

            if (mode == WorkingMode.ROOT && !RootShellExecutor.hasRootAccess()) {
                showRootNotFoundDialog()
                suppressWorkingModeListener = true
                when (currentMode) {
                    WorkingMode.LADB -> radioGroupWorkingMode.check(R.id.radioLadbMode)
                    WorkingMode.ROOT -> radioGroupWorkingMode.check(R.id.radioRootMode)
                    else -> radioGroupWorkingMode.check(R.id.radioShizukuMode)
                }
                suppressWorkingModeListener = false
                return@setOnCheckedChangeListener
            }

            sharedPreferences.edit().putString(MainActivity.KEY_WORKING_MODE, mode.name).apply()
            setResult(RESULT_OK)

            TransitionManager.beginDelayedTransition(rootView, AutoTransition())
            updateWorkingModeDependentUi(mode, restoreAutoEnable = true)
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
}
