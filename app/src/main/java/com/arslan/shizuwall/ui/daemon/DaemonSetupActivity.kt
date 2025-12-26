package com.arslan.shizuwall.ui.daemon

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.arslan.shizuwall.databinding.ActivityDaemonSetupBinding
import kotlinx.coroutines.launch

class DaemonSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDaemonSetupBinding
    private val viewModel: DaemonViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDaemonSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupListeners() {
        binding.btnStartDaemon.setOnClickListener {
            viewModel.installDaemon()
        }

        binding.btnRunCustomCommand.setOnClickListener {
            val cmd = binding.etCustomCommand.text?.toString()
            if (!cmd.isNullOrBlank()) {
                viewModel.runCommand(cmd)
            } else {
                Toast.makeText(this, "Enter a command", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnViewDaemonLogs.setOnClickListener {
            viewModel.viewDaemonLogs()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.daemonRunning.collect { isRunning ->
                        updateStatusUi(isRunning)
                    }
                }
                launch {
                    viewModel.logs.collect { logs ->
                        binding.tvLogs.text = logs
                        // Auto scroll to bottom
                        binding.tvLogs.post {
                            val scrollAmount = binding.tvLogs.layout?.getLineTop(binding.tvLogs.lineCount) ?: 0
                            val height = binding.tvLogs.height
                            if (scrollAmount > height) {
                                binding.tvLogs.scrollTo(0, scrollAmount - height)
                            }
                        }
                    }
                }
                launch {
                    viewModel.isInstalling.collect { isInstalling ->
                        binding.btnStartDaemon.isEnabled = !isInstalling
                        if (isInstalling) {
                            binding.btnStartDaemon.text = "Installing..."
                        } else {
                            binding.btnStartDaemon.text = "Start Daemon"
                        }
                    }
                }
            }
        }
    }

    private fun updateStatusUi(isRunning: Boolean) {
        if (isRunning) {
            binding.statusIndicator.setBackgroundColor(Color.GREEN)
            binding.statusText.text = "Running"
            binding.statusText.setTextColor(Color.GREEN)
        } else {
            binding.statusIndicator.setBackgroundColor(Color.RED)
            binding.statusText.text = "Stopped"
            binding.statusText.setTextColor(Color.RED)
        }
    }
}
