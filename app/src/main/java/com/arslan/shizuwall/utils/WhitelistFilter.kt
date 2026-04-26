package com.arslan.shizuwall.utils

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

object WhitelistFilter {

    data class WhitelistResult(
        val toBlock: List<String>,
        val toAllow: List<String>
    )

    fun compute(context: Context, selectedPkgs: List<String>, showSystemApps: Boolean): WhitelistResult {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val selfPkg = context.packageName
        val toBlock = mutableListOf<String>()
        val toAllow = mutableListOf<String>()

        for (pInfo in packages) {
            val appInfo = pInfo.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            if (!appInfo.enabled) continue
            if (pInfo.packageName == selfPkg) continue
            if (ShizukuPackageResolver.isShizukuPackage(context, pInfo.packageName)) continue

            val hasInet = pInfo.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
            if (!hasInet) continue
            if (!showSystemApps && isSystem) continue

            if (selectedPkgs.contains(pInfo.packageName)) {
                toAllow.add(pInfo.packageName)
            } else {
                toBlock.add(pInfo.packageName)
            }
        }
        return WhitelistResult(toBlock, toAllow)
    }
}