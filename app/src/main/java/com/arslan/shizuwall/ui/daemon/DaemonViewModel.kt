package com.arslan.shizuwall.ui.daemon

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arslan.shizuwall.daemon.PersistentDaemonManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DaemonViewModel(application: Application) : AndroidViewModel(application) {

    private val daemonManager = PersistentDaemonManager(application)

    private val _daemonRunning = MutableStateFlow(false)
    val daemonRunning: StateFlow<Boolean> = _daemonRunning

    private val _logs = MutableStateFlow("Ready...")
    val logs: StateFlow<String> = _logs

    private val _isInstalling = MutableStateFlow(false)
    val isInstalling: StateFlow<Boolean> = _isInstalling

    init {
        startStatusCheck()
    }

    private fun startStatusCheck() {
        viewModelScope.launch {
            while (true) {
                _daemonRunning.value = daemonManager.isDaemonRunning()
                delay(2000)
            }
        }
    }

    fun installDaemon() {
        if (_isInstalling.value) return
        
        viewModelScope.launch {
            _isInstalling.value = true
            _logs.value = "Starting installation...\n"
            
            val success = daemonManager.installDaemon { progress ->
                _logs.value += "$progress\n"
            }
            
            if (success) {
                _logs.value += "Installation successful!\n"
            } else {
                _logs.value += "Installation failed.\n"
            }
            
            _isInstalling.value = false
            _daemonRunning.value = daemonManager.isDaemonRunning()
        }
    }

    fun runCommand(command: String) {
        viewModelScope.launch {
            _logs.value += "Executing: $command\n"
            val result = daemonManager.executeCommand(command)
            _logs.value += "Result:\n$result\n"
        }
    }

    fun viewDaemonLogs() {
        viewModelScope.launch {
            _logs.value += "Reading daemon logs...\n"
            // We use LADB to read the log file since the daemon might not be able to read its own log file if it's locked
            // or we can just try to read it via the daemon itself.
            val result = daemonManager.executeCommand("cat /data/local/tmp/daemon.log")
            _logs.value += "Daemon Logs:\n$result\n"
        }
    }
}
