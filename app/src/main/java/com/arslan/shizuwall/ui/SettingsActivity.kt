package com.arslan.shizuwall.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.arslan.shizuwall.shizuku.ShizukuSetupActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Typeface
import android.os.Build
import androidx.appcompat.widget.SwitchCompat
import com.arslan.shizuwall.R
import com.arslan.shizuwall.ladb.LadbManager
import com.arslan.shizuwall.services.AppMonitorService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku

class SettingsActivity : BaseActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var switchMoveSelectedTop: SwitchCompat
    private lateinit var switchAdaptiveMode: SwitchCompat
    private lateinit var switchSkipConfirm: SwitchCompat
    private lateinit var switchSkipErrorDialog: SwitchCompat
    private lateinit var cardKeepErrorApps: com.google.android.material.card.MaterialCardView
    private lateinit var layoutKeepErrorApps: LinearLayout
    private lateinit var switchKeepErrorAppsSelected: SwitchCompat
    private lateinit var layoutChangeFont: LinearLayout
    private lateinit var tvCurrentFont: TextView
    private lateinit var btnExport: LinearLayout
    private lateinit var btnImport: LinearLayout
    private lateinit var btnDonate: LinearLayout
    private lateinit var btnGithub: LinearLayout
    private lateinit var tvVersion: TextView
    private lateinit var switchUseDynamicColor: SwitchCompat
    private lateinit var switchAutoEnableOnShizukuStart: SwitchCompat
    private lateinit var cardAutoEnableOnShizukuStart: com.google.android.material.card.MaterialCardView
    private lateinit var switchAppMonitor: SwitchCompat

    private lateinit var cardAdbBroadcastUsage: com.google.android.material.card.MaterialCardView
    private lateinit var layoutAdbBroadcastUsage: LinearLayout // new
    private lateinit var radioGroupWorkingMode: RadioGroup
    private lateinit var radioShizukuMode: RadioButton
    private lateinit var radioLadbMode: RadioButton
    private lateinit var cardSetLadb: com.google.android.material.card.MaterialCardView
    private lateinit var layoutSetLadb: LinearLayout
    private lateinit var cardSkipConfirm: com.google.android.material.card.MaterialCardView
    private var autoEnablePreviousState: Boolean = false  // Store previous state before disabling

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { lifecycleScope.launch { exportToUri(it) } }
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uris: Uri? ->
        uris?.let { lifecycleScope.launch { importFromUri(it) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        toolbar.menu.add(getString(R.string.reset_app_menu)).setOnMenuItemClickListener {
            showResetConfirmationDialog()
            true
        }

        // Add Shizuku Setup Guide to overflow menu
        toolbar.menu.add(getString(R.string.shizuku_setup_guide)).setOnMenuItemClickListener {
            startActivity(android.content.Intent(this, ShizukuSetupActivity::class.java))
            true
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply top margin to toolbar to account for status bar
            val toolbarParams = toolbar.layoutParams as ViewGroup.MarginLayoutParams
            toolbarParams.topMargin = systemBars.top
            toolbar.layoutParams = toolbarParams
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)

            insets
        }

        initializeViews()
        loadSettings()
        setupListeners()

        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        tvVersion.text = getString(R.string.version_format, packageInfo.versionName)
    }

    private fun initializeViews() {
        switchMoveSelectedTop = findViewById(R.id.switchMoveSelectedTop)
        switchAdaptiveMode = findViewById(R.id.switchAdaptiveMode)
        cardSkipConfirm = findViewById(R.id.cardSkipConfirm)
        switchSkipConfirm = findViewById(R.id.switchSkipConfirm)
        switchSkipErrorDialog = findViewById(R.id.switchSkipErrorDialog)
        cardKeepErrorApps = findViewById(R.id.cardKeepErrorApps)
        layoutKeepErrorApps = findViewById(R.id.layoutKeepErrorApps)
        switchKeepErrorAppsSelected = findViewById(R.id.switchKeepErrorAppsSelected)
        layoutChangeFont = findViewById(R.id.layoutChangeFont)
        tvCurrentFont = findViewById(R.id.tvCurrentFont)
        btnExport = findViewById(R.id.btnExport)
        btnImport = findViewById(R.id.btnImport)
        btnDonate = findViewById(R.id.btnDonate)
        btnGithub = findViewById(R.id.btnGithub)
        tvVersion = findViewById(R.id.tvVersion)
        switchUseDynamicColor = findViewById(R.id.switchUseDynamicColor)

        // new: bind XML item
        cardAdbBroadcastUsage = findViewById(R.id.cardAdbBroadcastUsage)
        layoutAdbBroadcastUsage = findViewById(R.id.layoutAdbBroadcastUsage)
        // Working mode controls
        radioGroupWorkingMode = findViewById(R.id.radioGroupWorkingMode)
        radioShizukuMode = findViewById(R.id.radioShizukuMode)
        radioLadbMode = findViewById(R.id.radioLadbMode)
        cardSetLadb = findViewById(R.id.cardSetLadb)
        layoutSetLadb = findViewById(R.id.layoutSetLadb)
        switchAppMonitor = findViewById(R.id.switchAppMonitor)
        // Auto-enable switch (new)
        switchAutoEnableOnShizukuStart = findViewById(R.id.switchAutoEnableOnShizukuStart)
        cardAutoEnableOnShizukuStart = findViewById(R.id.cardAutoEnableOnShizukuStart)
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        switchMoveSelectedTop.isChecked = prefs.getBoolean(MainActivity.KEY_MOVE_SELECTED_TOP, true)
        switchAdaptiveMode.isChecked = prefs.getBoolean(MainActivity.KEY_ADAPTIVE_MODE, false)
        switchSkipConfirm.isChecked = prefs.getBoolean("skip_enable_confirm", false)
        switchSkipErrorDialog.isChecked = prefs.getBoolean(MainActivity.KEY_SKIP_ERROR_DIALOG, false)
        switchKeepErrorAppsSelected.isChecked = prefs.getBoolean(MainActivity.KEY_KEEP_ERROR_APPS_SELECTED, false)
        
        // Show/hide keep error apps option based on skip error dialog state
        cardKeepErrorApps.visibility = if (switchSkipErrorDialog.isChecked) View.VISIBLE else View.GONE

        // Adaptive Mode dependency: if enabled, hide "Skip Confirm"
        if (switchAdaptiveMode.isChecked) {
            cardSkipConfirm.visibility = View.GONE
            if (!switchSkipConfirm.isChecked) {
                switchSkipConfirm.isChecked = true
                prefs.edit().putBoolean("skip_enable_confirm", true).apply()
            }
        } else {
            cardSkipConfirm.visibility = View.VISIBLE
        }

        val currentFont = prefs.getString(MainActivity.KEY_SELECTED_FONT, "default") ?: "default"
        tvCurrentFont.text = if (currentFont == "ndot") getString(R.string.font_ndot) else getString(R.string.font_default)
        switchUseDynamicColor.isChecked = prefs.getBoolean(MainActivity.KEY_USE_DYNAMIC_COLOR, true)
        switchAutoEnableOnShizukuStart.isChecked = prefs.getBoolean(MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START, false)
        switchAppMonitor.isChecked = prefs.getBoolean(MainActivity.KEY_APP_MONITOR_ENABLED, false)

        // Load working mode
        val workingModeName = prefs.getString(MainActivity.KEY_WORKING_MODE, com.arslan.shizuwall.WorkingMode.SHIZUKU.name)
        when (com.arslan.shizuwall.WorkingMode.fromName(workingModeName)) {
            com.arslan.shizuwall.WorkingMode.LADB -> radioGroupWorkingMode.check(R.id.radioLadbMode)
            else -> radioGroupWorkingMode.check(R.id.radioShizukuMode)
        }

        if (com.arslan.shizuwall.BuildConfig.FLAVOR == "fdroid") {
            radioLadbMode.isEnabled = false
            radioLadbMode.alpha = 0.5f
            val notSupportedText = getString(R.string.ladb_not_supported_fdroid)
            if (!radioLadbMode.text.toString().contains(notSupportedText)) {
                radioLadbMode.text = "${radioLadbMode.text}$notSupportedText"
            }
            if (radioLadbMode.isChecked) {
                radioGroupWorkingMode.check(R.id.radioShizukuMode)
                prefs.edit().putString(MainActivity.KEY_WORKING_MODE, com.arslan.shizuwall.WorkingMode.SHIZUKU.name).apply()
            }
        }
        // Show/hide LADB card if not selected
        val ladbSelected = radioLadbMode.isChecked
        cardSetLadb.visibility = if (ladbSelected) View.VISIBLE else View.GONE
        
        // Hide auto-enable firewall card when LADB mode is selected
        cardAutoEnableOnShizukuStart.visibility = if (ladbSelected) View.GONE else View.VISIBLE
        switchAutoEnableOnShizukuStart.isEnabled = !ladbSelected
    }

    private fun setupListeners() {
        val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        switchMoveSelectedTop.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_MOVE_SELECTED_TOP, isChecked).apply()
            setResult(RESULT_OK)
        }

        switchAdaptiveMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_ADAPTIVE_MODE, isChecked).apply()
            setResult(RESULT_OK)

            TransitionManager.beginDelayedTransition(findViewById(R.id.settingsRoot), AutoTransition())
            if (isChecked) {
                // When Adaptive Mode is enabled, force Skip Confirm to ON and hide the card
                if (!switchSkipConfirm.isChecked) {
                    switchSkipConfirm.isChecked = true
                }
                cardSkipConfirm.visibility = View.GONE
            } else {
                // When Adaptive Mode is disabled, show the card
                cardSkipConfirm.visibility = View.VISIBLE
            }
        }

        switchSkipConfirm.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("skip_enable_confirm", isChecked).apply()
        }

        switchSkipErrorDialog.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_SKIP_ERROR_DIALOG, isChecked).apply()
            
            TransitionManager.beginDelayedTransition(findViewById(R.id.settingsRoot), AutoTransition())
            // Show/hide the keep error apps option
            cardKeepErrorApps.visibility = if (isChecked) View.VISIBLE else View.GONE
            
            // If disabling skip error dialog, also disable keep error apps selected
            if (!isChecked) {
                switchKeepErrorAppsSelected.isChecked = false
                prefs.edit().putBoolean(MainActivity.KEY_KEEP_ERROR_APPS_SELECTED, false).apply()
            }
        }

        switchKeepErrorAppsSelected.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_KEEP_ERROR_APPS_SELECTED, isChecked).apply()
        }

        layoutChangeFont.setOnClickListener {
            showFontSelectorDialog()
        }

        btnExport.setOnClickListener {
            createDocumentLauncher.launch("shizuwall_settings")
        }

        btnImport.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
        }

        btnDonate.setOnClickListener {
            val url = getString(R.string.buymeacoffee_url)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        btnGithub.setOnClickListener {
            val url = getString(R.string.github_url)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        switchUseDynamicColor.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit()
                .putBoolean(MainActivity.KEY_USE_DYNAMIC_COLOR, isChecked)
                .apply()
            recreateWithAnimation()
        }

        switchAutoEnableOnShizukuStart.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START, isChecked).apply()
            setResult(RESULT_OK)
        }

        switchAppMonitor.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1002)
                }
            }
            prefs.edit().putBoolean(MainActivity.KEY_APP_MONITOR_ENABLED, isChecked).apply()
            val intent = Intent(this, AppMonitorService::class.java)
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } else {
                stopService(intent)
            }
        }

        radioGroupWorkingMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.radioLadbMode) com.arslan.shizuwall.WorkingMode.LADB else com.arslan.shizuwall.WorkingMode.SHIZUKU
            prefs.edit().putString(MainActivity.KEY_WORKING_MODE, mode.name).apply()
            setResult(RESULT_OK)
            
            TransitionManager.beginDelayedTransition(findViewById(R.id.settingsRoot), AutoTransition())
            // Update UI affordance for LADB setup
            val isLadb = mode == com.arslan.shizuwall.WorkingMode.LADB
            cardSetLadb.visibility = if (isLadb) View.VISIBLE else View.GONE

            if (isLadb) {
                autoEnablePreviousState = switchAutoEnableOnShizukuStart.isChecked
                cardAutoEnableOnShizukuStart.visibility = View.GONE
                switchAutoEnableOnShizukuStart.isEnabled = false
                if (switchAutoEnableOnShizukuStart.isChecked) {
                    switchAutoEnableOnShizukuStart.isChecked = false
                    prefs.edit().putBoolean(MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START, false).apply()
                }
            } else {
                cardAutoEnableOnShizukuStart.visibility = View.VISIBLE
                switchAutoEnableOnShizukuStart.isEnabled = true
                if (autoEnablePreviousState) {
                    switchAutoEnableOnShizukuStart.isChecked = true
                    prefs.edit().putBoolean(MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START, true).apply()
                }
            }
        }

        layoutSetLadb.setOnClickListener {
            // Only allow opening when LADB mode is selected
            if (!radioLadbMode.isChecked) {
                Toast.makeText(this, getString(R.string.working_mode_select_ladb_prompt), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val intent = android.content.Intent(this, com.arslan.shizuwall.LadbSetupActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.open_ladb_setup), Toast.LENGTH_SHORT).show()
            }
        }

        layoutAdbBroadcastUsage.setOnClickListener { showAdbBroadcastDialog() }

        // Make the whole card area toggle the corresponding switches when tapped
        makeCardClickableForSwitch(switchMoveSelectedTop)
        makeCardClickableForSwitch(switchUseDynamicColor)
        makeCardClickableForSwitch(switchSkipConfirm)
        makeCardClickableForSwitch(switchAdaptiveMode)
        makeCardClickableForSwitch(switchSkipErrorDialog)
        makeCardClickableForSwitch(switchKeepErrorAppsSelected)
        makeCardClickableForSwitch(switchAutoEnableOnShizukuStart)
        makeCardClickableForSwitch(switchAppMonitor)
    }

   
    private fun makeCardClickableForSwitch(switch: SwitchCompat) {
        try {
            val parent = switch.parent as? View ?: return

            // Apply ripple/selectable background so the row gives visual feedback when tapped
            val typedValue = TypedValue()
            if (theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)) {
                parent.setBackgroundResource(typedValue.resourceId)
            }

            parent.isClickable = true
            parent.isFocusable = true

            parent.setOnClickListener {
                // Only toggle if the switch is enabled (respect dependencies)
                if (switch.isEnabled) {
                    // Flip checked state; this will trigger the switch's change listener
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

        // Apply font to dialog views

        val radioGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.fontRadioGroup)
        val btnApply = dialogView.findViewById<MaterialButton>(R.id.btnApplyFont)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelFont)

        val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val currentFont = prefs.getString(MainActivity.KEY_SELECTED_FONT, "default") ?: "default"
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

            prefs.edit().putString(MainActivity.KEY_SELECTED_FONT, fontKey).apply()
            tvCurrentFont.text = if (fontKey == "ndot") getString(R.string.font_ndot) else getString(R.string.font_default)

            dialog.dismiss()
            recreateWithAnimation()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private suspend fun exportToUri(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                val exportJson = JSONObject().apply {
                    put("version", 2)
                    put("exported_at", System.currentTimeMillis())

                    // Main data lists
                    put("selected", JSONArray(prefs.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet())?.toList() ?: emptyList<String>()))
                    put("favorites", JSONArray(prefs.getStringSet(MainActivity.KEY_FAVORITE_APPS, emptySet())?.toList() ?: emptyList<String>()))

                    // All relevant settings
                    val keys = listOf(
                        MainActivity.KEY_SHOW_SYSTEM_APPS,
                        MainActivity.KEY_ADAPTIVE_MODE,
                        MainActivity.KEY_SKIP_ENABLE_CONFIRM,
                        MainActivity.KEY_MOVE_SELECTED_TOP,
                        MainActivity.KEY_SKIP_ERROR_DIALOG,
                        MainActivity.KEY_KEEP_ERROR_APPS_SELECTED,
                        MainActivity.KEY_USE_DYNAMIC_COLOR,
                        MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START,
                        MainActivity.KEY_APP_MONITOR_ENABLED,
                        MainActivity.KEY_SKIP_ANDROID11_INFO,
                        MainActivity.KEY_SELECTED_FONT,
                        MainActivity.KEY_WORKING_MODE,
                        MainActivity.KEY_SORT_ORDER,
                        MainActivity.KEY_SHOW_SETUP_PROMPT
                    )

                    for (key in keys) {
                        if (prefs.contains(key)) {
                            put(key, prefs.all[key])
                        }
                    }

                    // Export LADB config
                    val ladbPrefs = getSharedPreferences(LadbManager.PREFS_NAME, Context.MODE_PRIVATE)
                    val ladbJson = JSONObject().apply {
                        put(LadbManager.KEY_HOST, ladbPrefs.getString(LadbManager.KEY_HOST, null))
                        // We skip ephemeral ports as they change on every start/reboot
                        put(LadbManager.KEY_IS_PAIRED, ladbPrefs.getBoolean(LadbManager.KEY_IS_PAIRED, false))
                    }
                    put("ladb_config", ladbJson)
                }

                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(exportJson.toString(2).toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: throw IllegalStateException("Unable to open output stream")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.export_successful), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.export_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun importFromUri(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val content = contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                } ?: throw IllegalStateException("Unable to open input stream")

                val obj = JSONObject(content)
                val version = obj.optInt("version", 1)
                val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                val editor = prefs.edit()

                // 1. Handle Selected Apps (legacy key "selected")
                val selectedKey = if (obj.has("selected")) "selected" else MainActivity.KEY_SELECTED_APPS
                val selectedJson = obj.optJSONArray(selectedKey)
                if (selectedJson != null) {
                    val selectedSet = mutableSetOf<String>()
                    for (i in 0 until selectedJson.length()) {
                        val v = selectedJson.optString(i, null)
                        if (!v.isNullOrEmpty()) selectedSet.add(v)
                    }
                    val filteredSelectedSet = selectedSet.filterNot { isShizukuPackage(it) }.toSet()
                    editor.putStringSet(MainActivity.KEY_SELECTED_APPS, filteredSelectedSet)
                    editor.putInt(MainActivity.KEY_SELECTED_COUNT, filteredSelectedSet.size)
                }

                // 2. Handle Favorites (legacy key "favorites")
                val favoritesKey = if (obj.has("favorites")) "favorites" else MainActivity.KEY_FAVORITE_APPS
                val favoritesJson = obj.optJSONArray(favoritesKey)
                if (favoritesJson != null) {
                    val favoritesSet = mutableSetOf<String>()
                    for (i in 0 until favoritesJson.length()) {
                        val v = favoritesJson.optString(i, null)
                        if (!v.isNullOrEmpty()) favoritesSet.add(v)
                    }
                    editor.putStringSet(MainActivity.KEY_FAVORITE_APPS, favoritesSet)
                }

                // 3. Handle All Other Settings
                val keys = listOf(
                    MainActivity.KEY_SHOW_SYSTEM_APPS,
                    MainActivity.KEY_ADAPTIVE_MODE,
                    MainActivity.KEY_SKIP_ENABLE_CONFIRM,
                    MainActivity.KEY_MOVE_SELECTED_TOP,
                    MainActivity.KEY_SKIP_ERROR_DIALOG,
                    MainActivity.KEY_KEEP_ERROR_APPS_SELECTED,
                    MainActivity.KEY_USE_DYNAMIC_COLOR,
                    MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START,
                    MainActivity.KEY_APP_MONITOR_ENABLED,
                    MainActivity.KEY_SKIP_ANDROID11_INFO,
                    MainActivity.KEY_SELECTED_FONT,
                    MainActivity.KEY_WORKING_MODE,
                    MainActivity.KEY_SORT_ORDER,
                    MainActivity.KEY_SHOW_SETUP_PROMPT
                )

                for (key in keys) {
                    if (obj.has(key)) {
                        when (val value = obj.get(key)) {
                            is Boolean -> editor.putBoolean(key, value)
                            is String -> editor.putString(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                        }
                    } else if (version == 1) {
                        // Legacy v1 used slightly different names for some settings in JSON
                        // but most were matched to MainActivity constants already.
                        // We handled 'selected' and 'favorites' above.
                    }
                }

                editor.apply()

                // 4. Handle LADB config
                val ladbObj = obj.optJSONObject("ladb_config")
                if (ladbObj != null) {
                    val ladbEditor = getSharedPreferences(LadbManager.PREFS_NAME, Context.MODE_PRIVATE).edit()
                    if (ladbObj.has(LadbManager.KEY_HOST)) {
                        ladbEditor.putString(LadbManager.KEY_HOST, ladbObj.optString(LadbManager.KEY_HOST))
                    }
                    if (ladbObj.has(LadbManager.KEY_IS_PAIRED)) {
                        ladbEditor.putBoolean(LadbManager.KEY_IS_PAIRED, ladbObj.optBoolean(LadbManager.KEY_IS_PAIRED))
                    }
                    ladbEditor.apply()
                }

                // 5. Update Runtime State (App Monitor Service)
                val isMonitorEnabled = prefs.getBoolean(MainActivity.KEY_APP_MONITOR_ENABLED, false)
                val monitorIntent = Intent(this@SettingsActivity, AppMonitorService::class.java)
                try {
                    if (isMonitorEnabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(monitorIntent)
                        } else {
                            startService(monitorIntent)
                        }
                    } else {
                        stopService(monitorIntent)
                    }
                } catch (e: Exception) {
                    // Ignore service start failures during import
                }

                // 6. Mark onboarding as complete
                getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                    .putBoolean("onboarding_complete", true)
                    .apply()

                withContext(Dispatchers.Main) {
                    loadSettings()
                    setResult(RESULT_OK)
                    Toast.makeText(this@SettingsActivity, getString(R.string.import_successful), Toast.LENGTH_SHORT).show()
                    recreateWithAnimation()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.import_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // helper to detect shizuku packages (match exact privileged API package only)
    private fun isShizukuPackage(pkg: String): Boolean {
        return pkg == "moe.shizuku.privileged.api"
    }

    private fun showAdbBroadcastDialog() {
        // Compose the instructions using the same constants the app uses so examples stay correct.
        val pkg = "com.arslan.shizuwall"
        val action = MainActivity.ACTION_FIREWALL_CONTROL
        val extraEnabled = MainActivity.EXTRA_FIREWALL_ENABLED
        val extraCsv = MainActivity.EXTRA_PACKAGES_CSV
        val component = "$pkg/.receivers.FirewallControlReceiver"

        val adbUsageText = getString(
            R.string.adb_broadcast_usage_text,
            action,
            component,
            extraEnabled,
            extraCsv
        )

        val tv = TextView(this).apply {
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt()
            )
            // Ensure text is set and TextView spans dialog width
            text = adbUsageText
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val scroll = ScrollView(this).apply {
            addView(tv)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.adb_broadcast_usage_title))
            .setView(scroll)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun showResetConfirmationDialog() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.reset_app_title))
            .setMessage(getString(R.string.reset_app_message))
            .setPositiveButton(getString(R.string.reset)) { _, _ ->
                resetApp()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
        }
        dialog.show()
    }

    private fun resetApp() {
        lifecycleScope.launch {
            try {
                val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

                // If firewall is enabled, try to disable it first to revert system changes
                if (prefs.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)) {
                    val activePackages = prefs.getStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet()) ?: emptySet()
                    val selectedApps = prefs.getStringSet("selected_apps", emptySet()) ?: emptySet()
                    // Combine lists to ensure we catch everything
                    val allPackages = activePackages + selectedApps

                    var cleanupPerformed = false
                    withContext(Dispatchers.IO) {
                        try {
                            val prefsLocal = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                            val mode = prefsLocal.getString(MainActivity.KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"

                            val canPerformCleanup = if (mode == "SHIZUKU") {
                                try {
                                    Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
                                } catch (t: Throwable) {
                                    false
                                }
                            } else {
                                com.arslan.shizuwall.ladb.LadbManager.getInstance(this@SettingsActivity).isConnected()
                            }

                            if (canPerformCleanup) {
                                // 1. Disable global chain first (most important to restore internet)
                                com.arslan.shizuwall.shell.ShellExecutorBlocking.runBlockingSuccess(this@SettingsActivity, "cmd connectivity set-chain3-enabled false")

                                // 2. Clean up individual package rules
                                for (pkg in allPackages) {
                                    com.arslan.shizuwall.shell.ShellExecutorBlocking.runBlockingSuccess(this@SettingsActivity, "cmd connectivity set-package-networking-enabled true $pkg")
                                }
                                cleanupPerformed = true
                            }
                        } catch (e: Exception) {
                            // Proceed with reset even if cleanup fails
                        }
                    }

                    if (!cleanupPerformed) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SettingsActivity, getString(R.string.firewall_cleanup_warning), Toast.LENGTH_LONG).show()
                        }
                    }
                }

                // Clear main prefs
                prefs.edit().clear().commit()

                // Clear onboarding prefs
                getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().clear().commit()

                // Clear device protected prefs if applicable
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    try {
                        createDeviceProtectedStorageContext().getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE).edit().clear().commit()
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                Toast.makeText(this@SettingsActivity, getString(R.string.app_reset_complete), Toast.LENGTH_SHORT).show()

                // Close all activities
                finishAffinity()

                // Kill process to ensure fresh start
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(0)
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, getString(R.string.reset_app_failed, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }


}
