package com.arslan.shizuwall.profiles

import android.content.Context
import android.content.Intent
import com.arslan.shizuwall.model.Profile
import com.arslan.shizuwall.receivers.ProfileControlReceiver
import com.arslan.shizuwall.shell.ShellExecutorProvider
import com.arslan.shizuwall.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object ProfileActivator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun activateAndEnable(context: Context, profile: Profile) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, ProfileControlReceiver::class.java).apply {
            action = MainActivity.ACTION_PROFILE_CONTROL
            putExtra(MainActivity.EXTRA_PROFILE_ID, profile.id)
            putExtra(ProfileControlReceiver.EXTRA_FORCE_ENABLE, true)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_RECEIVER_FOREGROUND)
        }
        appContext.sendBroadcast(intent)
    }

    fun activateFromTile(context: Context, profile: Profile) {
        val appContext = context.applicationContext
        val collapseShade = appContext
            .getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(MainActivity.KEY_TILE_COLLAPSE_SHADE, true)

        activateAndEnable(appContext, profile)

        if (collapseShade) {
            scope.launch {
                runCatching {
                    ShellExecutorProvider.forContext(appContext).exec("cmd statusbar collapse")
                }
            }
        }
    }
}
