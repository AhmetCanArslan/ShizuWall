package com.arslan.shizuwall

import android.Manifest
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
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.arslan.shizuwall.ladb.LadbManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.ConnectivityManager
import android.net.LinkProperties
import java.net.Inet4Address
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.arslan.shizuwall.receivers.LadbPairingCodeReceiver

class LadbSetupActivity : AppCompatActivity() {

    private lateinit var rootView: View
    private lateinit var tvStatus: TextView
    private lateinit var etHostPort: TextInputEditText
    private lateinit var etPairingPort: TextInputEditText
    private lateinit var etPairingCode: TextInputEditText
    private lateinit var btnUnpair: MaterialButton

    private lateinit var ladbManager: LadbManager

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
        etHostPort = findViewById<TextInputEditText>(R.id.etHostPort)
        etPairingPort = findViewById<TextInputEditText>(R.id.etPairingPort)
        etPairingCode = findViewById<TextInputEditText>(R.id.etPairingCode)
        val btnPair = findViewById<MaterialButton>(R.id.btnPair)
        val btnConnect = findViewById<MaterialButton>(R.id.btnConnect)
        btnUnpair = findViewById<MaterialButton>(R.id.btnUnpair)
        val btnStartService = findViewById<MaterialButton>(R.id.btnStartService)

        ladbManager = LadbManager.getInstance(this)

        fun createPairingNotificationChannel() {
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

        fun showPairingCodeNotification() {
            // Android 13+ requires notification permission.
            if (Build.VERSION.SDK_INT >= 33) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Snackbar.make(rootView, getString(R.string.ladb_notification_permission_required), Snackbar.LENGTH_LONG).show()
                    return
                }
            }

            createPairingNotificationChannel()

            val detailsLabel = getString(R.string.ladb_pairing_details_hint_port_code)
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
                .setContentText(getString(R.string.ladb_pairing_notification_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .addAction(action)
                .build()

            NotificationManagerCompat.from(this)
                .notify(LadbPairingCodeReceiver.NOTIFICATION_ID, notification)
        }

        fun parseHostPort(raw: String): Pair<String, Int>? {
            val input = raw.trim()
            if (input.isEmpty()) return null

            // Allow "PORT" shorthand.
            input.toIntOrNull()?.let { p ->
                if (p > 0) return "127.0.0.1" to p
            }

            val parts = input.split(":")
            if (parts.size < 2) return null

            val host = parts[0].trim().ifEmpty { "127.0.0.1" }
            val port = parts[1].trim().toIntOrNull() ?: return null
            if (port <= 0) return null
            return host to port
        }

        fun parsePort(raw: String): Int? {
            val p = raw.trim().toIntOrNull() ?: return null
            return if (p > 0) p else null
        }

        fun showLadbErrorDialog(title: String, logs: String) {
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

        fun updateStatus() {
            tvStatus.text = when (ladbManager.state) {
                LadbManager.State.UNCONFIGURED -> getString(R.string.ladb_status_unconfigured)
                LadbManager.State.PAIRED -> getString(R.string.ladb_status_paired)
                LadbManager.State.CONNECTED -> getString(R.string.ladb_status_connected)
                LadbManager.State.DISCONNECTED -> getString(R.string.ladb_status_disconnected)
                LadbManager.State.ERROR -> "Error"
            }
            btnUnpair.visibility = if (ladbManager.state == LadbManager.State.PAIRED || ladbManager.state == LadbManager.State.CONNECTED) View.VISIBLE else View.GONE
        }

        populateFieldsIfBlank()
        updateStatus()

        btnPair.setOnClickListener {
            val parsed = parseHostPort(etHostPort.text?.toString().orEmpty())
            if (parsed == null) {
                showLadbErrorDialog(getString(R.string.ladb_error_title), getString(R.string.ladb_invalid_host_port))
                return@setOnClickListener
            }
            val host = parsed.first

            val pairingPort = parsePort(etPairingPort.text?.toString().orEmpty())
            val code = etPairingCode.text?.toString().orEmpty().trim()
            if (pairingPort == null || code.isEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    // Save host (and pairing port if present) so the receiver can complete pairing.
                    ladbManager.saveHost(host)
                    if (pairingPort != null) {
                        ladbManager.savePairingConfig(host, pairingPort)
                    }
                }

                showPairingCodeNotification()
                Snackbar.make(rootView, getString(R.string.ladb_pairing_notification_shown), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            lifecycleScope.launch {
                btnPair.isEnabled = false
                val ok = withContext(Dispatchers.IO) { ladbManager.pair(host, pairingPort, code) }
                updateStatus()
                if (!ok) {
                    val logs = ladbManager.getLastErrorLog() ?: "Pairing failed (no logs)."
                    showLadbErrorDialog(getString(R.string.ladb_error_title), logs)
                }
                btnPair.isEnabled = true
            }
        }

        btnConnect.setOnClickListener {
            lifecycleScope.launch {
                btnConnect.isEnabled = false
                val parsed = parseHostPort(etHostPort.text?.toString().orEmpty())
                val ok = withContext(Dispatchers.IO) {
                    if (parsed != null) {
                        // Persist config and connect using provided host:port.
                        ladbManager.saveConnectConfig(parsed.first, parsed.second)
                        ladbManager.connect(parsed.first, parsed.second)
                    } else {
                        // Fall back to previously saved config.
                        ladbManager.connect()
                    }
                }
                updateStatus()
                if (!ok) {
                    val logs = ladbManager.getLastErrorLog() ?: "Connect failed (no logs)."
                    showLadbErrorDialog(getString(R.string.ladb_error_title), logs)
                }
                btnConnect.isEnabled = true
            }
        }

        btnUnpair.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { ladbManager.disconnect() }
                updateStatus()
            }
        }

        btnStartService.setOnClickListener {
            val intent = android.content.Intent(this, com.arslan.shizuwall.services.LadbService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Snackbar.make(rootView, "Service started", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        populateFieldsIfBlank()
        // Refresh status in case pairing happened via notification while we were away.
        tvStatus.post { 
            try {
                tvStatus.text = when (ladbManager.state) {
                    LadbManager.State.UNCONFIGURED -> getString(R.string.ladb_status_unconfigured)
                    LadbManager.State.PAIRED -> getString(R.string.ladb_status_paired)
                    LadbManager.State.CONNECTED -> getString(R.string.ladb_status_connected)
                    LadbManager.State.DISCONNECTED -> getString(R.string.ladb_status_disconnected)
                    LadbManager.State.ERROR -> "Error"
                }
                btnUnpair.visibility = if (ladbManager.state == LadbManager.State.PAIRED || ladbManager.state == LadbManager.State.CONNECTED) View.VISIBLE else View.GONE
            } catch (_: Exception) {
                // ignore
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

    private fun populateFieldsIfBlank() {
        val savedHost = ladbManager.getSavedHost()

        val hostToUse = savedHost?.takeIf { it.isNotBlank() }
            ?: detectLocalIpv4OrNull()
            ?: "127.0.0.1"

        if (etHostPort.text.isNullOrBlank()) {
            etHostPort.setText(hostToUse)
        }

        // Never auto-fill pairing code.
    }

    companion object {
        private const val PAIRING_CHANNEL_ID = "ladb_pairing"
    }
}
