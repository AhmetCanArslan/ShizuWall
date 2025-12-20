package com.arslan.shizuwall

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.arslan.shizuwall.R
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import android.view.View
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.arslan.shizuwall.ladb.LadbManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.widget.LinearLayout
import android.widget.ScrollView
import java.net.Inet4Address
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.arslan.shizuwall.receivers.LadbPairingCodeReceiver
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.net.Socket
import java.net.InetSocketAddress
import android.os.Handler
import android.os.Looper

class LadbSetupActivity : AppCompatActivity(), AdbPortListener {

    private lateinit var rootView: View
    private lateinit var tvStatus: TextView
    private lateinit var btnUnpair: MaterialButton
    private lateinit var btnPair: MaterialButton
    private lateinit var btnConnect: MaterialButton
    private lateinit var btnStartService: MaterialButton
    private lateinit var tvLadbLogs: TextView
    private lateinit var switchEnableLogs: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var logsContainer: LinearLayout

    private lateinit var ladbManager: LadbManager
    private lateinit var adbPortFinder: AdbPortFinder
    private var localIp: String? = null
    private val detectedConnectPorts = mutableListOf<Int>()
    private val handler = Handler(Looper.getMainLooper())
    private var lastShownErrorHash: Int? = null
    private var mPermissionCallback: (() -> Unit)? = null

    private fun updateStatus() {
        runOnUiThread {
            val state = ladbManager.state
            val savedHost = ladbManager.getSavedHost()
            val savedPairingPort = ladbManager.getSavedPairingPort()
            val savedConnectPort = ladbManager.getSavedConnectPort()

            // Determine effective status based on both current state and saved configurations
            val effectiveState = when {
                state == LadbManager.State.CONNECTED -> LadbManager.State.CONNECTED
                state == LadbManager.State.ERROR -> LadbManager.State.ERROR
                savedConnectPort > 0 && savedHost != null -> LadbManager.State.DISCONNECTED
                savedPairingPort > 0 && savedHost != null -> LadbManager.State.PAIRED
                else -> LadbManager.State.UNCONFIGURED
            }

            tvStatus.text = when (effectiveState) {
                LadbManager.State.UNCONFIGURED -> getString(R.string.ladb_status_unconfigured)
                LadbManager.State.PAIRED -> getString(R.string.ladb_status_paired)
                LadbManager.State.CONNECTED -> {
                    if (isLadbServiceRunning()) {
                        getString(R.string.ladb_status_service_active)
                    } else {
                        getString(R.string.ladb_status_connected)
                    }
                }
                LadbManager.State.DISCONNECTED -> getString(R.string.ladb_status_disconnected)
                LadbManager.State.ERROR -> "Error"
            }

            val isPaired = effectiveState == LadbManager.State.PAIRED ||
                    effectiveState == LadbManager.State.DISCONNECTED

            val isConnected = effectiveState == LadbManager.State.CONNECTED

            if (isConnected) {
                btnPair.isEnabled = false
                btnConnect.isEnabled = false
                btnStartService.isEnabled = true
                btnUnpair.isEnabled = true
            } else if (isPaired) {
                btnPair.isEnabled = false
                btnConnect.isEnabled = true
                btnStartService.isEnabled = false
                btnUnpair.isEnabled = true
            } else {
                btnPair.isEnabled = true
                btnConnect.isEnabled = false
                btnStartService.isEnabled = false
                btnUnpair.isEnabled = false
            }

            // Update start service button text
            btnStartService.text = if (isLadbServiceRunning()) {
                getString(R.string.ladb_stop_service)
            } else {
                getString(R.string.ladb_start_service)
            }
        }
    }

