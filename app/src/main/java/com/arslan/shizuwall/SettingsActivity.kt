package com.arslan.shizuwall

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
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

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var switchShowSystemApps: SwitchMaterial
    private lateinit var switchMoveSelectedTop: SwitchMaterial
    private lateinit var switchSkipConfirm: SwitchMaterial
    private lateinit var layoutChangeFont: LinearLayout
    private lateinit var tvCurrentFont: TextView
    private lateinit var btnExport: MaterialButton
    private lateinit var btnImport: MaterialButton
    private lateinit var btnDonate: MaterialButton
    private lateinit var switchUseDynamicColor: SwitchMaterial

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
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply top margin to toolbar to account for status bar
            val toolbarParams = toolbar.layoutParams as ViewGroup.MarginLayoutParams
            toolbarParams.topMargin = systemBars.top
            toolbar.layoutParams = toolbarParams
            insets
        }

        initializeViews()
        loadSettings()
        setupListeners()
        
        // Apply custom font to all views
        applyFontToViews(findViewById(android.R.id.content))
    }

    private fun initializeViews() {
        switchShowSystemApps = findViewById(R.id.switchShowSystemApps)
        switchMoveSelectedTop = findViewById(R.id.switchMoveSelectedTop)
        switchSkipConfirm = findViewById(R.id.switchSkipConfirm)
        layoutChangeFont = findViewById(R.id.layoutChangeFont)
        tvCurrentFont = findViewById(R.id.tvCurrentFont)
        btnExport = findViewById(R.id.btnExport)
        btnImport = findViewById(R.id.btnImport)
        btnDonate = findViewById(R.id.btnDonate)
        switchUseDynamicColor = findViewById(R.id.switchUseDynamicColor)
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        
        switchShowSystemApps.isChecked = prefs.getBoolean(MainActivity.KEY_SHOW_SYSTEM_APPS, false)
        switchMoveSelectedTop.isChecked = prefs.getBoolean(MainActivity.KEY_MOVE_SELECTED_TOP, true)
        switchSkipConfirm.isChecked = prefs.getBoolean("skip_enable_confirm", false)
        
        val currentFont = prefs.getString(MainActivity.KEY_SELECTED_FONT, "default") ?: "default"
        tvCurrentFont.text = if (currentFont == "ndot") "Ndot" else "Default"
        switchUseDynamicColor.isChecked = prefs.getBoolean(MainActivity.KEY_USE_DYNAMIC_COLOR, true)
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

        switchSkipConfirm.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("skip_enable_confirm", isChecked).apply()
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

        switchUseDynamicColor.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit()
                .putBoolean(MainActivity.KEY_USE_DYNAMIC_COLOR, isChecked)
                .apply()
            showThemeRestartNotice()
        }
    }

    private fun showFontSelectorDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_font_selector, null)
        val dialog = AlertDialog.Builder(this)
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
            Toast.makeText(this, "Font changed. Please restart the app.", Toast.LENGTH_SHORT).show()
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
                val exportJson = org.json.JSONObject().apply {
                    put("version", 1)
                    put("selected", org.json.JSONArray(selected))
                    put("show_system_apps", prefs.getBoolean(MainActivity.KEY_SHOW_SYSTEM_APPS, false))
                    put("skip_enable_confirm", prefs.getBoolean("skip_enable_confirm", false))
                    put("move_selected_top", prefs.getBoolean(MainActivity.KEY_MOVE_SELECTED_TOP, true))
                }
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(exportJson.toString().toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: throw IllegalStateException("Unable to open output stream")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Export successful", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
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
                val showSys = obj.optBoolean("show_system_apps", false)
                val skipConfirm = obj.optBoolean("skip_enable_confirm", false)
                val moveTop = obj.optBoolean("move_selected_top", true)

                val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putStringSet("selected_apps", selectedSet)
                    putInt("selected_count", selectedSet.size)
                    putBoolean(MainActivity.KEY_SHOW_SYSTEM_APPS, showSys)
                    putBoolean("skip_enable_confirm", skipConfirm)
                    putBoolean(MainActivity.KEY_MOVE_SELECTED_TOP, moveTop)
                    apply()
                }

                withContext(Dispatchers.Main) {
                    loadSettings()
                    setResult(RESULT_OK)
                    Toast.makeText(this@SettingsActivity, "Import successful. Please restart the app.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
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

    private fun showThemeRestartNotice() {
        AlertDialog.Builder(this)
            .setTitle("Theme Changed")
            .setMessage("The theme has been updated. Please restart the app to apply the changes.")
            .setPositiveButton("Restart Now") { _, _ ->
                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Later", null)
            .show()
    }
}
