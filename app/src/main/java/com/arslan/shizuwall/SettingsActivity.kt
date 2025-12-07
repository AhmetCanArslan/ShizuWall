package com.arslan.shizuwall

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var switchShowSystemApps: SwitchCompat
    private lateinit var switchMoveSelectedTop: SwitchCompat
    private lateinit var switchAdaptiveMode: SwitchCompat
    private lateinit var switchSkipConfirm: SwitchCompat
    private lateinit var switchSkipErrorDialog: SwitchCompat
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

    private lateinit var layoutAdbBroadcastUsage: LinearLayout // new

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

        val useDynamicColor = sharedPreferences.getBoolean(MainActivity.KEY_USE_DYNAMIC_COLOR, true)
        if (useDynamicColor) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        toolbar.menu.add(getString(R.string.reset_app_menu)).setOnMenuItemClickListener {
            showResetConfirmationDialog()
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
        tvVersion.text = "Version ${packageInfo.versionName}"

        // Apply custom font to all views
        applyFontToViews(findViewById(android.R.id.content))
    }

    private fun initializeViews() {
        switchShowSystemApps = findViewById(R.id.switchShowSystemApps)
        switchMoveSelectedTop = findViewById(R.id.switchMoveSelectedTop)
        switchAdaptiveMode = findViewById(R.id.switchAdaptiveMode)
        switchSkipConfirm = findViewById(R.id.switchSkipConfirm)
        switchSkipErrorDialog = findViewById(R.id.switchSkipErrorDialog)
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
        layoutAdbBroadcastUsage = findViewById(R.id.layoutAdbBroadcastUsage)
        // Auto-enable switch (new)
        switchAutoEnableOnShizukuStart = findViewById(R.id.switchAutoEnableOnShizukuStart)
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        switchShowSystemApps.isChecked = prefs.getBoolean(MainActivity.KEY_SHOW_SYSTEM_APPS, false)
        switchMoveSelectedTop.isChecked = prefs.getBoolean(MainActivity.KEY_MOVE_SELECTED_TOP, true)
        switchAdaptiveMode.isChecked = prefs.getBoolean(MainActivity.KEY_ADAPTIVE_MODE, false)
        switchSkipConfirm.isChecked = prefs.getBoolean("skip_enable_confirm", false)
        switchSkipErrorDialog.isChecked = prefs.getBoolean(MainActivity.KEY_SKIP_ERROR_DIALOG, false)
        switchKeepErrorAppsSelected.isChecked = prefs.getBoolean(MainActivity.KEY_KEEP_ERROR_APPS_SELECTED, false)
        
        // Enable/disable keep error apps option based on skip error dialog state
        switchKeepErrorAppsSelected.isEnabled = switchSkipErrorDialog.isChecked
        layoutKeepErrorApps.alpha = if (switchSkipErrorDialog.isChecked) 1.0f else 0.5f

        // Adaptive Mode dependency: if enabled, force "Skip Confirm" to true and disable it
        if (switchAdaptiveMode.isChecked) {
            switchSkipConfirm.isEnabled = false
            switchSkipConfirm.alpha = 0.5f
            if (!switchSkipConfirm.isChecked) {
                switchSkipConfirm.isChecked = true
                prefs.edit().putBoolean("skip_enable_confirm", true).apply()
            }
        }

        val currentFont = prefs.getString(MainActivity.KEY_SELECTED_FONT, "default") ?: "default"
        tvCurrentFont.text = if (currentFont == "ndot") "Ndot" else "Default"
        switchUseDynamicColor.isChecked = prefs.getBoolean(MainActivity.KEY_USE_DYNAMIC_COLOR, true)
        switchAutoEnableOnShizukuStart.isChecked = prefs.getBoolean(MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START, false)
    }

    private fun setupListeners() {
        val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        switchShowSystemApps.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_SHOW_SYSTEM_APPS, isChecked).apply()
            setResult(RESULT_OK)
        }

        switchMoveSelectedTop.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_MOVE_SELECTED_TOP, isChecked).apply()
            setResult(RESULT_OK)
        }

        switchAdaptiveMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_ADAPTIVE_MODE, isChecked).apply()
            setResult(RESULT_OK)

            if (isChecked) {
                // When Adaptive Mode is enabled, force Skip Confirm to ON and disable the switch
                if (!switchSkipConfirm.isChecked) {
                    switchSkipConfirm.isChecked = true
                }
                switchSkipConfirm.isEnabled = false
                switchSkipConfirm.alpha = 0.5f
            } else {
                // When Adaptive Mode is disabled, re-enable the switch (keep current checked state)
                switchSkipConfirm.isEnabled = true
                switchSkipConfirm.alpha = 1.0f
            }
        }

        switchSkipConfirm.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("skip_enable_confirm", isChecked).apply()
        }

        switchSkipErrorDialog.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_SKIP_ERROR_DIALOG, isChecked).apply()
            // Enable/disable the keep error apps option
            switchKeepErrorAppsSelected.isEnabled = isChecked
            layoutKeepErrorApps.alpha = if (isChecked) 1.0f else 0.5f
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
            showRestartNotice(getString(R.string.theme_changed_title), getString(R.string.theme_changed_message))
        }

        switchAutoEnableOnShizukuStart.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_AUTO_ENABLE_ON_SHIZUKU_START, isChecked).apply()
            setResult(RESULT_OK)
        }

        layoutAdbBroadcastUsage.setOnClickListener { showAdbBroadcastDialog() }

        // Make the whole card area toggle the corresponding switches when tapped
        makeCardClickableForSwitch(switchShowSystemApps)
        makeCardClickableForSwitch(switchMoveSelectedTop)
        makeCardClickableForSwitch(switchUseDynamicColor)
        makeCardClickableForSwitch(switchSkipConfirm)
        makeCardClickableForSwitch(switchAdaptiveMode)
        makeCardClickableForSwitch(switchSkipErrorDialog)
        makeCardClickableForSwitch(switchKeepErrorAppsSelected)
        makeCardClickableForSwitch(switchAutoEnableOnShizukuStart)
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
        applyFontToViews(dialogView)

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
            tvCurrentFont.text = if (fontKey == "ndot") "Ndot" else "Default"

            dialog.dismiss()
            showRestartNotice(getString(R.string.font_changed_title), getString(R.string.font_changed_message))
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
                val selected = prefs.getStringSet("selected_apps", emptySet())?.toList() ?: emptyList()
                val favorites = prefs.getStringSet(MainActivity.KEY_FAVORITE_APPS, emptySet())?.toList() ?: emptyList()
                val exportJson = org.json.JSONObject().apply {
                    put("version", 1)
                    put("selected", org.json.JSONArray(selected))
                    put("favorites", org.json.JSONArray(favorites))
                    put("show_system_apps", prefs.getBoolean(MainActivity.KEY_SHOW_SYSTEM_APPS, false))
                    put("adaptive_mode", prefs.getBoolean(MainActivity.KEY_ADAPTIVE_MODE, false))
                    put("skip_enable_confirm", prefs.getBoolean("skip_enable_confirm", false))
                    put("move_selected_top", prefs.getBoolean(MainActivity.KEY_MOVE_SELECTED_TOP, true))
                }
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(exportJson.toString().toByteArray(Charsets.UTF_8))
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

                val obj = org.json.JSONObject(content)
                val selectedJson = obj.optJSONArray("selected") ?: org.json.JSONArray()
                val selectedSet = mutableSetOf<String>()
                for (i in 0 until selectedJson.length()) {
                    val v = selectedJson.optString(i, null)
                    if (!v.isNullOrEmpty()) selectedSet.add(v)
                }

                // Ensure imported selection cannot include Shizuku packages
                val filteredSelectedSet = selectedSet.filterNot { isShizukuPackage(it) }.toMutableSet()

                val favoritesJson = obj.optJSONArray("favorites") ?: org.json.JSONArray()
                val favoritesSet = mutableSetOf<String>()
                for (i in 0 until favoritesJson.length()) {
                    val v = favoritesJson.optString(i, null)
                    if (!v.isNullOrEmpty()) favoritesSet.add(v)
                }

                val showSys = obj.optBoolean("show_system_apps", false)
                val adaptive = obj.optBoolean("adaptive_mode", false)
                val skipConfirm = obj.optBoolean("skip_enable_confirm", false)
                val moveTop = obj.optBoolean("move_selected_top", true)

                val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putStringSet("selected_apps", filteredSelectedSet)
                    putInt("selected_count", filteredSelectedSet.size)
                    putStringSet(MainActivity.KEY_FAVORITE_APPS, favoritesSet)
                    putBoolean(MainActivity.KEY_SHOW_SYSTEM_APPS, showSys)
                    putBoolean(MainActivity.KEY_ADAPTIVE_MODE, adaptive)
                    putBoolean("skip_enable_confirm", skipConfirm)
                    putBoolean(MainActivity.KEY_MOVE_SELECTED_TOP, moveTop)
                    apply()
                }

                withContext(Dispatchers.Main) {
                    loadSettings()
                    setResult(RESULT_OK)
                    Toast.makeText(this@SettingsActivity, getString(R.string.import_successful), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.import_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun applyFontToViews(view: View) {
        val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val savedFont = prefs.getString(MainActivity.KEY_SELECTED_FONT, "default") ?: "default"
        if (savedFont == "default") return

        val typeface = try {
            ResourcesCompat.getFont(this, R.font.ndot)
        } catch (e: Exception) {
            return
        }

        applyTypefaceRecursively(view, typeface)
    }

    // helper to detect shizuku packages (match exact privileged API package only)
    private fun isShizukuPackage(pkg: String): Boolean {
        return pkg == "moe.shizuku.privileged.api"
    }

    private fun applyTypefaceRecursively(view: View, typeface: Typeface?) {
        if (typeface == null) return

        when (view) {
            is TextView -> {
                view.typeface = typeface
            }
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    applyTypefaceRecursively(view.getChildAt(i), typeface)
                }
            }
        }
    }

    private fun showRestartNotice(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(getString(R.string.restart_now)) { _, _ ->
                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
                finish()
            }
            .setNegativeButton(getString(R.string.later), null)
            .show()
    }

    private fun showAdbBroadcastDialog() {
        // Compose the instructions using the same constants the app uses so examples stay correct.
        val pkg = "com.arslan.shizuwall"
        val action = MainActivity.ACTION_FIREWALL_CONTROL
        val extraEnabled = MainActivity.EXTRA_FIREWALL_ENABLED
        val extraCsv = MainActivity.EXTRA_PACKAGES_CSV
        val component = "$pkg/.FirewallControlReceiver"

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
            dialog.window?.decorView?.let { applyFontToViews(it) }
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
                            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                // 1. Disable global chain first (most important to restore internet)
                                runShizukuShellCommand("cmd connectivity set-chain3-enabled false")

                                // 2. Clean up individual package rules
                                for (pkg in allPackages) {
                                    runShizukuShellCommand("cmd connectivity set-package-networking-enabled true $pkg")
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

    private fun runShizukuShellCommand(command: String): Boolean {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            val process = method.invoke(
                null,
                arrayOf("/system/bin/sh", "-c", command),
                null,
                null
            ) as? ShizukuRemoteProcess ?: return false

            process.outputStream.close()
            process.inputStream.close()
            process.errorStream.close()
            val exitCode = process.waitFor()
            process.destroy()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
}
