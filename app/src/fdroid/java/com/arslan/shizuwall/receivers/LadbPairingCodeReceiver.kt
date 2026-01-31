package com.arslan.shizuwall.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LadbPairingCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // No-op for F-Droid build
    }
    
    companion object {
        const val ACTION_LADB_PAIRING_CODE = "com.arslan.shizuwall.ACTION_LADB_PAIRING_CODE"
    }
}