    private fun isLadbServiceRunning(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
            if (com.arslan.shizuwall.services.LadbService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun appendLog(message: String) {
        if (!getLoggingEnabled()) return
        
        runOnUiThread {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val logEntry = "[$timestamp] $message\n"
            val currentLogs = tvLadbLogs.text.toString()
            val newLogs = currentLogs + logEntry
            
            // Keep only the last 1000 lines to prevent memory issues
            val lines = newLogs.split("\n")
            val trimmedLogs = if (lines.size > 1000) {
                lines.takeLast(1000).joinToString("\n") + "\n"
            } else {
                newLogs
            }
            
            tvLadbLogs.text = trimmedLogs
            
            // Save logs to persistent storage
            saveLogs(trimmedLogs)
            
            // Auto-scroll to bottom
            val scrollView = tvLadbLogs.parent as? ScrollView
            scrollView?.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun saveLogs(logs: String) {
        val prefs = getSharedPreferences("ladb_logs", Context.MODE_PRIVATE)
        prefs.edit().putString("logs", logs).apply()
    }

    private fun loadLogs(): String {
        val prefs = getSharedPreferences("ladb_logs", Context.MODE_PRIVATE)
        return prefs.getString("logs", "") ?: ""
    }

    private fun getLoggingEnabled(): Boolean {
        val prefs = getSharedPreferences("ladb_logs", Context.MODE_PRIVATE)
        return prefs.getBoolean("logging_enabled", false)
    }

    private fun setServiceShouldBeRunning(running: Boolean) {
        val prefs = getSharedPreferences("ladb_logs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("service_should_run", running).apply()
    }

    private fun getServiceShouldBeRunning(): Boolean {
        val prefs = getSharedPreferences("ladb_logs", Context.MODE_PRIVATE)
        return prefs.getBoolean("service_should_run", false)
    }

    private fun animateLogsContainer(show: Boolean) {
        // Cancel any ongoing animation
        logsContainer.animate().cancel()

        if (show) {
            // Fade in animation
            logsContainer.visibility = View.VISIBLE
            logsContainer.alpha = 0f
            logsContainer.animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .setListener(null) // Remove any previous listener
                .start()
        } else {
            // Fade out animation
            logsContainer.animate()
                .alpha(0f)
                .setDuration(300)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .setListener(object : android.animation.Animator.AnimatorListener {
                    override fun onAnimationStart(animation: android.animation.Animator) {}
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (!getLoggingEnabled()) { // Double-check the state
                            logsContainer.visibility = View.GONE
                        }
                    }
                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        // If animation is cancelled and logging is disabled, hide immediately
                        if (!getLoggingEnabled()) {
                            logsContainer.visibility = View.GONE
                            logsContainer.alpha = 0f
                        }
                    }
                    override fun onAnimationRepeat(animation: android.animation.Animator) {}
                })
                .start()
        }
    }

    private fun setLoggingEnabled(enabled: Boolean) {
        val prefs = getSharedPreferences("ladb_logs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("logging_enabled", enabled).apply()
    }

    private fun showLadbErrorDialog(title: String, logs: String) {
        val hint = if (logs.contains("ECONNREFUSED", ignoreCase = true) || logs.contains("Connection refused", ignoreCase = true)) {
            getString(R.string.ladb_hint_connection_refused)
        } else {
            ""
        }

        val merged = if (hint.isNotBlank()) {
            "$hint\n\n$logs"
        } else {
            logs
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_ladb_error, null)
        val tvLogs = dialogView.findViewById<TextView>(R.id.tvLadbErrorLogs)
        val btnCopy = dialogView.findViewById<android.widget.Button>(R.id.btnCopy)
        val btnClose = dialogView.findViewById<android.widget.Button>(R.id.btnClose)

        tvLogs.text = merged

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnCopy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("ladb_logs", merged)
            cm.setPrimaryClip(clip)
            Snackbar.make(rootView, getString(R.string.copy) + "!", Snackbar.LENGTH_SHORT).show()
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showNotificationPermissionDialog(onPermissionGranted: (() -> Unit)? = null) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Notification Permission Required")
            .setMessage("ShizuWall needs notification permission to show the persistent notification for the LADB service. This notification keeps the service running in the background and displays the connection status.\n\nWithout this permission, the LADB service cannot run properly.")
            .setPositiveButton("Grant Permission") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mPermissionCallback = onPermissionGranted
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
                } else {
                    // For older versions, open app settings
                    val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    startActivity(intent)
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                showNotificationDeniedDialog()
            }
            .show()
    }

    private fun showNotificationDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Cannot Continue")
            .setMessage("Notification permission is required to use the LADB pairing feature. This permission allows us to show important pairing information.\n\nPlease grant notification permission to proceed.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showPairingInfoDialog() {
        MaterialAlertDialogBuilder(this)
            .setMessage("Enter pairing code from wireless debugging page into notification.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open Developer Settings") { _, _ ->
                // Open developer settings
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                // After opening settings, proceed with pairing
                proceedWithPairing()
            }
            .show()
    }

    private fun proceedWithPairing() {
        val savedHost = ladbManager.getSavedHost()
        val savedPairingPort = ladbManager.getSavedPairingPort()
        
        if (savedHost.isNullOrBlank() || savedPairingPort <= 0) {
            appendLog("No pairing config found, waiting for auto-discovery...")
            Snackbar.make(rootView, "Waiting for pairing service discovery...", Snackbar.LENGTH_SHORT).show()
            return
        }

        appendLog("Showing pairing notification for config: $savedHost:$savedPairingPort")
        showPairingCodeNotification()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ladb_setup)

        val toolbar = findViewById<MaterialToolbar?>(R.id.ladbToolbar)
        toolbar?.setNavigationOnClickListener { finish() }

        // Respect system bars (status/navigation) similar to SettingsActivity
        rootView = findViewById<android.view.View>(R.id.ladbSetupRoot) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply top margin to toolbar to account for status bar
            try {
                toolbar?.let {
                    val toolbarParams = it.layoutParams as ViewGroup.MarginLayoutParams
                    toolbarParams.topMargin = systemBars.top
                    it.layoutParams = toolbarParams
                }
            } catch (e: Exception) {
                // ignore
            }
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        // Bind UI controls
        tvStatus = findViewById<TextView>(R.id.tvLadbStatus)
        btnPair = findViewById<MaterialButton>(R.id.btnPair)
        btnConnect = findViewById<MaterialButton>(R.id.btnConnect)
        btnUnpair = findViewById<MaterialButton>(R.id.btnUnpair)
        btnStartService = findViewById<MaterialButton>(R.id.btnStartService)
        tvLadbLogs = findViewById<TextView>(R.id.tvLadbLogs)
        val btnClearLogs = findViewById<MaterialButton>(R.id.btnClearLogs)
        val btnCopyLogs = findViewById<MaterialButton>(R.id.btnCopyLogs)
        switchEnableLogs = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchEnableLogs)
        logsContainer = findViewById<LinearLayout>(R.id.logsContainer)

        // Load logging preference
        switchEnableLogs.isChecked = getLoggingEnabled()
        
        // Set initial state without animation
        logsContainer.visibility = if (getLoggingEnabled()) View.VISIBLE else View.GONE

        switchEnableLogs.setOnCheckedChangeListener { _, isChecked ->
            setLoggingEnabled(isChecked)
            animateLogsContainer(isChecked)
            if (isChecked) {
                appendLog("Logging enabled")
            }
        }

        // Load saved logs
        val savedLogs = loadLogs()
        if (savedLogs.isNotEmpty()) {
            tvLadbLogs.text = savedLogs
        }

        ladbManager = LadbManager.getInstance(this)
        localIp = detectLocalIpv4OrNull()
        adbPortFinder = AdbPortFinder(this, this)
        detectedConnectPorts.clear()
        adbPortFinder.startDiscovery()

        updateStatus()


        updateStatus()
        appendLog("LADB Setup initialized. Current status: ${tvStatus.text}")

        btnPair.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    showNotificationPermissionDialog {
                        showPairingInfoDialog()
                    }
                    return@setOnClickListener
                }
            } else if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                // For older Android versions, show denied dialog since we can't properly request permission
                showNotificationDeniedDialog()
                return@setOnClickListener
            }
            
            showPairingInfoDialog()
        }

        btnConnect.setOnClickListener {
            appendLog("Starting connection...")
            lifecycleScope.launch {
                btnConnect.isEnabled = false
                var savedHost = ladbManager.getSavedHost()
                var savedConnectPort = ladbManager.getSavedConnectPort()
                
                // If no connect config but we have detected ports, try to scan and save first
                if ((savedHost.isNullOrBlank() || savedConnectPort <= 0) && detectedConnectPorts.isNotEmpty()) {
                    val host = savedHost ?: localIp
                    if (!host.isNullOrBlank()) {
                        appendLog("No connect config, attempting to scan detected ports...")
                        val foundPort = withContext(Dispatchers.IO) {
                            scanAndSaveConnectPort(host)
                        }
                        if (foundPort > 0) {
                            savedHost = host
                            savedConnectPort = foundPort
                        }
                    }
                }
                
                if (savedHost.isNullOrBlank() || savedConnectPort <= 0) {
                    appendLog("No connect config found")
                    Snackbar.make(rootView, "No connection configuration found. Wait for auto-discovery or check wireless debugging.", Snackbar.LENGTH_LONG).show()
                    btnConnect.isEnabled = true
                    return@launch
                }
                
                val ok = withContext(Dispatchers.IO) {
                    appendLog("Connecting to $savedHost:$savedConnectPort")
                    ladbManager.connect(savedHost, savedConnectPort)
                }
                updateStatus()
                if (!ok) {
                    val logs = ladbManager.getLastErrorLog()
                    val errorMessage = if (logs.isNullOrBlank()) {
                        "Connection failed. Please check:\n" +
                        "1. Wireless debugging is enabled in Developer options\n" +
                        "2. The device is on the same network\n" +
                        "3. No firewall is blocking the connection"
                    } else {
                        logs
                    }
                    appendLog("Connection failed: $errorMessage")
                    showLadbErrorDialog(getString(R.string.ladb_error_title), errorMessage)
                } else {
                    appendLog("Connection successful")
                }
                btnConnect.isEnabled = true
            }
        }

        btnUnpair.setOnClickListener {
            appendLog("Unpairing device...")
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { ladbManager.clearAllConfig() }
                
                // Stop service if running
                if (isLadbServiceRunning()) {
                    val intent = android.content.Intent(this@LadbSetupActivity, com.arslan.shizuwall.services.LadbService::class.java)
                    stopService(intent)
                    setServiceShouldBeRunning(false)
                    appendLog("LADB service stopped")
                }
                
                updateStatus()
                appendLog("Device unpaired")
            }
        }

        btnClearLogs.setOnClickListener {
            tvLadbLogs.text = ""
            saveLogs("")
            appendLog("Logs cleared")
        }

        btnCopyLogs.setOnClickListener {
            val logs = tvLadbLogs.text.toString()
            if (logs.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("ladb_logs", logs)
                clipboard.setPrimaryClip(clip)
                Snackbar.make(rootView, getString(R.string.copy) + " logs!", Snackbar.LENGTH_SHORT).show()
                appendLog("Logs copied to clipboard")
            } else {
                Snackbar.make(rootView, "No logs to copy", Snackbar.LENGTH_SHORT).show()
            }
        }

        btnStartService.setOnClickListener {
            if (isLadbServiceRunning()) {
                // Stop service
                appendLog("Stopping LADB service...")
                val intent = android.content.Intent(this, com.arslan.shizuwall.services.LadbService::class.java)
                stopService(intent)
                setServiceShouldBeRunning(false)
                appendLog("LADB service stopped")
                Snackbar.make(rootView, "Service stopped", Snackbar.LENGTH_SHORT).show()
            } else {
                // Start service
                appendLog("Starting LADB service...")
                val intent = android.content.Intent(this, com.arslan.shizuwall.services.LadbService::class.java)
                startService(intent)
                setServiceShouldBeRunning(true)
                appendLog("LADB service started")
                Snackbar.make(rootView, "Service started", Snackbar.LENGTH_SHORT).show()
            }
            updateStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        // Restart service if it should be running but isn't, and we're in a connectable state
        val state = ladbManager.state
        if (getServiceShouldBeRunning() && !isLadbServiceRunning() && (state == com.arslan.shizuwall.ladb.LadbManager.State.PAIRED || state == com.arslan.shizuwall.ladb.LadbManager.State.CONNECTED)) {
            appendLog("Restarting LADB service...")
            val intent = android.content.Intent(this, com.arslan.shizuwall.services.LadbService::class.java)
            startService(intent)
            appendLog("LADB service restarted")
        }
        // Refresh status in case pairing happened via notification while we were away.
        tvStatus.post { 
            try {
                val oldStatus = tvStatus.text.toString()
                updateStatus()
                val newStatus = tvStatus.text.toString()
                if (oldStatus != newStatus) {
                    appendLog("Status updated: $newStatus")
                }

                // After pairing completes, check if we have detected connect ports but no saved connect config
                // This handles the race condition where connect port is detected before pairing finishes
                val hasPairingConfig = ladbManager.getSavedPairingPort() > 0 && ladbManager.getSavedHost() != null
                val hasConnectConfig = ladbManager.getSavedConnectPort() > 0
                if (hasPairingConfig && !hasConnectConfig && detectedConnectPorts.isNotEmpty()) {
                    val savedHost = ladbManager.getSavedHost()
                    if (!savedHost.isNullOrBlank()) {
                        appendLog("Pairing complete but no connect config - scanning detected ports...")
                        scanForOpenPort(savedHost)
                    }
                }

                if (ladbManager.state == LadbManager.State.ERROR) {
                    val logs = ladbManager.getLastErrorLog().orEmpty()
                    if (logs.isNotBlank()) {
                        val h = logs.hashCode()
                        if (lastShownErrorHash != h) {
                            lastShownErrorHash = h
                            appendLog("Showing last LADB error details")
                            showLadbErrorDialog(getString(R.string.ladb_error_title), logs)
                        }
                    }
                }
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        adbPortFinder.stopDiscovery()
    }

    override fun onPairingPortFound(host: String, port: Int) {
        appendLog("Pairing port detected: $host:$port")
        if (host == "unknown" || port <= 0 || port > 65535) return

        lifecycleScope.launch(Dispatchers.IO) {
            ladbManager.saveHost(host)
            val success = ladbManager.savePairingConfig(host, port)
            withContext(Dispatchers.Main) {
                appendLog(if (success) "Pairing config saved" else "Failed to save pairing config")
                showPairingCodeNotification()
            }
        }
    }

    override fun onConnectPortFound(host: String, port: Int) {
        if (host == localIp) {
            appendLog("Connect port detected: $host:$port")
            detectedConnectPorts.add(port)
            if (detectedConnectPorts.size == 1) {
                // Schedule scan after 2 seconds to allow more detections
                handler.postDelayed({ scanForOpenPort(host) }, 2000)
            }
        } else {
            // Optionally log ignored detections if needed for debugging
            // appendLog("Ignoring connect port from different host: $host")
        }
    }

    /**
     * Suspend version of port scanning that returns the found port.
     * Must be called from a coroutine.
     */
    private suspend fun scanAndSaveConnectPort(host: String): Int {
        val ports = detectedConnectPorts.sorted()
        var realPort = -1
        for (port in ports) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), 200)
                if (socket.isConnected) {
                    realPort = port
                    socket.close()
                    break
                }
            } catch (e: Exception) {
                // Port not open, continue
            }
        }
        if (realPort != -1) {
            withContext(Dispatchers.Main) { appendLog("Real connect port found: $realPort") }
            val hasPairingConfig = ladbManager.getSavedPairingPort() > 0 && ladbManager.getSavedHost() != null
            if (hasPairingConfig) {
                val success = ladbManager.saveConnectConfig(host, realPort)
                withContext(Dispatchers.Main) {
                    if (success) {
                        appendLog("Connect config saved")
                    } else {
                        appendLog("Failed to save connect config")
                    }
                }
                return if (success) realPort else -1
            }
        } else {
            withContext(Dispatchers.Main) { appendLog("No open connect port found from detected ports") }
        }
        return realPort
    }

    private fun scanForOpenPort(host: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val ports = detectedConnectPorts.sorted()
            var realPort = -1
            for (port in ports) {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(host, port), 200)
                    if (socket.isConnected) {
                        realPort = port
                        socket.close()
                        break
                    }
                } catch (e: Exception) {
                    // Port not open, continue
                }
            }
            withContext(Dispatchers.Main) {
                if (realPort != -1) {
                    appendLog("Real connect port found: $realPort")
                    lifecycleScope.launch {
                        // Only save connect config if we have pairing config (device has been paired)
                        val hasPairingConfig = ladbManager.getSavedPairingPort() > 0 && ladbManager.getSavedHost() != null
                        if (hasPairingConfig) {
                            val success = ladbManager.saveConnectConfig(host, realPort)
                            if (success) {
                                appendLog("Connect config saved")
                            } else {
                                appendLog("Failed to save connect config")
                            }
                        } else {
                            appendLog("Connect port found but not saving config (device not paired)")
                        }
                    }
                } else {
                    appendLog("No open connect port found")
                }
            }
        }
    }

    private fun detectLocalIpv4OrNull(): String? {
        return try {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return null
            val network = cm.activeNetwork ?: return null
            val lp: LinkProperties = cm.getLinkProperties(network) ?: return null
            lp.linkAddresses
                .mapNotNull { it.address }
                .firstOrNull { addr -> addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }

    private fun createPairingNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(PAIRING_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    PAIRING_CHANNEL_ID,
                    getString(R.string.ladb_pairing_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                )
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun showPairingCodeNotification() {
        // Android 13+ requires notification permission.
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(rootView, getString(R.string.ladb_notification_permission_required), Snackbar.LENGTH_LONG).show()
                return
            }
        }

        createPairingNotificationChannel()

        val hasPairingPort = ladbManager.getSavedPairingPort() > 0
        val detailsLabel = if (hasPairingPort) {
            getString(R.string.ladb_pairing_details_hint_code_only)
        } else {
            getString(R.string.ladb_pairing_details_hint_port_code)
        }
        val remoteDetails = RemoteInput.Builder(LadbPairingCodeReceiver.KEY_REMOTE_INPUT_DETAILS)
            .setLabel(detailsLabel)
            .build()

        val intent = Intent(this, LadbPairingCodeReceiver::class.java).apply {
            action = LadbPairingCodeReceiver.ACTION_LADB_PAIRING_CODE
        }

        // RemoteInput requires a mutable PendingIntent on Android 12+.
        val actionFlags = PendingIntent.FLAG_UPDATE_CURRENT or when {
            Build.VERSION.SDK_INT >= 31 -> PendingIntent.FLAG_MUTABLE
            Build.VERSION.SDK_INT >= 23 -> 0
            else -> 0
        }
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, actionFlags)

        val action = NotificationCompat.Action.Builder(
            0,
            getString(R.string.ladb_enter_pairing_details_action),
            pendingIntent
        )
            .addRemoteInput(remoteDetails)
            .setAllowGeneratedReplies(false)
            .build()

        val openIntent = Intent(this, LadbSetupActivity::class.java)
        val contentFlags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val contentIntent = PendingIntent.getActivity(this, 0, openIntent, contentFlags)

        val notification = NotificationCompat.Builder(this, PAIRING_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.ladb_pairing_notification_title))
            .setContentText(
                if (hasPairingPort) {
                    getString(R.string.ladb_pairing_notification_text_code_only)
                } else {
                    getString(R.string.ladb_pairing_notification_text)
                }
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(action)
            .build()

        NotificationManagerCompat.from(this)
            .notify(LadbPairingCodeReceiver.NOTIFICATION_ID, notification)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, execute callback if available
                Snackbar.make(rootView, "Notification permission granted!", Snackbar.LENGTH_SHORT).show()
                mPermissionCallback?.invoke()
                mPermissionCallback = null
            } else {
                // Permission denied
                showNotificationDeniedDialog()
            }
        }
    }

    companion object {
        private const val PAIRING_CHANNEL_ID = "ladb_pairing"
    }
}

