package com.arslan.shizuwall.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.arslan.shizuwall.R
import com.arslan.shizuwall.LadbSetupActivity
import com.arslan.shizuwall.ladb.LadbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LadbPairingCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LADB_PAIRING_CODE) return

        val results = RemoteInput.getResultsFromIntent(intent) ?: return

        val rawDetails = results.getCharSequence(KEY_REMOTE_INPUT_DETAILS)?.toString()?.trim().orEmpty()
        val legacyCode = results.getCharSequence(KEY_REMOTE_INPUT_CODE)?.toString()?.trim().orEmpty()
        val legacyPortStr = results.getCharSequence(KEY_REMOTE_INPUT_PORT)?.toString()?.trim().orEmpty()

        val pendingResult = goAsync()

        fun parseDetails(input: String): Pair<Int?, String?> {
            val s = input.trim()
            if (s.isEmpty()) return null to null

            // Accept: "37133 123456" or "37133:123456" or "37133,123456".
            val tokens = s.split(Regex("[\\s,:]+"), limit = 2).filter { it.isNotBlank() }
            if (tokens.size != 2) return null to null

            val first = tokens[0]
            val second = tokens[1]

            val port: Int?
            val code: String?

            if (first.length == 6 && first.toIntOrNull() != null && second.toIntOrNull() != null && second.toInt() in 1..65535) {
                // Assume first is 6-digit code, second is port
                code = first
                port = second.toInt()
            } else if (first.toIntOrNull() != null && first.toInt() in 1..65535) {
                // Assume first is port, second is code
                port = first.toInt()
                code = second
            } else {
                return null to null
            }

            return port to code
        }

        fun postResultNotification(title: String, text: String) {
            val openIntent = Intent(context, LadbSetupActivity::class.java)
            val contentFlags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            val contentIntent = PendingIntent.getActivity(context, 0, openIntent, contentFlags)

            val notification = NotificationCompat.Builder(context, PAIRING_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .build()

            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID, notification)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ladb = LadbManager.getInstance(context)

                val (detailsPort, detailsCode) = parseDetails(rawDetails)
                val port = detailsPort
                    ?: legacyPortStr.toIntOrNull()?.takeIf { it > 0 }
                val code = detailsCode
                    ?.takeIf { it.isNotBlank() }
                    ?: legacyCode.takeIf { it.isNotBlank() }
                    ?: rawDetails.takeIf { it.isNotBlank() }

                if (code.isNullOrBlank()) {
                    postResultNotification(
                        context.getString(R.string.ladb_pairing_notification_title),
                        context.getString(R.string.ladb_pairing_result_missing_code)
                    )
                    return@launch
                }

                if (port != null) {
                    ladb.savePairingPortUsingSavedHost(port)
                }

                // If the user entered code-only but we still don't have a pairing port, fail fast
                // with a clear message (otherwise pairing will always fail).
                if (ladb.getSavedPairingPort() <= 0) {
                    postResultNotification(
                        context.getString(R.string.ladb_pairing_result_failed_title),
                        context.getString(R.string.ladb_pairing_result_missing_port)
                    )
                    return@launch
                }

                val ok = ladb.pairUsingSavedConfig(code)
                if (ok) {
                    postResultNotification(
                        context.getString(R.string.ladb_pairing_result_success_title),
                        context.getString(R.string.ladb_pairing_result_success_text)
                    )
                } else {
                    val log = ladb.getLastErrorLog().orEmpty()
                    val summary = log.lineSequence()
                        .firstOrNull { it.startsWith("exception=") }
                        ?.removePrefix("exception=")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.ladb_pairing_result_failed_text)

                    postResultNotification(
                        context.getString(R.string.ladb_pairing_result_failed_title),
                        summary
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_LADB_PAIRING_CODE = "com.arslan.shizuwall.ACTION_LADB_PAIRING_CODE"
        const val KEY_REMOTE_INPUT_DETAILS = "pairing_details"
        const val KEY_REMOTE_INPUT_PORT = "pairing_port"
        const val KEY_REMOTE_INPUT_CODE = "pairing_code"
        const val NOTIFICATION_ID = 2201
        const val PAIRING_CHANNEL_ID = "ladb_pairing"
    }
}
