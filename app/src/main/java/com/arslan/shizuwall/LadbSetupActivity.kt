package com.arslan.shizuwall

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

class LadbSetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ladb_setup)

        val toolbar = findViewById<MaterialToolbar?>(R.id.ladbToolbar)
        toolbar?.setNavigationOnClickListener { finish() }

        // Respect system bars (status/navigation) similar to SettingsActivity
        val root = findViewById<android.view.View>(R.id.ladbSetupRoot) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
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
        val tvStatus = findViewById<TextView>(R.id.tvLadbStatus)
        val etHostPort = findViewById<TextInputEditText>(R.id.etHostPort)
        val etPairingPort = findViewById<TextInputEditText>(R.id.etPairingPort)
        val etPairingCode = findViewById<TextInputEditText>(R.id.etPairingCode)
        val btnPair = findViewById<MaterialButton>(R.id.btnPair)
        val btnConnect = findViewById<MaterialButton>(R.id.btnConnect)
        val btnUnpair = findViewById<MaterialButton>(R.id.btnUnpair)
        val btnStartService = findViewById<MaterialButton>(R.id.btnStartService)

        val ladbManager = LadbManager.getInstance(this)

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
                Snackbar.make(root, getString(R.string.copy) + "!", Snackbar.LENGTH_SHORT).show()
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

        updateStatus()

        btnPair.setOnClickListener {
            val parsed = parseHostPort(etHostPort.text?.toString().orEmpty())
            if (parsed == null) {
                showLadbErrorDialog(getString(R.string.ladb_error_title), getString(R.string.ladb_invalid_host_port))
                return@setOnClickListener
            }
            val host = parsed.first

            val pairingPort = parsePort(etPairingPort.text?.toString().orEmpty())
            if (pairingPort == null) {
                showLadbErrorDialog(getString(R.string.ladb_error_title), getString(R.string.ladb_invalid_pairing_port))
                return@setOnClickListener
            }

            val code = etPairingCode.text?.toString().orEmpty().trim()
            if (code.isEmpty()) {
                showLadbErrorDialog(getString(R.string.ladb_error_title), getString(R.string.ladb_pairing_code_required))
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
            Snackbar.make(root, "Service started", Snackbar.LENGTH_SHORT).show()
        }
    }
}
