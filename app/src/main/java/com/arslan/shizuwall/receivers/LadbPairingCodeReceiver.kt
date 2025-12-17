package com.arslan.shizuwall.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.arslan.shizuwall.ladb.LadbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LadbPairingCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LADB_PAIRING_CODE) return

        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val code = results.getCharSequence(KEY_REMOTE_INPUT_CODE)?.toString()?.trim().orEmpty()
        val portStr = results.getCharSequence(KEY_REMOTE_INPUT_PORT)?.toString()?.trim().orEmpty()
        val port = portStr.toIntOrNull() ?: -1
        if (code.isEmpty() || port <= 0) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ladb = LadbManager.getInstance(context)
                ladb.savePairingPortUsingSavedHost(port)
                val ok = ladb.pairUsingSavedConfig(code)

                // Dismiss the pairing request notification either way.
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

                // Optional: on failure, keep logs for the UI dialog; notification is dismissed.
                // (We avoid posting extra notifications to keep UX minimal.)
                if (!ok) {
                    // no-op
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_LADB_PAIRING_CODE = "com.arslan.shizuwall.ACTION_LADB_PAIRING_CODE"
        const val KEY_REMOTE_INPUT_PORT = "pairing_port"
        const val KEY_REMOTE_INPUT_CODE = "pairing_code"
        const val NOTIFICATION_ID = 2201
    }
}
