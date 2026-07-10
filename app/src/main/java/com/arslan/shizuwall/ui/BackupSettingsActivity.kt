package com.arslan.shizuwall.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.arslan.shizuwall.R
import com.arslan.shizuwall.ladb.LadbManager
import com.arslan.shizuwall.services.AppMonitorService
import com.arslan.shizuwall.utils.ShizukuPackageResolver
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BackupSettingsActivity : BaseActivity() {

    companion object {
        private const val EXPORT_SCHEMA = "shizuwall.preferences"
        private const val EXPORT_VERSION = 3
    }

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var btnExport: LinearLayout
    private lateinit var btnImport: LinearLayout

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
        setContentView(R.layout.activity_settings_backup)

        val rootView = findViewById<View>(R.id.backupSettingsRoot)
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

        btnExport = findViewById(R.id.btnExport)
        btnImport = findViewById(R.id.btnImport)

        btnExport.setOnClickListener {
            createDocumentLauncher.launch("shizuwall_settings")
        }

        btnImport.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
        }
    }

    private suspend fun exportToUri(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val files = JSONObject().apply {
                    put(
                        MainActivity.PREF_NAME,
                        exportSharedPreferences(
                            MainActivity.PREF_NAME,
                            setOf(
                                MainActivity.KEY_FIREWALL_ENABLED,
                                MainActivity.KEY_ACTIVE_PACKAGES,
                                MainActivity.KEY_FIREWALL_SAVED_ELAPSED,
                                MainActivity.KEY_FIREWALL_UPDATE_TS,
                                MainActivity.KEY_SMART_FOREGROUND_APP
                            )
                        )
                    )
                    put("app_sharedPreferences", exportSharedPreferences("app_sharedPreferences"))
                    put(LadbManager.PREFS_NAME, exportSharedPreferences(LadbManager.PREFS_NAME))
                    put("daemon_sharedPreferences", exportSharedPreferences("daemon_sharedPreferences"))
                }

                val exportJson = JSONObject().apply {
                    put("schema", EXPORT_SCHEMA)
                    put("version", EXPORT_VERSION)
                    put("exported_at", System.currentTimeMillis())
                    put("files", files)
                }

                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(exportJson.toString(2).toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: throw IllegalStateException("Unable to open output stream")

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BackupSettingsActivity, getString(R.string.export_successful), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BackupSettingsActivity, getString(R.string.export_failed, e.message), Toast.LENGTH_LONG).show()
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
                if (obj.optString("schema") == EXPORT_SCHEMA && obj.has("files")) {
                    val version = obj.optInt("version", 1)
                    if (version <= EXPORT_VERSION) {
                        if (!importStructuredBackup(obj)) {
                            android.util.Log.w("BackupSettingsActivity", "Structured import completed with warnings")
                        }
                    } else {
                        importLegacyBackup(obj)
                    }
                } else {
                    importLegacyBackup(obj)
                }

                val sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                sanitizeSelectedApps(sharedPreferences)

                val isMonitorEnabled = sharedPreferences.getBoolean(MainActivity.KEY_APP_MONITOR_ENABLED, false) ||
                    sharedPreferences.getBoolean(MainActivity.KEY_AUTO_FIREWALL_NEW_APPS, false) ||
                    sharedPreferences.getBoolean(MainActivity.KEY_SHOW_FIREWALL_STATUS_NOTIFICATION, false)
                val monitorIntent = Intent(this@BackupSettingsActivity, AppMonitorService::class.java)
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

                getSharedPreferences("app_sharedPreferences", Context.MODE_PRIVATE).edit()
                    .putBoolean("onboarding_complete", true)
                    .apply()

                withContext(Dispatchers.Main) {
                    setResult(RESULT_OK)
                    Toast.makeText(this@BackupSettingsActivity, getString(R.string.import_successful), Toast.LENGTH_SHORT).show()
                    recreateWithAnimation()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BackupSettingsActivity, getString(R.string.import_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportSharedPreferences(
        sharedPreferencesName: String,
        excludedKeys: Set<String> = emptySet()
    ): JSONObject {
        val sharedPreferencesMap = getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE).all
        val entries = JSONArray()

        for (key in sharedPreferencesMap.keys.sorted()) {
            if (key in excludedKeys) continue
            val value = sharedPreferencesMap[key] ?: continue
            val entry = JSONObject().put("key", key)

            when (value) {
                is Boolean -> {
                    entry.put("type", "boolean")
                    entry.put("value", value)
                }
                is String -> {
                    entry.put("type", "string")
                    entry.put("value", value)
                }
                is Int -> {
                    entry.put("type", "int")
                    entry.put("value", value)
                }
                is Long -> {
                    entry.put("type", "long")
                    entry.put("value", value)
                }
                is Float -> {
                    entry.put("type", "float")
                    entry.put("value", value.toDouble())
                }
                is Set<*> -> {
                    val stringSet = value.filterIsInstance<String>().toSet()
                    val arr = JSONArray()
                    stringSet.sorted().forEach { arr.put(it) }
                    entry.put("type", "string_set")
                    entry.put("value", arr)
                }
                else -> continue
            }

            entries.put(entry)
        }

        return JSONObject().put("entries", entries)
    }

    private fun importStructuredBackup(root: JSONObject): Boolean {
        val files = root.optJSONObject("files")
        if (files == null) {
            android.util.Log.w("BackupSettingsActivity", "Import failed: missing files object")
            return false
        }
        val fileNames = files.keys()

        var success = false
        while (fileNames.hasNext()) {
            val sharedPreferencesName = fileNames.next()
            val fileObj = files.optJSONObject(sharedPreferencesName)
            if (fileObj == null) {
                android.util.Log.w("BackupSettingsActivity", "Import warning: missing object for $sharedPreferencesName")
                continue
            }
            val entries = fileObj.optJSONArray("entries")
            if (entries == null) {
                android.util.Log.w("BackupSettingsActivity", "Import warning: missing entries for $sharedPreferencesName")
                continue
            }

            val editor = getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE).edit()
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                applyPreferenceEntry(editor, entry)
            }
            editor.apply()
            success = true
        }
        return success
    }

    private fun importLegacyBackup(obj: JSONObject) {
        val sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val selectedKey = if (obj.has("selected")) "selected" else MainActivity.KEY_SELECTED_APPS
        val selectedJson = obj.optJSONArray(selectedKey)
        if (selectedJson != null) {
            editor.putStringSet(MainActivity.KEY_SELECTED_APPS, jsonArrayToStringSet(selectedJson))
        }

        val favoritesKey = if (obj.has("favorites")) "favorites" else MainActivity.KEY_FAVORITE_APPS
        val favoritesJson = obj.optJSONArray(favoritesKey)
        if (favoritesJson != null) {
            editor.putStringSet(MainActivity.KEY_FAVORITE_APPS, jsonArrayToStringSet(favoritesJson))
        }

        val reserved = setOf(
            "schema",
            "files",
            "version",
            "exported_at",
            "selected",
            "favorites",
            "ladb_config"
        )

        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key in reserved) continue

            val value = obj.opt(key)
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
                is JSONArray -> editor.putStringSet(key, jsonArrayToStringSet(value))
            }
        }

        editor.apply()

        val ladbObj = obj.optJSONObject("ladb_config")
        if (ladbObj != null) {
            val ladbEditor = getSharedPreferences(LadbManager.PREFS_NAME, Context.MODE_PRIVATE).edit()
            val ladbKeys = ladbObj.keys()
            while (ladbKeys.hasNext()) {
                val key = ladbKeys.next()
                when (val value = ladbObj.opt(key)) {
                    is Boolean -> ladbEditor.putBoolean(key, value)
                    is String -> ladbEditor.putString(key, value)
                    is Int -> ladbEditor.putInt(key, value)
                    is Long -> ladbEditor.putLong(key, value)
                    is Double -> ladbEditor.putFloat(key, value.toFloat())
                    is JSONArray -> ladbEditor.putStringSet(key, jsonArrayToStringSet(value))
                }
            }
            ladbEditor.apply()
        }
    }

    private fun applyPreferenceEntry(editor: SharedPreferences.Editor, entry: JSONObject) {
        val key = entry.optString("key", "")
        if (key.isEmpty()) return

        when (entry.optString("type", "")) {
            "boolean" -> editor.putBoolean(key, entry.optBoolean("value"))
            "string" -> editor.putString(key, if (entry.isNull("value")) null else entry.optString("value"))
            "int" -> {
                val longValue = entry.getLong("value")
                if (longValue in Int.MIN_VALUE..Int.MAX_VALUE) {
                    editor.putInt(key, longValue.toInt())
                }
            }
            "long" -> editor.putLong(key, entry.optLong("value"))
            "float" -> editor.putFloat(key, entry.optDouble("value").toFloat())
            "string_set" -> editor.putStringSet(key, jsonArrayToStringSet(entry.optJSONArray("value")))
        }
    }

    private fun jsonArrayToStringSet(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        val result = linkedSetOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i, null)
            if (!value.isNullOrEmpty()) result.add(value)
        }
        return result
    }

    private fun sanitizeSelectedApps(sharedPreferences: SharedPreferences) {
        val selected = sharedPreferences.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        val filtered = selected.filterNot { isShizukuPackage(it) }.toSet()
        sharedPreferences.edit()
            .putStringSet(MainActivity.KEY_SELECTED_APPS, filtered)
            .putInt(MainActivity.KEY_SELECTED_COUNT, filtered.size)
            .apply()
    }

    private fun isShizukuPackage(pkg: String): Boolean {
        return ShizukuPackageResolver.isShizukuPackage(this, pkg)
    }
}
