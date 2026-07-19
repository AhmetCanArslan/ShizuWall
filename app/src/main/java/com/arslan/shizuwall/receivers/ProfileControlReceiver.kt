package com.arslan.shizuwall.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.arslan.shizuwall.R
import com.arslan.shizuwall.profiles.ProfilesStore
import com.arslan.shizuwall.shell.ShellExecutorProvider
import com.arslan.shizuwall.ui.MainActivity
import com.arslan.shizuwall.utils.FirewallUtils
import com.arslan.shizuwall.widgets.FirewallWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileControlReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ProfileControl"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MainActivity.ACTION_PROFILE_CONTROL) return

        val name = intent.getStringExtra(MainActivity.EXTRA_PROFILE_NAME)
        val id = intent.getStringExtra(MainActivity.EXTRA_PROFILE_ID)
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profile = when {
                    !id.isNullOrBlank() -> ProfilesStore.getById(context, id)
                    !name.isNullOrBlank() -> ProfilesStore.getByName(context, name)
                    else -> null
                }

                if (profile == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.profile_not_found, name ?: id ?: ""),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                val wasEnabled = FirewallUtils.loadFirewallEnabled(prefs)
                val oldActive = prefs.getStringSet(MainActivity.KEY_ACTIVE_PACKAGES, emptySet())?.toList() ?: emptyList()

                ProfilesStore.writeSelectionFromProfile(context, profile)

                val autoEnable = prefs.getBoolean(MainActivity.KEY_AUTO_ENABLE_ON_PROFILE_ACTIVATE, false)

                if (wasEnabled || autoEnable) {
                    val executor = ShellExecutorProvider.forContext(context)
                    for (pkg in oldActive) {
                        try {
                            executor.exec("cmd connectivity set-package-networking-enabled true $pkg")
                        } catch (_: Throwable) {
                        }
                    }

                    val enableIntent = Intent(context, FirewallControlReceiver::class.java).apply {
                        action = MainActivity.ACTION_FIREWALL_CONTROL
                        putExtra(MainActivity.EXTRA_FIREWALL_ENABLED, true)
                        putExtra(FirewallControlReceiver.EXTRA_AUTOMATION_EVENT, true)
                    }
                    context.sendBroadcast(enableIntent)
                } else {
                    val updateIntent = Intent(context, FirewallWidgetProvider::class.java).apply {
                        action = MainActivity.ACTION_FIREWALL_STATE_CHANGED
                    }
                    context.sendBroadcast(updateIntent)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.profile_switched, profile.name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "Failed to activate profile", t)
            } finally {
                pending.finish()
            }
        }
    }
}
