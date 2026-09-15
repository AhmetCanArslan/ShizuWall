package com.arslan.shizuwall.utils

import android.content.Context
import android.content.pm.PackageManager

object ShizukuPackageResolver {
    
    private const val TAG = "ShizukuPackageResolver"
    
    private val SHIZUKU_PERMISSIONS = listOf(
        "moe.shizuku.api.v3.permission.SHIZUKU",
        "moe.shizuku.api.permission.SHIZUKU"
    )
    
    private val FALLBACK_PACKAGES = listOf(
        "moe.shizuku.privileged.api",
        "moe.shizuku.manager"
    )
    
    @Volatile
    private var cachedPackageNames: Set<String>? = null
    
    private val lock = Any()
    
    fun resolveShizukuPackages(context: Context): Set<String> {
        cachedPackageNames?.let { return it }
        
        synchronized(lock) {
            cachedPackageNames?.let { return it }
            
            val resolved = mutableSetOf<String>()
            val pm = context.packageManager
            
            for (permission in SHIZUKU_PERMISSIONS) {
                try {
                    val permissionInfo = pm.getPermissionInfo(permission, 0)
                    val packageName = permissionInfo.packageName
                    if (!packageName.isNullOrBlank() && packageName != context.packageName) {
                        resolved.add(packageName)
                        android.util.Log.d(TAG, "Found Shizuku package via permission '$permission': $packageName")
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Error checking permission $permission: ${e.message}")
                }
            }
            
            for (authority in FALLBACK_PACKAGES) {
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
            
            if (resolved.isEmpty()) {
                try {
                    val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                    for (packageInfo in packages) {
                        val pkg = packageInfo.packageName
                        if (pkg == context.packageName) continue
                        
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
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Error querying installed packages: ${e.message}")
                }
            }
            
            if (resolved.isEmpty()) {
                resolved.addAll(FALLBACK_PACKAGES)
                android.util.Log.d(TAG, "No Shizuku packages found via detection, using fallback: $FALLBACK_PACKAGES")
            }
            
            android.util.Log.d(TAG, "Resolved Shizuku packages: $resolved")
            cachedPackageNames = resolved
            return resolved
        }
    }
    
    fun isShizukuPackage(context: Context, packageName: String): Boolean {
        return packageName in resolveShizukuPackages(context)
    }
    
    fun getLaunchCandidates(context: Context): List<String> {
        val resolved = resolveShizukuPackages(context).toList()
        val fallbacks = FALLBACK_PACKAGES.filter { it !in resolved }
        return resolved + fallbacks
    }
    
    fun clearCache() {
        cachedPackageNames = null
    }
}
