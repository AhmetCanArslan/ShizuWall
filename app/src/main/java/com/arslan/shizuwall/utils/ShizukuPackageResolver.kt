package com.arslan.shizuwall.utils

import android.content.Context
import android.content.pm.PackageManager

/**
 * Utility class to dynamically resolve Shizuku package name from its permission.
 * This supports both the official Shizuku and forks with obfuscated package names
 * (like thedjchi's fork for HyperOS compatibility).
 * 
 * Detection methods (in order of reliability):
 * 1. Permission-based: Find packages that define Shizuku permissions
 * 2. Provider-based: Find packages that expose Shizuku providers
 * 3. Fallback: Use known package names
 */
object ShizukuPackageResolver {
    
    private const val TAG = "ShizukuPackageResolver"
    
    /**
     * Known Shizuku permission names that may be defined by Shizuku or its forks.
     * The official Shizuku uses "moe.shizuku.api.v3.permission.SHIZUKU".
     * Forks that maintain API compatibility must define one of these permissions.
     */
    private val SHIZUKU_PERMISSIONS = listOf(
        "moe.shizuku.api.v3.permission.SHIZUKU",
        "moe.shizuku.api.permission.SHIZUKU"
    )
    
    /**
     * Known Shizuku provider authorities.
     * Forks that maintain API compatibility typically use similar authority patterns.
     */
    private val SHIZUKU_PROVIDER_AUTHORITIES = listOf(
        "moe.shizuku.privileged.api",
        "moe.shizuku.manager"
    )
    
    /**
     * Fallback package names for the official Shizuku app.
     * Used when all other detection methods fail.
     */
    private val FALLBACK_PACKAGES = listOf(
        "moe.shizuku.privileged.api",
        "moe.shizuku.manager"
    )
    
    @Volatile
    private var cachedPackageNames: Set<String>? = null
    
    private val lock = Any()
    
    /**
     * Resolves all Shizuku package names by finding packages that define
     * Shizuku permissions or expose Shizuku providers.
     * 
     * This works with:
     * - Official Shizuku (moe.shizuku.privileged.api)
     * - thedjchi's fork and similar forks for HyperOS compatibility
     * - Any other fork that maintains API compatibility
     *
     * @param context Application context
     * @return Set of package names that provide Shizuku functionality
     */
    fun resolveShizukuPackages(context: Context): Set<String> {
        cachedPackageNames?.let { return it }
        
        synchronized(lock) {
            cachedPackageNames?.let { return it }
            
            val resolved = mutableSetOf<String>()
            val pm = context.packageManager
            
            // Method 1: Find packages that define Shizuku permissions
            // This is the most reliable method as any API-compatible fork must define these permissions
            for (permission in SHIZUKU_PERMISSIONS) {
                try {
                    val permissionInfo = pm.getPermissionInfo(permission, 0)
                    val packageName = permissionInfo.packageName
                    if (!packageName.isNullOrBlank() && packageName != context.packageName) {
                        resolved.add(packageName)
                        android.util.Log.d(TAG, "Found Shizuku package via permission '$permission': $packageName")
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    // Permission not defined by any package, continue to next
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Error checking permission $permission: ${e.message}")
                }
            }
            
            // Method 2: Find packages that expose Shizuku providers
            // This catches forks that might use different permission names but still expose the provider
            for (authority in SHIZUKU_PROVIDER_AUTHORITIES) {
                try {
                    val providerInfo = pm.resolveContentProvider(authority, 0)
                    if (providerInfo != null) {
                        val packageName = providerInfo.packageName
                        if (packageName != context.packageName) {
                            resolved.add(packageName)
                            android.util.Log.d(TAG, "Found Shizuku package via provider authority '$authority': $packageName")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Error checking provider $authority: ${e.message}")
                }
            }
            
            // Method 3: Query all packages for Shizuku-related permissions (more thorough)
            // This catches any package that requests or defines Shizuku-related permissions
            // Only run this expensive query if Methods 1 and 2 didn't find anything
            if (resolved.isEmpty()) {
                try {
                    val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                    for (packageInfo in packages) {
                        val pkg = packageInfo.packageName
                        if (pkg == context.packageName) continue
                        
                        // Check if this package defines Shizuku permissions
                        try {
                            val pkgInfo = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
                            val permissions = pkgInfo.permissions ?: emptyArray()
                            for (permInfo in permissions) {
                                if (permInfo.name in SHIZUKU_PERMISSIONS) {
                                    resolved.add(pkg)
                                    android.util.Log.d(TAG, "Found Shizuku package via queryPermissionsByPackage: $pkg")
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            // Package might not export permission info, skip
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Error querying installed packages: ${e.message}")
                }
            }
            
            // If no packages found, use fallback
            if (resolved.isEmpty()) {
                resolved.addAll(FALLBACK_PACKAGES)
                android.util.Log.d(TAG, "No Shizuku packages found via detection, using fallback: $FALLBACK_PACKAGES")
            }
            
            android.util.Log.d(TAG, "Resolved Shizuku packages: $resolved")
            cachedPackageNames = resolved
            return resolved
        }
    }
    
    /**
     * Checks if the given package name is a Shizuku package.
     *
     * @param context Application context
     * @param packageName Package name to check
     * @return true if the package is a Shizuku package
     */
    fun isShizukuPackage(context: Context, packageName: String): Boolean {
        return packageName in resolveShizukuPackages(context)
    }
    
    /**
     * Gets all known Shizuku package names for launching purposes.
     * Returns resolved packages first, then fallback packages.
     *
     * @param context Application context
     * @return List of package names to try when launching Shizuku
     */
    fun getLaunchCandidates(context: Context): List<String> {
        val resolved = resolveShizukuPackages(context).toList()
        val fallbacks = FALLBACK_PACKAGES.filter { it !in resolved }
        return resolved + fallbacks
    }
    
    /**
     * Clears the cached package names. Call this if Shizuku package may have changed
     * (e.g., after package added/removed broadcast).
     */
    fun clearCache() {
        cachedPackageNames = null
    }
}
