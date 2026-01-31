package com.arslan.shizuwall

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import com.arslan.shizuwall.ui.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import android.view.View
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.arslan.shizuwall.ladb.LadbManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
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
import android.graphics.Color
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import com.arslan.shizuwall.daemon.PersistentDaemonManager
import kotlinx.coroutines.delay

class LadbSetupActivity : BaseActivity(), AdbPortListener {

    private lateinit var rootView: View
    private lateinit var tvStatus: TextView
    private lateinit var btnUnpair: MaterialButton
    private lateinit var btnPair: MaterialButton
    private lateinit var btnPairSimple: MaterialButton
    private lateinit var btnConnect: MaterialButton
    private lateinit var tvLadbLogs: TextView
    private lateinit var switchEnableLogs: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var switchAdvancedMode: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var logsContainer: LinearLayout
    private lateinit var simpleSetupContainer: LinearLayout
    private lateinit var advancedSetupContainer: LinearLayout

    // Daemon UI
    private lateinit var daemonStatusIndicator: View
    private lateinit var daemonStatusIndicatorSimple: View
    private lateinit var tvDaemonStatus: TextView
    private lateinit var tvDaemonStatusSimple: TextView
    private lateinit var actvDaemonCommands: AutoCompleteTextView
    private lateinit var connectProgress: android.widget.ProgressBar
    private lateinit var connectProgressSimple: android.widget.ProgressBar
    private lateinit var btnStartDaemon: MaterialButton
    private lateinit var btnKillDaemon: MaterialButton

