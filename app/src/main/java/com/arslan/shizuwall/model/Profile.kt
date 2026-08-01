package com.arslan.shizuwall.model

import org.json.JSONArray
import org.json.JSONObject

data class Profile(
    val id: String,
    val name: String,
    val packages: Set<String>,
    val firewallMode: String,
    val appModesJson: String,
    val showSystemApps: Boolean,
    val createdAt: Long,
    val icon: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_NAME, name)
        put(KEY_PACKAGES, JSONArray(packages.toList()))
        put(KEY_MODE, firewallMode)
        put(KEY_APP_MODES, appModesJson)
        put(KEY_SHOW_SYSTEM, showSystemApps)
        put(KEY_CREATED_AT, createdAt)
        put(KEY_ICON, icon)
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_PACKAGES = "packages"
        private const val KEY_MODE = "mode"
        private const val KEY_APP_MODES = "appModes"
        private const val KEY_SHOW_SYSTEM = "showSystem"
        private const val KEY_CREATED_AT = "createdAt"
        private const val KEY_ICON = "icon"

        fun fromJson(obj: JSONObject): Profile {
            val pkgs = mutableSetOf<String>()
            val arr = obj.optJSONArray(KEY_PACKAGES)
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optString(i)?.takeIf { it.isNotBlank() }?.let { pkgs.add(it) }
                }
            }
            return Profile(
                id = obj.optString(KEY_ID),
                name = obj.optString(KEY_NAME),
                packages = pkgs,
                firewallMode = obj.optString(KEY_MODE, "DEFAULT"),
                appModesJson = obj.optString(KEY_APP_MODES, "{}"),
                showSystemApps = obj.optBoolean(KEY_SHOW_SYSTEM, false),
                createdAt = obj.optLong(KEY_CREATED_AT, 0L),
                icon = obj.optString(KEY_ICON, "")
            )
        }
    }
}
