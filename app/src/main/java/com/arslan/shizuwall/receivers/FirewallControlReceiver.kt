package com.arslan.shizuwall.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.arslan.shizuwall.FirewallMode
import com.arslan.shizuwall.R
import com.arslan.shizuwall.ui.MainActivity
import com.arslan.shizuwall.utils.FirewallUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class FirewallControlReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "FirewallControl"
        private const val SHIZUKU_WAIT_MAX_ATTEMPTS = 10
        private const val SHIZUKU_WAIT_DELAY_MS = 300L
        const val EXTRA_AUTOMATION_EVENT = "com.arslan.shizuwall.EXTRA_AUTOMATION_EVENT"
    }

    private suspend fun waitForShizukuBinder() {
        repeat(SHIZUKU_WAIT_MAX_ATTEMPTS) { attempt ->
            if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return
            if (attempt < SHIZUKU_WAIT_MAX_ATTEMPTS - 1) delay(SHIZUKU_WAIT_DELAY_MS)
        }
        android.util.Log.w(TAG, "Shizuku binder not available after $SHIZUKU_WAIT_MAX_ATTEMPTS attempts")
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MainActivity.ACTION_FIREWALL_CONTROL) return

        val pending = goAsync()
        val enabled = intent.getBooleanExtra(MainActivity.EXTRA_FIREWALL_ENABLED, false)
        val csv = intent.getStringExtra(MainActivity.EXTRA_PACKAGES_CSV)
        val automationEvent = intent.getBooleanExtra(EXTRA_AUTOMATION_EVENT, false)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                if (prefs.getString(MainActivity.KEY_WORKING_MODE, "SHIZUKU") == "SHIZUKU") {
                    waitForShizukuBinder()
                }
                val keys = csv?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                android.util.Log.d(TAG, "state=$enabled delta=${keys?.size ?: "global"}")

                if (keys == null) {
                    FirewallUtils.toggle(context, prefs, enabled)
                    return@launch
                }
                val ready = withContext(Dispatchers.Main) {
                    FirewallUtils.checkBackendReady(context, showToast = !automationEvent)
                }
                if (!ready) return@launch
                val ok = FirewallUtils.applyDelta(context, prefs, keys, enabled, syncSelection = !automationEvent)
                if (!ok && automationEvent) {
                    warnScreenLockFailure(context, FirewallUtils.firewallMode(prefs))
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.error_format, t.message), Toast.LENGTH_SHORT).show()
                }
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    private suspend fun warnScreenLockFailure(context: Context, mode: FirewallMode) {
        if (mode != FirewallMode.SCREEN_LOCK_MODE && mode != FirewallMode.HYBRID) return
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.screen_lock_mode_operation_failed), Toast.LENGTH_SHORT).show()
        }
    }
}