    private lateinit var ladbManager: LadbManager
    private lateinit var daemonManager: PersistentDaemonManager
    private var adbPortFinder: AdbPortFinder? = null
    private var localIp: String? = null
    private val detectedConnectPorts = mutableListOf<Pair<String, Int>>()
    private val handler = Handler(Looper.getMainLooper())
    private var lastShownErrorHash: Int? = null
    private var mPermissionCallback: (() -> Unit)? = null
    private var isDaemonRunning = false

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
                (savedPairingPort > 0 || ladbManager.isPaired()) && savedHost != null -> LadbManager.State.PAIRED
                else -> LadbManager.State.UNCONFIGURED
            }

            tvStatus.text = when (effectiveState) {
                LadbManager.State.UNCONFIGURED -> getString(R.string.ladb_status_unconfigured)
                LadbManager.State.PAIRED -> getString(R.string.ladb_status_paired)
                LadbManager.State.CONNECTED -> getString(R.string.ladb_status_connected)
                LadbManager.State.DISCONNECTED -> getString(R.string.ladb_status_disconnected)
                LadbManager.State.ERROR -> "Error"
            }

            val isPaired = effectiveState == LadbManager.State.PAIRED ||
                    effectiveState == LadbManager.State.DISCONNECTED

            val isConnected = effectiveState == LadbManager.State.CONNECTED

            fun applyButtonEnabledState(button: MaterialButton, enabled: Boolean) {
                button.isEnabled = enabled
                button.alpha = if (enabled) 1.0f else 0.5f
            }

            if (isConnected) {
                applyButtonEnabledState(btnPair, false)
                applyButtonEnabledState(btnConnect, false)
                applyButtonEnabledState(btnUnpair, true)
            } else if (isPaired) {
                applyButtonEnabledState(btnPair, false)
                applyButtonEnabledState(btnConnect, true)
                applyButtonEnabledState(btnUnpair, true)
            } else {
                applyButtonEnabledState(btnPair, true)
                applyButtonEnabledState(btnConnect, false)
                applyButtonEnabledState(btnUnpair, false)
            }

            if (isDaemonRunning) {
                btnPairSimple.text = getString(R.string.daemon_status_running)
            } else if (isConnected) {
                btnPairSimple.text = getString(R.string.ladb_status_connected)
            } else if (isPaired) {
                btnPairSimple.text = getString(R.string.ladb_connect)
            } else {
                btnPairSimple.text = getString(R.string.pair)
            }

            // Simple pair button is only disabled when daemon is running
            applyButtonEnabledState(btnPairSimple, !isDaemonRunning)

            // Update Daemon UI
            if (isDaemonRunning) {
                daemonStatusIndicator.setBackgroundColor(Color.GREEN)
                daemonStatusIndicatorSimple.setBackgroundColor(Color.GREEN)
                tvDaemonStatus.text = getString(R.string.daemon_status_running)
                tvDaemonStatusSimple.text = getString(R.string.daemon_status_running)
                tvDaemonStatus.setTextColor(Color.GREEN)
                tvDaemonStatusSimple.setTextColor(Color.GREEN)
                applyButtonEnabledState(btnStartDaemon, false)
                applyButtonEnabledState(btnKillDaemon, isConnected)
            } else {
                daemonStatusIndicator.setBackgroundColor(Color.RED)
                daemonStatusIndicatorSimple.setBackgroundColor(Color.RED)
                tvDaemonStatus.text = getString(R.string.daemon_status_stopped)
                tvDaemonStatusSimple.text = getString(R.string.daemon_status_stopped)
                tvDaemonStatus.setTextColor(Color.RED)
                tvDaemonStatusSimple.setTextColor(Color.RED)
                applyButtonEnabledState(btnStartDaemon, isConnected)
                applyButtonEnabledState(btnKillDaemon, false)
            }
        }
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
            val scrollView = tvLadbLogs.parent as? NestedScrollView
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
            Snackbar.make(rootView, R.string.copy_logs_success, Snackbar.LENGTH_SHORT).show()
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showNotificationPermissionDialog(onPermissionGranted: (() -> Unit)? = null) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.notification_permission_required_title)
            .setMessage(R.string.notification_permission_required_message)
            .setPositiveButton(R.string.grant_permission) { _, _ ->
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
            .setNegativeButton(R.string.cancel) { _, _ ->
                showNotificationDeniedDialog()
            }
            .show()
    }

    private fun showNotificationDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cannot_continue)
            .setMessage(R.string.notification_permission_required_ladb)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showPairingInfoDialog() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.enter_pairing_code_hint)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.open_developer_settings) { _, _ ->
                // Open developer settings
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                
                // After opening settings, proceed with pairing (will show "Waiting..." snackbar)
                proceedWithPairing()
            }
            .show()
    }

    private fun proceedWithPairing() {
        val savedHost = ladbManager.getSavedHost()
        val savedPairingPort = ladbManager.getSavedPairingPort()
        
        if (savedHost.isNullOrBlank() || savedPairingPort <= 0) {
            appendLog(getString(R.string.log_no_pairing_config))
            Snackbar.make(rootView, R.string.waiting_for_pairing_discovery, Snackbar.LENGTH_SHORT).show()
            return
        }

        appendLog(getString(R.string.log_showing_pairing_notif, savedHost, savedPairingPort))
        showPairingCodeNotification()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_ladb_setup)

        val toolbar = findViewById<MaterialToolbar?>(R.id.ladbToolbar)
        toolbar?.setNavigationOnClickListener { finish() }

        try {
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
            val onSurfaceColor = typedValue.data
            toolbar?.setTitleTextColor(onSurfaceColor)
            toolbar?.navigationIcon?.setTint(onSurfaceColor)
        } catch (_: Exception) {
            // ignore
        }

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
        btnPairSimple = findViewById<MaterialButton>(R.id.btnPairSimple)
        btnConnect = findViewById<MaterialButton>(R.id.btnConnect)
        connectProgress = findViewById(R.id.connectProgress)
        connectProgressSimple = findViewById(R.id.connectProgressSimple)
        btnUnpair = findViewById<MaterialButton>(R.id.btnUnpair)
        tvLadbLogs = findViewById<TextView>(R.id.tvLadbLogs)
        val btnClearLogs = findViewById<MaterialButton>(R.id.btnClearLogs)
        val btnCopyLogs = findViewById<MaterialButton>(R.id.btnCopyLogs)
        switchEnableLogs = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchEnableLogs)
        switchAdvancedMode = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchAdvancedMode)
        val cardAdvancedMode = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardAdvancedMode)
        logsContainer = findViewById<LinearLayout>(R.id.logsContainer)
        simpleSetupContainer = findViewById<LinearLayout>(R.id.simpleSetupContainer)
        advancedSetupContainer = findViewById<LinearLayout>(R.id.advancedSetupContainer)
        // Daemon UI
        daemonStatusIndicator = findViewById(R.id.daemonStatusIndicator)
        daemonStatusIndicatorSimple = findViewById(R.id.daemonStatusIndicatorSimple)
        tvDaemonStatus = findViewById(R.id.tvDaemonStatus)
        tvDaemonStatusSimple = findViewById(R.id.tvDaemonStatusSimple)
        actvDaemonCommands = findViewById(R.id.actvDaemonCommands)
        btnStartDaemon = findViewById(R.id.btnStartDaemon)
        btnKillDaemon = findViewById(R.id.btnKillDaemon)

        setupDaemonCommandsDropdown()
        // Load logging preference
        switchEnableLogs.isChecked = getLoggingEnabled()
        
        // Set initial state without animation
        logsContainer.visibility = if (getLoggingEnabled()) View.VISIBLE else View.GONE

        switchEnableLogs.setOnCheckedChangeListener { _, isChecked ->
            setLoggingEnabled(isChecked)
            animateLogsContainer(isChecked)
            if (isChecked) {
                appendLog(getString(R.string.log_logging_enabled))
            }
        }

        // Advanced Mode Toggle
        val prefs = getSharedPreferences("ladb_settings", Context.MODE_PRIVATE)
        val isAdvanced = prefs.getBoolean("advanced_mode", false)
        switchAdvancedMode.isChecked = isAdvanced
        simpleSetupContainer.visibility = if (isAdvanced) View.GONE else View.VISIBLE
        advancedSetupContainer.visibility = if (isAdvanced) View.VISIBLE else View.GONE

        switchAdvancedMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("advanced_mode", isChecked).apply()
            
            val outgoing = if (isChecked) simpleSetupContainer else advancedSetupContainer
            val incoming = if (isChecked) advancedSetupContainer else simpleSetupContainer

            // Cancel any ongoing animations to prevent overlapping transitions
            simpleSetupContainer.animate().cancel()
            advancedSetupContainer.animate().cancel()

            outgoing.animate()
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    outgoing.visibility = View.GONE
                    incoming.alpha = 0f
                    incoming.visibility = View.VISIBLE
                    incoming.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                }
                .start()
        }

        cardAdvancedMode.setOnClickListener {
            switchAdvancedMode.isChecked = !switchAdvancedMode.isChecked
        }

        // Load saved logs
        val savedLogs = loadLogs()
        if (savedLogs.isNotEmpty()) {
            tvLadbLogs.text = savedLogs
            // Scroll to bottom when loading saved logs
            val scrollView = tvLadbLogs.parent as? NestedScrollView
            scrollView?.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }

        ladbManager = LadbManager.getInstance(this)
        daemonManager = PersistentDaemonManager(this)
        // Start daemon status check
        lifecycleScope.launch {
            while (true) {
                val running = withContext(Dispatchers.IO) { daemonManager.isDaemonRunning() }
                if (isDaemonRunning != running) {
                    isDaemonRunning = running
                    updateStatus()
                }
                delay(2000)
            }
        }

        updateStatus()
        appendLog(getString(R.string.log_setup_initialized, tvStatus.text))

        // Auto-initialize components if paired to start port discovery immediately
        if (ladbManager.isPaired() && ladbManager.state != LadbManager.State.CONNECTED) {
            initializeConnectionComponents()
        }

        btnPair.setOnClickListener {
            handlePairingClick()
        }

        btnPairSimple.setOnClickListener {
            val state = ladbManager.state
            val savedHost = ladbManager.getSavedHost()
            val savedPairingPort = ladbManager.getSavedPairingPort()
            
            val isPaired = (savedPairingPort > 0 || ladbManager.isPaired()) && savedHost != null
            val isConnected = state == LadbManager.State.CONNECTED

            if (isConnected) {
                // If connected but daemon not running, start daemon
                if (!isDaemonRunning) {
                    lifecycleScope.launch {
                        performDaemonStart()
                    }
                }
            } else if (isPaired) {
                // If paired, skip to connection
                performConnection()
            } else {
                // Otherwise start pairing
                handlePairingClick()
            }
        }

        btnStartDaemon.setOnClickListener {
            if (isDaemonRunning) {
                appendLog(getString(R.string.log_daemon_already_running))
                return@setOnClickListener
            }
            lifecycleScope.launch {
                performDaemonStart()
            }
        }

        btnKillDaemon.setOnClickListener {
            appendLog(getString(R.string.log_killing_daemon))
            lifecycleScope.launch {
                val cmd = "kill \$(cat /data/local/tmp/daemon.pid 2>/dev/null) 2>/dev/null; pkill -f 'com.arslan.shizuwall.daemon.SystemDaemon'"
                val result = withContext(Dispatchers.IO) {
                    ladbManager.execShell(cmd)
                }
                if (result.exitCode == 0) {
                    appendLog(getString(R.string.log_kill_success))
                } else {
                    appendLog(getString(R.string.log_kill_failed, result.stderr))
                }
            }
        }

        btnConnect.setOnClickListener {
            performConnection()
        }

        btnUnpair.setOnClickListener {
            appendLog(getString(R.string.log_unpairing))
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { ladbManager.clearAllConfig() }
                
                updateStatus()
                appendLog(getString(R.string.log_device_unpaired))
            }
        }

        btnClearLogs.setOnClickListener {
            tvLadbLogs.text = ""
            saveLogs("")
            appendLog(getString(R.string.log_logs_cleared))
        }

        btnCopyLogs.setOnClickListener {
            val logs = tvLadbLogs.text.toString()
            if (logs.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("ladb_logs", logs)
                clipboard.setPrimaryClip(clip)
                Snackbar.make(rootView, R.string.copy_logs_success, Snackbar.LENGTH_SHORT).show()
                appendLog(getString(R.string.log_logs_copied))
            } else {
                Snackbar.make(rootView, R.string.no_logs_to_copy, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun handlePairingClick() {
        // Initialize pairing components when pairing is requested
        initializePairingComponents()
        
        // Clear stale pairing port for fresh pairing
        lifecycleScope.launch {
            ladbManager.clearPairingPort()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                showNotificationPermissionDialog {
                    showPairingInfoDialog()
                }
                return
            }
        } else if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            // For older Android versions, show denied dialog since we can't properly request permission
            showNotificationDeniedDialog()
            return
        }
        
        showPairingInfoDialog()
    }

    private fun performConnection() {
        // Initialize connection components when connecting is requested
        initializeConnectionComponents()

        appendLog(getString(R.string.log_starting_connection))
        lifecycleScope.launch {
            btnConnect.isEnabled = false
            btnPairSimple.isEnabled = false
            connectProgress.visibility = View.VISIBLE
            connectProgressSimple.visibility = View.VISIBLE
            var savedHost = ladbManager.getSavedHost()
            var savedConnectPort = ladbManager.getSavedConnectPort()

            // If no connect config, wait a bit for auto-discovery to complete
            if ((savedHost.isNullOrBlank() || savedConnectPort <= 0)) {
                appendLog(getString(R.string.log_no_connect_config))
                // Wait up to 3 seconds for port discovery to complete
                var waitCount = 0
                while (waitCount < 30) { // 30 * 100ms = 3 seconds
                    delay(100)
                    savedHost = ladbManager.getSavedHost()
                    savedConnectPort = ladbManager.getSavedConnectPort()
                    if (savedHost != null && savedConnectPort > 0) {
                        appendLog(getString(R.string.log_connect_config_found))
                        break
                    }
                    waitCount++
                }
            }

            // If still no config, try scanning detected ports
            if ((savedHost.isNullOrBlank() || savedConnectPort <= 0)) {
                val host = savedHost ?: localIp
                if (!host.isNullOrBlank()) {
                    val hasPorts = synchronized(detectedConnectPorts) {
                        detectedConnectPorts.any { it.first == host }
                    }
                    if (hasPorts) {
                        appendLog(getString(R.string.log_scanning_ports))
                        val foundPort = withContext(Dispatchers.IO) {
                            scanAndSaveConnectPort(host)
                        }
                        if (foundPort > 0) {
                            savedHost = host
                            savedConnectPort = foundPort
                        }
                    }
                }
            }

            if (savedHost.isNullOrBlank() || savedConnectPort <= 0) {
                appendLog(getString(R.string.log_no_connect_config_final))
                Snackbar.make(rootView, R.string.no_connection_config_found, Snackbar.LENGTH_LONG).show()
                btnConnect.isEnabled = true
                btnPairSimple.isEnabled = true
                connectProgress.visibility = View.GONE
                connectProgressSimple.visibility = View.GONE
                return@launch
            }

            val ok = withContext(Dispatchers.IO) {
                appendLog(getString(R.string.log_connecting_to, savedHost, savedConnectPort))
                ladbManager.connect(savedHost, savedConnectPort)
            }
            updateStatus()
            if (!ok) {
                val logs = ladbManager.getLastErrorLog()
                val errorMessage = if (logs.isNullOrBlank()) {
                    getString(R.string.ladb_connection_failed_help)
                } else {
                    logs
                }
                appendLog(getString(R.string.log_connection_failed, errorMessage))
                
                // Clear state so next attempt starts fresh discovery
                withContext(Dispatchers.IO) {
                    ladbManager.clearConnectPort()
                }
                
                showLadbErrorDialog(getString(R.string.ladb_error_title), errorMessage)
            } else {
                appendLog(getString(R.string.log_connection_success))
                // Automatically start daemon after connection (if not already running)
                if (!isDaemonRunning) {
                    // Delay to let connection stabilize
                    delay(2000)
                    performDaemonStart()
                } else {
                    appendLog(getString(R.string.log_daemon_running_skipping))
                }
            }
            btnConnect.isEnabled = true
            btnPairSimple.isEnabled = true
            connectProgress.visibility = View.GONE
            connectProgressSimple.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh status in case pairing happened via notification while we were away.
        tvStatus.post { 
            try {
                val oldStatus = tvStatus.text.toString()
                updateStatus()
                val newStatus = tvStatus.text.toString()
                if (oldStatus != newStatus) {
                    appendLog(getString(R.string.log_status_updated, newStatus))
                }

                // After pairing completes, check if we have detected connect ports but no saved connect config
                // This handles the race condition where connect port is detected before pairing finishes
                val hasPairingConfig = ladbManager.getSavedPairingPort() > 0 || ladbManager.isPaired()
                val hasConnectConfig = ladbManager.getSavedConnectPort() > 0
                
                // Auto-connect if paired but not connected
                if (hasPairingConfig && !hasConnectConfig && 
                    ladbManager.state != LadbManager.State.CONNECTED && 
                    connectProgress.visibility != View.VISIBLE &&
                    connectProgressSimple.visibility != View.VISIBLE) {
                    appendLog("Device paired but not connected. Attempting auto-connection...")
                    performConnection()
                }

                if (hasPairingConfig && !hasConnectConfig) {
                    val savedHost = ladbManager.getSavedHost()
                    if (!savedHost.isNullOrBlank()) {
                        val hasPorts = synchronized(detectedConnectPorts) {
                            detectedConnectPorts.any { it.first == savedHost }
                        }
                        if (hasPorts) {
                            appendLog("Pairing complete but no connect config - scanning detected ports...")
                            scanForOpenPort(savedHost)
                        }
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
        adbPortFinder?.stopDiscovery()
    }

    override fun onPairingPortFound(host: String, port: Int) {
        appendLog("Pairing port detected: $host:$port")
        if (host == "unknown" || port <= 0 || port > 65535) return

        lifecycleScope.launch(Dispatchers.IO) {
            ladbManager.clearConnectPort() // Clear old connect port when new pairing port is found
            ladbManager.saveHost(host)
            val success = ladbManager.savePairingConfig(host, port)
            withContext(Dispatchers.Main) {
                appendLog(if (success) "Pairing config saved" else "Failed to save pairing config")
                showPairingCodeNotification()
            }
        }
    }

    override fun onConnectPortFound(host: String, port: Int) {
        appendLog("Connect port detected: $host:$port")
        synchronized(detectedConnectPorts) {
            if (detectedConnectPorts.none { it.first == host && it.second == port }) {
                detectedConnectPorts.add(host to port)
            }
        }
        
        val savedHost = ladbManager.getSavedHost()
        if (host == localIp || (savedHost != null && host == savedHost)) {
            handler.removeCallbacksAndMessages(null) 
            handler.postDelayed({ scanForOpenPort(host) }, 2000)
        }
    }

    /**
     * Suspend version of port scanning that returns the found port.
     * Must be called from a coroutine.
     */
    private suspend fun scanAndSaveConnectPort(host: String): Int {
        val ports = synchronized(detectedConnectPorts) {
            detectedConnectPorts.filter { it.first == host }.map { it.second }.sorted()
        }
        
        if (ports.isEmpty()) {
            withContext(Dispatchers.Main) { appendLog("No detected ports for host $host") }
            return -1
        }

        var realPort = -1
        for (port in ports) {
            try {
                val socket = Socket()
                withContext(Dispatchers.IO) {
                    socket.connect(InetSocketAddress(host, port), 200)
                }
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
            withContext(Dispatchers.Main) { appendLog("No open connect port found from detected ports for $host") }
        }
        return realPort
    }

    private fun scanForOpenPort(host: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val ports = synchronized(detectedConnectPorts) {
                detectedConnectPorts.filter { it.first == host }.map { it.second }.sorted()
            }
            
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
                        val hasPairingConfig = ladbManager.getSavedPairingPort() > 0 || ladbManager.isPaired()
                        if (hasPairingConfig && ladbManager.getSavedHost() != null) {
                            val success = ladbManager.saveConnectConfig(host, realPort)
                            if (success) {
                                appendLog("Connect config saved")
                                updateStatus()
                                
                                // Auto-connect if we're not currently connected
                                if (ladbManager.state != LadbManager.State.CONNECTED) {
                                    appendLog("Discovered port - attempting auto-connection...")
                                    performConnection()
                                }
                            } else {
                                appendLog("Failed to save connect config")
                            }
                        } else {
                            appendLog("Connect port found but not saving config (device not paired)")
                        }
                    }
                }
            }
        }
    }

    private suspend fun performDaemonStart() {
        withContext(Dispatchers.Main) {
            appendLog("Starting daemon installation...")
            btnStartDaemon.isEnabled = false
            btnPairSimple.isEnabled = false
            btnStartDaemon.text = getString(R.string.daemon_installing)
            connectProgressSimple.visibility = View.VISIBLE
            
            // Small delay to ensure connection is stable
            delay(500)
            
            val success = withContext(Dispatchers.IO) {
                // Kill any existing daemon first
                try {
                    val pidResult = daemonManager.executeCommand("cat /data/local/tmp/daemon.pid 2>/dev/null")
                    if (pidResult.isNotBlank()) {
                        val pid = pidResult.trim()
                        daemonManager.executeCommand("kill $pid 2>/dev/null || kill -9 $pid 2>/dev/null || true")
                    }
                } catch (e: Exception) {
                    // ignore
                }
                
                daemonManager.installDaemon { progress ->
                    appendLog("Daemon: $progress")
                }
            }
            
            if (success) {
                appendLog("Daemon started successfully!")
                isDaemonRunning = true
            } else {
                appendLog("Daemon failed to start. Check LADB connection.")
                isDaemonRunning = false
            }
            
            btnStartDaemon.text = getString(R.string.daemon_start)
            btnPairSimple.isEnabled = true
            connectProgressSimple.visibility = View.GONE
            updateStatus()
        }
    }

    private fun setupDaemonCommandsDropdown() {
        val commands = arrayOf(
            "ping",
            "status",
            "id",
            "ps -A | grep daemon",
            "ls -l /data/local/tmp",
            "cat /data/local/tmp/daemon.log",
            "getprop ro.product.model",
            "uname -a",
            "pm list packages -3",
            "dumpsys battery"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, commands)
        actvDaemonCommands.setAdapter(adapter)
        actvDaemonCommands.setOnItemClickListener { parent, _, position, _ ->
            val selectedCommand = parent.getItemAtPosition(position) as String
            executeDaemonCommand(selectedCommand)
        }
    }

    private fun executeDaemonCommand(cmd: String) {
        if (cmd.isBlank()) return
        
        lifecycleScope.launch {
            appendLog("Executing daemon command: $cmd")
            
            val result = withContext(Dispatchers.IO) {
                when (cmd) {
                    "ping" -> {
                        // Health check - verify daemon is responding
                        try {
                            val response = daemonManager.executeCommand("ping")
                            if (response.trim() == "pong") {
                                "✓ Daemon is alive and responding!"
                            } else {
                                "✗ Unexpected response: $response"
                            }
                        } catch (e: Exception) {
                            "✗ Ping failed: ${e.message}"
                        }
                    }
                    else -> {
                        daemonManager.executeCommand(cmd)
                    }
                }
            }
            appendLog("Daemon Result:\n$result")

            // Update daemon status after ping or kill
            if (cmd == "ping") {
                isDaemonRunning = result.contains("✓")
                updateStatus()
            } else if (cmd == "kill daemon") {
                isDaemonRunning = false
                updateStatus()
            }
        }
    }

    private fun initializePairingComponents() {
        // Initialize components needed for pairing
        if (localIp == null) {
            localIp = detectLocalIpv4OrNull()
        }
        if (adbPortFinder == null) {
            adbPortFinder = AdbPortFinder(this, this)
            synchronized(detectedConnectPorts) {
                detectedConnectPorts.clear()
            }
            adbPortFinder?.startDiscovery()
        }
    }

    private fun initializeConnectionComponents() {
        // Initialize components needed for connection
        if (localIp == null) {
            localIp = detectLocalIpv4OrNull()
        }
        if (adbPortFinder == null) {
            adbPortFinder = AdbPortFinder(this, this)
            synchronized(detectedConnectPorts) {
                detectedConnectPorts.clear()
            }
            adbPortFinder?.startDiscovery()
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
                Snackbar.make(rootView, R.string.notification_permission_granted, Snackbar.LENGTH_SHORT).show()
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
