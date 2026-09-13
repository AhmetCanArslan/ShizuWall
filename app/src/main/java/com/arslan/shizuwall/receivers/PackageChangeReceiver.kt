package com.arslan.shizuwall.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.arslan.shizuwall.utils.ShizukuPackageResolver

class PackageChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PackageChangeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d(TAG, "onReceive action=$action")

        when (action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                val packageName = intent?.data?.schemeSpecificPart
                Log.d(TAG, "Package change detected: $action, package=$packageName")
                
                ShizukuPackageResolver.clearCache()
                Log.d(TAG, "Cleared ShizukuPackageResolver cache due to package change")
            }
        }
    }
}
