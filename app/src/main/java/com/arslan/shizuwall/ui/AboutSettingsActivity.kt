package com.arslan.shizuwall.ui

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
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
import rikka.shizuku.Shizuku
import java.io.File

class AboutSettingsActivity : BaseActivity() {

    private lateinit var tvVersion: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        enableEdgeToEdge()
        setContentView(R.layout.activity_settings_about)

        val rootView = findViewById<View>(R.id.aboutSettingsRoot)
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

        val btnDonate = findViewById<LinearLayout>(R.id.btnDonate)
        val btnGithub = findViewById<LinearLayout>(R.id.btnGithub)
        val btnShizukuGuide = findViewById<LinearLayout>(R.id.btnShizukuGuide)
        val btnResetApp = findViewById<LinearLayout>(R.id.btnResetApp)
        tvVersion = findViewById(R.id.tvVersion)

        btnDonate.setOnClickListener {
            val url = getString(R.string.buymeacoffee_url)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        btnGithub.setOnClickListener {
            val url = getString(R.string.github_url)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        btnShizukuGuide.setOnClickListener {
            startActivity(Intent(this, ShizukuSetupActivity::class.java))
        }

        btnResetApp.setOnClickListener {
            showResetConfirmationDialog()
        }

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
                    Toast.makeText(this@AboutSettingsActivity, getString(R.string.firewall_cleanup_warning), Toast.LENGTH_LONG).show()
                }

                val clearRequested = requestSystemDataClear()
                if (clearRequested) {
                    Toast.makeText(this@AboutSettingsActivity, getString(R.string.app_reset_complete), Toast.LENGTH_SHORT).show()
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

                Toast.makeText(this@AboutSettingsActivity, getString(R.string.app_reset_complete), Toast.LENGTH_SHORT).show()
                finishAffinity()
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(0)
            } catch (e: Exception) {
                Toast.makeText(this@AboutSettingsActivity, getString(R.string.reset_app_failed, e.message), Toast.LENGTH_SHORT).show()
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
            val sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
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
}
