package com.arslan.shizuwall.trackers

import android.content.Context
import org.json.JSONObject


data class Tracker(
    val id: Int,
    val name: String,
    val categories: List<String>,
    val website: String,
    val signatures: List<String>
)


object TrackerRegistry {

    private const val ASSET_NAME = "trackers.json"

    const val PREFS_NAME = "ShizuWallTrackers"

    @Volatile
    private var cached: List<Tracker>? = null

    @Volatile
    private var databaseStamp: String = ""

    fun trackers(context: Context): List<Tracker> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val loaded = load(context)
            cached = loaded
            return loaded
        }
    }

    fun stamp(context: Context): String {
        trackers(context)
        return databaseStamp
    }

    private fun load(context: Context): List<Tracker> {
        val base = readAsset(context, ASSET_NAME)
        if (base == null) {
            databaseStamp = "empty"
            return emptyList()
        }

        return try {
            val parsed = parse(base)
            databaseStamp = "${parsed.size}-${base.length}"
            parsed
        } catch (_: Exception) {
            databaseStamp = "empty"
            emptyList()
        }
    }

    private fun readAsset(context: Context, name: String): String? = try {
        context.assets.open(name).bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        null
    }

    private fun parse(json: String): List<Tracker> {
        val root = JSONObject(json)
        val result = mutableListOf<Tracker>()

        val array = root.optJSONArray("trackers")
        if (array != null) {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                toTracker(
                    obj.optInt("id"),
                    obj.optString("name"),
                    obj.optString("website"),
                    stringList(obj, "categories"),
                    stringList(obj, "signatures").ifEmpty {
                        splitSignature(obj.optString("code_signature"))
                    }
                )?.let { result.add(it) }
            }
            return result
        }

        val map = root.optJSONObject("trackers") ?: return result
        for (key in map.keys()) {
            val obj = map.optJSONObject(key) ?: continue
            toTracker(
                obj.optInt("id"),
                obj.optString("name"),
                obj.optString("website"),
                stringList(obj, "categories"),
                splitSignature(obj.optString("code_signature"))
            )?.let { result.add(it) }
        }
        return result
    }

    private fun toTracker(
        id: Int,
        name: String,
        website: String,
        categories: List<String>,
        signatures: List<String>
    ): Tracker? {
        if (name.isBlank() || signatures.isEmpty()) return null
        return Tracker(id, name, categories, website, signatures)
    }

    private fun stringList(obj: JSONObject, key: String): List<String> {
        val array = obj.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
    }

    private fun splitSignature(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split('|')
            .map { it.trim().trim('.') }
            .filter { it.length > 3 }
            .distinct()
    }

}
