package com.arslan.shizuwall.ui

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.arslan.shizuwall.R
import com.arslan.shizuwall.ladb.LadbManager
import com.arslan.shizuwall.services.AppMonitorService
import com.arslan.shizuwall.services.FloatingButtonService
import com.arslan.shizuwall.services.ForegroundDetectionService
import com.arslan.shizuwall.shell.RootShellExecutor
import com.arslan.shizuwall.shell.ShellExecutorProvider
import com.arslan.shizuwall.shizuku.ShizukuSetupActivity
import com.arslan.shizuwall.utils.ShizukuPackageResolver
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.File

class SettingsActivity : BaseActivity() {

    companion object {
        private const val EXPORT_SCHEMA = "shizuwall.preferences"
        private const val EXPORT_VERSION = 3
    }

    private lateinit var sharedPreferences: SharedPreferences

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

        sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE)

        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        if (sharedPreferences.getBoolean(MainActivity.KEY_USE_AMOLED_BLACK, false)) {
            findViewById<View>(R.id.settingsRoot).setBackgroundColor(android.graphics.Color.BLACK)
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            if (isTaskRoot) {
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val toolbarParams = toolbar.layoutParams as ViewGroup.MarginLayoutParams
            toolbarParams.topMargin = systemBars.top
            toolbar.layoutParams = toolbarParams
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        findViewById<LinearLayout>(R.id.rowAppearance).setOnClickListener {
            startActivity(Intent(this, AppearanceSettingsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.rowFirewall).setOnClickListener {
            startActivity(Intent(this, FirewallSettingsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.rowBackend).setOnClickListener {
            startActivity(Intent(this, BackendSettingsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.rowNotifications).setOnClickListener {
            startActivity(Intent(this, NotificationsSettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnExport).setOnClickListener {
            createDocumentLauncher.launch("shizuwall_settings")
        }

        findViewById<LinearLayout>(R.id.btnImport).setOnClickListener {
            openDocumentLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
        }

        findViewById<LinearLayout>(R.id.btnDonate).setOnClickListener {
            val url = getString(R.string.buymeacoffee_url)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        findViewById<LinearLayout>(R.id.btnGithub).setOnClickListener {
            val url = getString(R.string.github_url)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        findViewById<LinearLayout>(R.id.btnShizukuGuide).setOnClickListener {
            startActivity(Intent(this, ShizukuSetupActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnResetApp).setOnClickListener {
            showResetConfirmationDialog()
        }

        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        tvVersion.text = getString(R.string.version_format, packageInfo.versionName)
    }

    private fun showResetConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.reset_app_title))
            .setMessage(getString(R.string.reset_app_message))
            .setPositiveButton(getString(R.string.reset)) { _, _ -> resetApp() }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun resetApp() {
        lifecycleScope.launch {
            try {
                stopResetSensitiveComponents()

                val cleanupPerformed = withContext(Dispatchers.IO) {
                    performBestEffortFirewallCleanup()
                }
                if (!cleanupPerformed) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.firewall_cleanup_warning), Toast.LENGTH_LONG).show()
                }

                val clearRequested = requestSystemDataClear()
                if (clearRequested) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.app_reset_complete), Toast.LENGTH_SHORT).show()
                    delay(1200)
                    finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(0)
                    return@launch
                }

                val fallbackSuccess = withContext(Dispatchers.IO) { performManualDataClearFallback() }
                if (!fallbackSuccess) {
                    throw IllegalStateException("Manual reset fallback failed")
                }

                Toast.makeText(this@SettingsActivity, getString(R.string.app_reset_complete), Toast.LENGTH_SHORT).show()
                finishAffinity()
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(0)
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, getString(R.string.reset_app_failed, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopResetSensitiveComponents() {
        try { stopService(Intent(this, AppMonitorService::class.java)) } catch (_: Exception) {}
        try { stopService(Intent(this, ForegroundDetectionService::class.java)) } catch (_: Exception) {}
        try { FloatingButtonService.stop(this) } catch (_: Exception) {}
        try { (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancelAll() } catch (_: Exception) {}
    }

    private suspend fun performBestEffortFirewallCleanup(): Boolean {
        return try {
            if (!sharedPreferences.getBoolean(MainActivity.KEY_FIREWALL_ENABLED, false)) return true

            val activePackages = sharedPreferences.getStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet()) ?: emptySet()
            val selectedApps = sharedPreferences.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet()) ?: emptySet()
            val allPackages = (activePackages + selectedApps)
                .filterNot { it == packageName || ShizukuPackageResolver.isShizukuPackage(this, it) }

            val mode = sharedPreferences.getString(MainActivity.KEY_WORKING_MODE, "SHIZUKU") ?: "SHIZUKU"
            val canPerformCleanup = when (mode) {
                "SHIZUKU" -> {
                    try {
                        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
                    } catch (_: Throwable) {
                        false
                    }
                }
                "ROOT" -> RootShellExecutor.hasRootAccess()
                else -> {
                    val dm = com.arslan.shizuwall.daemon.PersistentDaemonManager(this)
                    dm.isDaemonRunning() || LadbManager.getInstance(this).isConnected()
                }
            }

            if (!canPerformCleanup) return false

            val executor = ShellExecutorProvider.forContext(this)
            val chainResult = executor.exec("cmd connectivity set-chain3-enabled false")

            for (pkg in allPackages) {
                executor.exec("cmd connectivity set-package-networking-enabled true $pkg")
            }

            if (mode == "LADB") {
                executor.exec("kill \\$(cat /data/local/tmp/daemon.pid 2>/dev/null) 2>/dev/null; pkill -f 'com.arslan.shizuwall.daemon.SystemDaemon' 2>/dev/null || true")
            }

            chainResult.success
        } catch (_: Exception) {
            false
        }
    }

    private fun requestSystemDataClear(): Boolean {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.clearApplicationUserData() == true
        } catch (_: Exception) {
            false
        }
    }

    private fun performManualDataClearFallback(): Boolean {
        var ok = true

        ok = clearAllSharedPrefs(this) && ok

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val dp = createDeviceProtectedStorageContext()
                ok = clearAllSharedPrefs(dp) && ok
            } catch (_: Exception) {
                ok = false
            }
        }

        try {
            for (dbName in databaseList()) {
                if (!deleteDatabase(dbName)) ok = false
            }
        } catch (_: Exception) {
            ok = false
        }

        ok = deleteChildren(filesDir) && ok
        ok = deleteChildren(cacheDir) && ok
        ok = deleteChildren(codeCacheDir) && ok
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ok = deleteChildren(noBackupFilesDir) && ok
        }
        ok = deleteChildren(externalCacheDir) && ok
        ok = deleteChildren(getExternalFilesDir(null)) && ok

        return ok
    }

    private fun clearAllSharedPrefs(context: Context): Boolean {
        var ok = true
        val sharedPrefsDir = File(context.filesDir.parentFile, "shared_sharedPreferences")
        val prefNames = sharedPrefsDir.listFiles()
            ?.mapNotNull { file ->
                val name = file.name
                if (name.endsWith(".xml")) name.removeSuffix(".xml") else null
            }
            ?.toSet()
            ?: emptySet()

        for (name in prefNames) {
            try {
                val deleted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.deleteSharedPreferences(name)
                } else {
                    context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                }
                if (!deleted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                }
            } catch (_: Exception) {
                ok = false
            }
        }
        return ok
    }

    private fun deleteChildren(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return true
        var ok = true
        val children = dir.listFiles() ?: return true
        for (child in children) {
            if (!deleteRecursively(child)) ok = false
        }
        return ok
    }

    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    if (!deleteRecursively(child)) return false
                }
            }
        }
        return file.delete() || !file.exists()
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
                if (obj.optString("schema") == EXPORT_SCHEMA && obj.has("files")) {
                    val version = obj.optInt("version", 1)
                    if (version <= EXPORT_VERSION) {
                        if (!importStructuredBackup(obj)) {
                            android.util.Log.w("SettingsActivity", "Structured import completed with warnings")
                        }
                    } else {
                        importLegacyBackup(obj)
                    }
                } else {
                    importLegacyBackup(obj)
                }

                val sp = getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE)
                sanitizeSelectedApps(sp)

                val isMonitorEnabled = sp.getBoolean(MainActivity.KEY_APP_MONITOR_ENABLED, false) ||
                    sp.getBoolean(MainActivity.KEY_AUTO_FIREWALL_NEW_APPS, false) ||
                    sp.getBoolean(MainActivity.KEY_SHOW_FIREWALL_STATUS_NOTIFICATION, false)
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
                } catch (_: Exception) {
                }

                getSharedPreferences("app_sharedPreferences", MODE_PRIVATE).edit()
                    .putBoolean("onboarding_complete", true)
                    .apply()

                withContext(Dispatchers.Main) {
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

    private fun exportSharedPreferences(
        sharedPreferencesName: String,
        excludedKeys: Set<String> = emptySet()
    ): JSONObject {
        val sharedPreferencesMap = getSharedPreferences(sharedPreferencesName, MODE_PRIVATE).all
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
            android.util.Log.w("SettingsActivity", "Import failed: missing files object")
            return false
        }
        val fileNames = files.keys()

        var success = false
        while (fileNames.hasNext()) {
            val sharedPreferencesName = fileNames.next()
            val fileObj = files.optJSONObject(sharedPreferencesName)
            if (fileObj == null) {
                android.util.Log.w("SettingsActivity", "Import warning: missing object for $sharedPreferencesName")
                continue
            }
            val entries = fileObj.optJSONArray("entries")
            if (entries == null) {
                android.util.Log.w("SettingsActivity", "Import warning: missing entries for $sharedPreferencesName")
                continue
            }

            val editor = getSharedPreferences(sharedPreferencesName, MODE_PRIVATE).edit()
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
        val sp = getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE)
        val editor = sp.edit()

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
            val ladbEditor = getSharedPreferences(LadbManager.PREFS_NAME, MODE_PRIVATE).edit()
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

    private fun sanitizeSelectedApps(sp: SharedPreferences) {
        val selected = sp.getStringSet(MainActivity.KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        val filtered = selected.filterNot { isShizukuPackage(it) }.toSet()
        sp.edit()
            .putStringSet(MainActivity.KEY_SELECTED_APPS, filtered)
            .putInt(MainActivity.KEY_SELECTED_COUNT, filtered.size)
            .apply()
    }

    private fun isShizukuPackage(pkg: String): Boolean {
        return ShizukuPackageResolver.isShizukuPackage(this, pkg)
    }
}
