package com.arslan.shizuwall.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.arslan.shizuwall.services.LadbService

class LadbServiceRestarter : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("ladb_logs", Context.MODE_PRIVATE)
        val shouldRun = prefs.getBoolean("service_should_run", false)
        
        if (shouldRun) {
            val serviceIntent = Intent(context, LadbService::class.java)
            context.startService(serviceIntent)
        }
    }
}