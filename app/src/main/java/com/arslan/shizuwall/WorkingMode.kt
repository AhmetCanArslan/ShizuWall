package com.arslan.shizuwall

import android.content.Context
import com.arslan.shizuwall.ui.MainActivity

enum class WorkingMode(val icon: Int) {
    SHIZUKU(R.drawable.ic_shizuku),
    LADB(R.drawable.adb_24px),
    ROOT(R.drawable.hashtag_24px),
    VPN(R.drawable.ic_picon_vpn_key);

    companion object {
        fun of(context: Context): WorkingMode = fromName(
            context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE).getString(MainActivity.KEY_WORKING_MODE, null)
        )

        fun fromName(name: String?): WorkingMode {
            return try {
                if (name == null) SHIZUKU else valueOf(name)
            } catch (e: Exception) {
                SHIZUKU
            }
        }
    }
}
