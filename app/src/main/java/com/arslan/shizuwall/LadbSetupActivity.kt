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

        fun updateStatus() {
            tvStatus.text = when (LadbManager.status) {
                LadbManager.Status.UNCONFIGURED -> getString(R.string.ladb_status_unconfigured)
                LadbManager.Status.PAIRED -> getString(R.string.ladb_status_paired)
                LadbManager.Status.CONNECTED -> getString(R.string.ladb_status_connected)
                LadbManager.Status.DISCONNECTED -> getString(R.string.ladb_status_disconnected)
            }
            btnUnpair.visibility = if (LadbManager.status == LadbManager.Status.PAIRED || LadbManager.status == LadbManager.Status.CONNECTED) View.VISIBLE else View.GONE
        }

        updateStatus()

        btnPair.setOnClickListener {
            val code = etHostPort.text?.toString().orEmpty()
            lifecycleScope.launch {
                btnPair.isEnabled = false
                val ok = withContext(Dispatchers.IO) { LadbManager.pair() }
                updateStatus()
                if (!ok) {
                    Snackbar.make(root, "Pairing failed (not implemented)", Snackbar.LENGTH_SHORT).show()
                }
                btnPair.isEnabled = true
            }
        }

        btnConnect.setOnClickListener {
            lifecycleScope.launch {
                btnConnect.isEnabled = false
                val ok = withContext(Dispatchers.IO) { LadbManager.connect() }
                updateStatus()
                if (!ok) {
                    Snackbar.make(root, "Connect failed (not implemented)", Snackbar.LENGTH_SHORT).show()
                }
                btnConnect.isEnabled = true
            }
        }

        btnUnpair.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { LadbManager.disconnect() }
                // Reset to unconfigured for now
                LadbManager.status = LadbManager.Status.UNCONFIGURED
                updateStatus()
            }
        }

        btnStartService.setOnClickListener {
            Snackbar.make(root, "Start service not implemented", Snackbar.LENGTH_SHORT).show()
        }
    }
}
