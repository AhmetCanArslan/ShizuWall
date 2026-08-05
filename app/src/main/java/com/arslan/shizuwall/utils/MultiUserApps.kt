package com.arslan.shizuwall.utils

import android.content.Context
import android.util.Log
import com.arslan.shizuwall.shell.ShellExecutorProvider
import com.arslan.shizuwall.ui.MainActivity
import org.json.JSONArray
import org.json.JSONObject


object MultiUserApps {

    private const val TAG = "MultiUserApps"

    private val USER_LINE = Regex("""UserInfo\{(\d+):([^:]*):""")
    private val PACKAGE_LINE = Regex("""^package:(\S+)\s+uid:(\d+)$""")

    data class SecondaryApp(
        val userId: Int,
        val packageName: String,
        val uid: Int
    ) {
        val key: String get() = AppKey.of(userId, packageName)
    }

    data class Snapshot(
        val apps: List<SecondaryApp>,
        val userNames: Map<Int, String>
    ) {
        companion object {
            val EMPTY = Snapshot(emptyList(), emptyMap())
        }
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(MainActivity.KEY_SHOW_OTHER_PROFILES, false)

    private const val SCAN_TTL_MS = 60_000L

    @Volatile
    private var memoryCache: Snapshot? = null

    @Volatile
    private var lastScanAt = 0L

    suspend fun snapshot(context: Context): Snapshot {
        val cached = readCache(context)
        if (cached != null && System.currentTimeMillis() - lastScanAt < SCAN_TTL_MS) {
            return cached
        }
        val scanned = scan(context)
        return when {
            scanned != null -> {
                lastScanAt = System.currentTimeMillis()
                writeCache(context, scanned)
                scanned
            }
            cached != null -> cached
            else -> Snapshot.EMPTY
        }
    }

    fun cachedUid(context: Context, key: String): Int? {
        if (!AppKey.isSecondary(key)) return null
        val snapshot = readCache(context) ?: return null
        return snapshot.apps.firstOrNull { it.key == key }?.uid
    }

    fun cachedSnapshot(context: Context): Snapshot = readCache(context) ?: Snapshot.EMPTY

    fun userLabel(context: Context, userId: Int): String {
        val name = readCache(context)?.userNames?.get(userId)?.takeIf { it.isNotBlank() }
        return name ?: "User $userId"
    }

    private suspend fun scan(context: Context): Snapshot? {
        val executor = ShellExecutorProvider.forContext(context)
        val selfPkg = context.packageName

        val usersResult = try {
            executor.exec("pm list users")
        } catch (t: Throwable) {
            Log.w(TAG, "Could not list users", t)
            return null
        }
        if (!usersResult.isEffectivelySuccess) {
            Log.w(TAG, "pm list users failed: ${usersResult.stderr.ifEmpty { usersResult.stdout }}")
            return null
        }

        val userNames = mutableMapOf<Int, String>()
        USER_LINE.findAll(usersResult.stdout).forEach { match ->
            val id = match.groupValues[1].toIntOrNull() ?: return@forEach
            if (id == 0) return@forEach
            userNames[id] = match.groupValues[2].trim()
        }
        if (userNames.isEmpty()) return Snapshot(emptyList(), emptyMap())

        val apps = mutableListOf<SecondaryApp>()
        for (userId in userNames.keys) {

            val listResult = try {
                executor.exec("pm list packages -3 -U --user $userId")
            } catch (t: Throwable) {
                Log.w(TAG, "Could not list packages for user $userId", t)
                continue
            }

            if (!listResult.isEffectivelySuccess) continue

            listResult.stdout.lineSequence().forEach { line ->
                val match = PACKAGE_LINE.matchEntire(line.trim()) ?: return@forEach
                val pkg = match.groupValues[1]
                val uid = match.groupValues[2].toIntOrNull() ?: return@forEach

                if (pkg == selfPkg) return@forEach
                if (ShizukuPackageResolver.isShizukuPackage(context, pkg)) return@forEach
                apps.add(SecondaryApp(userId, pkg, uid))
            }
        }
        return Snapshot(apps, userNames)
    }

    private const val KEY_CACHE = "secondary_user_apps_cache"

    private fun prefs(context: Context) =
        context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

    private fun writeCache(context: Context, snapshot: Snapshot) {
        val users = JSONObject()
        snapshot.userNames.forEach { (id, name) -> users.put(id.toString(), name) }
        val apps = JSONArray()
        snapshot.apps.forEach { app ->
            apps.put(
                JSONObject().apply {
                    put("u", app.userId)
                    put("p", app.packageName)
                    put("uid", app.uid)
                }
            )
        }
        val root = JSONObject().apply {
            put("users", users)
            put("apps", apps)
        }
        memoryCache = snapshot
        prefs(context).edit()
            .putString(KEY_CACHE, root.toString())
            .apply()
    }

    private fun readCache(context: Context): Snapshot? {
        memoryCache?.let { return it }
        val json = prefs(context).getString(KEY_CACHE, null) ?: return null
        return try {
            val root = JSONObject(json)
            val users = root.optJSONObject("users") ?: JSONObject()
            val userNames = mutableMapOf<Int, String>()
            users.keys().forEach { key ->
                key.toIntOrNull()?.let { userNames[it] = users.optString(key) }
            }
            val arr = root.optJSONArray("apps") ?: JSONArray()
            val apps = mutableListOf<SecondaryApp>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val pkg = obj.optString("p")
                if (pkg.isBlank()) continue
                apps.add(SecondaryApp(obj.optInt("u"), pkg, obj.optInt("uid")))
            }
            Snapshot(apps, userNames).also { memoryCache = it }
        } catch (t: Throwable) {
            Log.w(TAG, "Corrupt secondary-user cache", t)
            null
        }
    }

    fun clearCache(context: Context) {
        memoryCache = null
        lastScanAt = 0L
        prefs(context).edit().remove(KEY_CACHE).apply()
    }
}
