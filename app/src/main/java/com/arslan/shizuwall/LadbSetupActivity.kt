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
        val btnPair = findViewById<MaterialButton>(R.id.btnPair)
        val btnConnect = findViewById<MaterialButton>(R.id.btnConnect)
        val btnUnpair = findViewById<MaterialButton>(R.id.btnUnpair)
        val btnStartService = findViewById<MaterialButton>(R.id.btnStartService)

        val ladbManager = LadbManager.getInstance(this)

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
            val input = etHostPort.text?.toString().orEmpty()
            val parts = input.split(":")
            val host = if (parts.isNotEmpty()) parts[0] else "localhost"
            val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 5555 else 5555
            
            lifecycleScope.launch {
                btnPair.isEnabled = false
                val ok = withContext(Dispatchers.IO) { ladbManager.pair(host, port, null) }
                updateStatus()
                if (!ok) {
                    Snackbar.make(root, "Pairing failed", Snackbar.LENGTH_SHORT).show()
                }
                btnPair.isEnabled = true
            }
        }

        btnConnect.setOnClickListener {
            lifecycleScope.launch {
                btnConnect.isEnabled = false
                val ok = withContext(Dispatchers.IO) { ladbManager.connect() }
                updateStatus()
                if (!ok) {
                    Snackbar.make(root, "Connect failed", Snackbar.LENGTH_SHORT).show()
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
