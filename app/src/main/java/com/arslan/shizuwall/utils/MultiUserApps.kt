package com.arslan.shizuwall.utils

import android.content.Context
import android.util.Log
import com.arslan.shizuwall.shell.ShellExecutorProvider
import com.arslan.shizuwall.ui.MainActivity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONObject


object MultiUserApps {

    private const val TAG = "MultiUserApps"

    private val USER_LINE = Regex("""UserInfo\{(\d+):([^:]*):""")
    private val PACKAGE_LINE = Regex("""^package:(\S+)\s+uid:(\d+)$""")
    private val DUMPSYS_PACKAGE_TOKEN = Regex("""^Package \[([^\]]+)]$""")
    private val DUMPSYS_APP_ID_TOKEN = Regex("""^(?:appId|userId)=(\d+)$""")
    private val DUMPSYS_FLAGS_TOKEN = Regex("""^pkgFlags=\[(.*)]$""")
    private val DUMPSYS_PRIVATE_FLAGS_TOKEN = Regex("""^privateFlags=\[(.*)]$""")
    private val DUMPSYS_USER_TOKEN = Regex("""^User (\d+):$""")
    private val DUMPSYS_INSTALLED_TOKEN = Regex("""^installed=(\w+)$""")
    private val DUMPSYS_ENABLED_TOKEN = Regex("""^enabled=(\d+)$""")
    private val DUMPSYS_END_TOKEN = Regex("""^Shared users:$""")
    private val LIBRARY_FLAGS = setOf("STATIC_SHARED_LIBRARY", "SDK_LIBRARY")
    private val DISABLED_STATES = setOf(2, 3, 4)

    private const val DUMPSYS_COMMAND = "dumpsys package packages | grep -oE " +
        "'Package \\[[^]]+]|appId=[0-9]+|userId=[0-9]+|pkgFlags=\\[[^]]*]|" +
        "privateFlags=\\[[^]]*]|User [0-9]+:|installed=[a-z]+|enabled=[0-9]+|Shared users:'"

