package com.arslan.shizuwall.trackers

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.util.zip.ZipFile


object TrackerScanner {

    sealed class ScanResult {
        data class Success(val trackers: List<Tracker>) : ScanResult()
        object Failed : ScanResult()
    }

    private const val MAX_DEX_BYTES = 64 * 1024 * 1024

    private const val CACHE_PREFIX = "scan:"

    /**
     * Result for an already-scanned package, without touching disk beyond prefs.
     * Returns null when nothing usable is cached, so the caller must run [scan].
     * Cheap enough for the main thread: it never opens an APK and never parses
     * the tracker asset (bails out if the registry has not been loaded yet).
     */
    fun cachedResult(context: Context, packageName: String): ScanResult? {
        val definitions = TrackerRegistry.trackersIfLoaded() ?: return null
        if (definitions.isEmpty()) return null

        val versionCode = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0).longVersionCode
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }

        val cached = context.getSharedPreferences(TrackerRegistry.PREFS_NAME, Context.MODE_PRIVATE)
            .getString("$CACHE_PREFIX$packageName", null) ?: return null
        val parts = cached.split('|')
        if (parts.size != 3 || parts[0] != versionCode.toString() ||
            parts[1] != TrackerRegistry.stamp(context)
        ) {
            return null
        }

        val ids = parts[2].split(',').mapNotNull { it.toIntOrNull() }.toSet()
        return ScanResult.Success(definitions.filter { it.id in ids }.sortedBy { it.name.lowercase() })
    }

    suspend fun scan(context: Context, packageName: String): ScanResult =
        withContext(Dispatchers.IO) {
            val definitions = TrackerRegistry.trackers(context)
            if (definitions.isEmpty()) return@withContext ScanResult.Failed

            val pm = context.packageManager
            val packageInfo = try {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                return@withContext ScanResult.Failed
            }

            @Suppress("DEPRECATION")
            val versionCode = packageInfo.longVersionCode
            val stamp = TrackerRegistry.stamp(context)
            val cacheKey = "$CACHE_PREFIX$packageName"
            val prefs = context.getSharedPreferences(TrackerRegistry.PREFS_NAME, Context.MODE_PRIVATE)

            prefs.getString(cacheKey, null)?.let { cached ->
                val parts = cached.split('|')
                if (parts.size == 3 && parts[0] == versionCode.toString() && parts[1] == stamp) {
                    val ids = parts[2].split(',').mapNotNull { it.toIntOrNull() }.toSet()
                    return@withContext ScanResult.Success(
                        definitions.filter { it.id in ids }.sortedBy { it.name.lowercase() }
                    )
                }
            }

            val appInfo = packageInfo.applicationInfo ?: return@withContext ScanResult.Failed
            val apks = buildList {
                appInfo.sourceDir?.let { add(it) }
                appInfo.splitSourceDirs?.let { addAll(it) }
            }
            if (apks.isEmpty()) return@withContext ScanResult.Failed

            val signatureIndex = HashMap<String, MutableList<Int>>()
            for (tracker in definitions) {
                for (signature in tracker.signatures) {
                    signatureIndex.getOrPut(signature) { mutableListOf() }.add(tracker.id)
                }
            }

            val found = HashSet<Int>()
            var scannedAnything = false

            for (path in apks) {
                try {
                    ZipFile(path).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val name = entry.name
                            if (!name.startsWith("classes") || !name.endsWith(".dex")) continue
                            if (entry.size > MAX_DEX_BYTES) continue

                            val bytes = try {
                                DataInputStream(zip.getInputStream(entry)).use { it.readBytes() }
                            } catch (_: OutOfMemoryError) {
                                continue
                            }
                            if (matchDex(bytes, signatureIndex, found)) scannedAnything = true
                        }
                    }
                } catch (_: Exception) {

                }
            }

            if (!scannedAnything) return@withContext ScanResult.Failed

            prefs.edit()
                .putString(cacheKey, "$versionCode|$stamp|${found.joinToString(",")}")
                .apply()

            ScanResult.Success(definitions.filter { it.id in found }.sortedBy { it.name.lowercase() })
        }

    private fun matchDex(
        dex: ByteArray,
        signatureIndex: Map<String, MutableList<Int>>,
        found: MutableSet<Int>
    ): Boolean {
        if (dex.size < 112) return false
        if (dex[0] != 'd'.code.toByte() || dex[1] != 'e'.code.toByte() ||
            dex[2] != 'x'.code.toByte() || dex[3] != '\n'.code.toByte()
        ) return false

        val stringIdsSize = readInt(dex, 56)
        val stringIdsOff = readInt(dex, 60)
        if (stringIdsSize <= 0 || stringIdsOff <= 0) return false
        if (stringIdsOff + stringIdsSize * 4 > dex.size) return false

        val builder = StringBuilder(128)
        for (i in 0 until stringIdsSize) {
            val dataOff = readInt(dex, stringIdsOff + i * 4)
            if (dataOff <= 0 || dataOff >= dex.size) continue

            var p = dataOff
            while (p < dex.size && (dex[p].toInt() and 0x80) != 0) p++
            p++
            if (p >= dex.size) continue

            if (dex[p] != 'L'.code.toByte()) continue
            p++

            builder.setLength(0)
            var slashes = 0
            while (p < dex.size) {
                val b = dex[p].toInt()
                if (b == 0) break
                if (b == ';'.code) break
                if (b < 0) break // non-ASCII, cannot be part of a tracker prefix
                if (b == '/'.code) {
                    slashes++
                    // Check the package prefix accumulated so far.
                    signatureIndex[builder.toString()]?.let { found.addAll(it) }
                    builder.append('.')
                } else {
                    builder.append(b.toChar())
                }
                p++
            }
            if (slashes > 0) {
                signatureIndex[builder.toString()]?.let { found.addAll(it) }
            }
        }
        return true
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int {
        if (offset + 3 >= bytes.size) return -1
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}
