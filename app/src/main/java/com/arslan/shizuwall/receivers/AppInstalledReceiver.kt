package com.arslan.shizuwall.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arslan.shizuwall.services.AppMonitorService
import com.arslan.shizuwall.ui.MainActivity

class AppInstalledReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MainActivity.ACTION_APP_INSTALLED) return
        val csv = intent.getStringExtra(MainActivity.EXTRA_PACKAGES_CSV) ?: return
        csv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { AppMonitorService.showNewAppNotification(context, it) }
    }
}