    data class SecondaryApp(
        val userId: Int,
        val packageName: String,
        val uid: Int,
        val isSystem: Boolean = false
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

    @Volatile
    private var memoryCache: Snapshot? = null

    private val invalidations = AtomicLong(1L)

    @Volatile
    private var scannedAtGeneration = 0L

    private val scanMutex = Mutex()

    fun invalidate() {
        invalidations.incrementAndGet()
    }

    private fun isFresh(): Boolean = scannedAtGeneration == invalidations.get()

    suspend fun snapshot(context: Context): Snapshot {
        if (isFresh()) readCache(context)?.let { return it }
        return scanMutex.withLock {
            if (isFresh()) readCache(context)?.let { return@withLock it }
            val generation = invalidations.get()
            val scanned = scan(context)
            when {
                scanned != null -> {
                    writeCache(context, scanned)
                    scannedAtGeneration = generation
                    scanned
                }
                else -> readCache(context) ?: Snapshot.EMPTY
            }
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

        val known = readCache(context)?.apps.orEmpty().groupBy { it.userId }
        val apps = mutableListOf<SecondaryApp>()
        val blocked = mutableListOf<Int>()
        val empty = mutableListOf<Int>()
        for (userId in userNames.keys) {

            val results = try {
                executor.execBatch(
                    listOf(
                        "pm list packages -3 -e -U --user $userId",
                        "pm list packages -s -e -U --user $userId"
                    )
                )
            } catch (t: Throwable) {
                Log.w(TAG, "Could not list packages for user $userId", t)
                blocked.add(userId)
                continue
            }

            if (results.none { it.isEffectivelySuccess }) {
                Log.w(TAG, "pm list packages failed for user $userId, falling back to dumpsys")
                blocked.add(userId)
                continue
            }

            var found = 0
            results.forEachIndexed { index, listResult ->
                if (!listResult.isEffectivelySuccess) return@forEachIndexed
                val isSystem = index == 1
                listResult.stdout.lineSequence().forEach inner@{ line ->
                    val match = PACKAGE_LINE.matchEntire(line.trim()) ?: return@inner
                    val pkg = match.groupValues[1]
                    val uid = match.groupValues[2].toIntOrNull() ?: return@inner

                    found++
                    if (pkg == selfPkg) return@inner
                    if (ShizukuPackageResolver.isShizukuPackage(context, pkg)) return@inner
                    apps.add(SecondaryApp(userId, pkg, uid, isSystem))
                }
            }
            if (found == 0) empty.add(userId)
        }

        val fallbackFor = blocked + empty
        if (fallbackFor.isNotEmpty()) {
            val fallback = scanViaDumpsys(context, fallbackFor.toSet())
            fallbackFor.forEach { userId ->
                val forUser = fallback[userId].orEmpty()
                Log.i(TAG, "dumpsys fallback found ${forUser.size} apps for user $userId")
                when {
                    forUser.isNotEmpty() -> apps.addAll(forUser)
                    userId in blocked -> known[userId]?.let { apps.addAll(it) }
                }
            }
        }
        return Snapshot(apps, userNames)
    }

    private suspend fun scanViaDumpsys(
        context: Context,
        userIds: Set<Int>
    ): Map<Int, List<SecondaryApp>> {
        val executor = ShellExecutorProvider.forContext(context)
        val selfPkg = context.packageName

        val result = try {
            executor.exec(DUMPSYS_COMMAND)
        } catch (t: Throwable) {
            Log.w(TAG, "dumpsys package fallback failed", t)
            return emptyMap()
        }
        if (!result.isEffectivelySuccess) {
            Log.w(TAG, "dumpsys package fallback failed: ${result.stderr.ifEmpty { result.stdout }}")
            return emptyMap()
        }

        return parseDumpsysPackages(result.stdout, userIds) { pkg ->
            pkg == selfPkg || ShizukuPackageResolver.isShizukuPackage(context, pkg)
        }
    }

    internal fun parseDumpsysPackages(
        output: String,
        userIds: Set<Int>,
        exclude: (String) -> Boolean
    ): Map<Int, List<SecondaryApp>> {
        val byUser = mutableMapOf<Int, MutableList<SecondaryApp>>()
        val seen = mutableSetOf<String>()
        var pkg: String? = null
        var appId = -1
        var skip = false
        var isSystem = false
        var pendingUser = -1
        var pending: SecondaryApp? = null

        fun flush() {
            val app = pending ?: return
            pending = null
            byUser.getOrPut(app.userId) { mutableListOf() }.add(app)
        }

        output.lineSequence().forEach { raw ->
            val token = raw.trim()

            if (DUMPSYS_END_TOKEN.matches(token)) {
                flush()
                pkg = null
                return@forEach
            }
            DUMPSYS_PACKAGE_TOKEN.matchEntire(token)?.let { match ->
                flush()
                val name = match.groupValues[1]
                pkg = name
                appId = -1
                isSystem = false
                pendingUser = -1
                skip = !seen.add(name) || exclude(name)
                return@forEach
            }
            val current = pkg ?: return@forEach
            if (skip) return@forEach

            DUMPSYS_APP_ID_TOKEN.matchEntire(token)?.let { match ->
                flush()
                if (appId < 0) appId = match.groupValues[1].toIntOrNull() ?: -1
                pendingUser = -1
                return@forEach
            }
            DUMPSYS_FLAGS_TOKEN.matchEntire(token)?.let { match ->
                flush()
                isSystem = match.groupValues[1].split(" ").contains("SYSTEM")
                pendingUser = -1
                return@forEach
            }
            DUMPSYS_PRIVATE_FLAGS_TOKEN.matchEntire(token)?.let { match ->
                flush()
                val flags = match.groupValues[1].split(" ")
                if (flags.any { flag -> LIBRARY_FLAGS.any { flag.endsWith(it) } }) skip = true
                pendingUser = -1
                return@forEach
            }
            DUMPSYS_USER_TOKEN.matchEntire(token)?.let { match ->
                flush()
                pendingUser = match.groupValues[1].toIntOrNull() ?: -1
                return@forEach
            }
            DUMPSYS_INSTALLED_TOKEN.matchEntire(token)?.let { match ->
                val userId = pendingUser
                pendingUser = -1
                if (userId < 0 || appId < 0) return@forEach
                if (match.groupValues[1] != "true") return@forEach
                if (userId !in userIds) return@forEach
                val uid = userId * AppIds.PER_USER_RANGE + AppIds.appIdOf(appId)
                pending = SecondaryApp(userId, current, uid, isSystem)
                return@forEach
            }
            DUMPSYS_ENABLED_TOKEN.matchEntire(token)?.let { match ->
                val state = match.groupValues[1].toIntOrNull()
                if (state in DISABLED_STATES) pending = null else flush()
            }
        }
        flush()
        return byUser
    }

    private const val KEY_CACHE = "secondary_user_apps_cache"
    private fun encodeApps(apps: List<SecondaryApp>): JSONArray {
        val arr = JSONArray()
        apps.forEach { app ->
            arr.put(
                JSONObject().apply {
                    put("u", app.userId)
                    put("p", app.packageName)
                    put("uid", app.uid)
                    put("s", app.isSystem)
                }
            )
        }
        return arr
    }

    private fun decodeApps(arr: JSONArray): List<SecondaryApp> {
        val apps = mutableListOf<SecondaryApp>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val pkg = obj.optString("p")
            if (pkg.isBlank()) continue
            apps.add(
                SecondaryApp(obj.optInt("u"), pkg, obj.optInt("uid"), obj.optBoolean("s", false))
            )
        }
        return apps
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

    private fun writeCache(context: Context, snapshot: Snapshot) {
        val users = JSONObject()
        snapshot.userNames.forEach { (id, name) -> users.put(id.toString(), name) }
        val root = JSONObject().apply {
            put("users", users)
            put("apps", encodeApps(snapshot.apps))
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
            val apps = decodeApps(root.optJSONArray("apps") ?: JSONArray())
            Snapshot(apps, userNames).also { memoryCache = it }
        } catch (t: Throwable) {
            Log.w(TAG, "Corrupt secondary-user cache", t)
            null
        }
    }

    fun clearCache(context: Context) {
        memoryCache = null
        invalidate()
        prefs(context).edit()
            .remove(KEY_CACHE)
            .remove("secondary_user_apps_cache_at")
            .apply()
    }
}
