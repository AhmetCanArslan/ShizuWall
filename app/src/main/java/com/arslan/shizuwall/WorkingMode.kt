package com.arslan.shizuwall

import android.content.Context
import com.arslan.shizuwall.ui.MainActivity

enum class WorkingMode {
    SHIZUKU,
    LADB,
    ROOT,
    VPN;

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