interface AdbPortListener {
    fun onPairingPortFound(host: String, port: Int)
    fun onConnectPortFound(host: String, port: Int)
}

class AdbPortFinder(context: Context, private val listener: AdbPortListener) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    // Service types
    private val PAIRING_SERVICE_TYPE = "_adb-tls-pairing._tcp"
    private val CONNECT_SERVICE_TYPE = "_adb-tls-connect._tcp"
    private val TAG = "AdbPortFinder"

    fun startDiscovery() {
        // Ensure any previous discovery is stopped to avoid "listener already in use" error
        stopDiscovery()
        nsdManager.discoverServices(
            PAIRING_SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            pairingDiscoveryListener
        )
        nsdManager.discoverServices(
            CONNECT_SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            connectDiscoveryListener
        )
    }

    fun stopDiscovery() {
        try {
            nsdManager.stopServiceDiscovery(pairingDiscoveryListener)
        } catch (e: Exception) {
            // Already stopped
        }
        try {
            nsdManager.stopServiceDiscovery(connectDiscoveryListener)
        } catch (e: Exception) {
            // Already stopped
        }
    }

    private val pairingDiscoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "Pairing service discovery started")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d(TAG, "Pairing service found: ${service.serviceName}")
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "Pairing resolve failed: $errorCode")
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val port = serviceInfo.port
                    val host = serviceInfo.host?.hostAddress ?: "unknown"
                    Log.d(TAG, "Pairing resolved! IP: $host, PORT: $port")
                    listener.onPairingPortFound(host, port)
                }
            })
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Log.e(TAG, "Pairing service lost: $service")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG, "Pairing discovery stopped")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Pairing discovery start failed: $errorCode")
            nsdManager.stopServiceDiscovery(this)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            nsdManager.stopServiceDiscovery(this)
        }
    }

    private val connectDiscoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "Connect service discovery started")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d(TAG, "Connect service found: ${service.serviceName}")
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "Connect resolve failed: $errorCode")
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val port = serviceInfo.port
                    val host = serviceInfo.host?.hostAddress ?: "unknown"
                    Log.d(TAG, "Connect resolved! IP: $host, PORT: $port")
                    listener.onConnectPortFound(host, port)
                }
            })
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Log.e(TAG, "Connect service lost: $service")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG, "Connect discovery stopped")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Connect discovery start failed: $errorCode")
            nsdManager.stopServiceDiscovery(this)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            nsdManager.stopServiceDiscovery(this)
        }
    }
}
