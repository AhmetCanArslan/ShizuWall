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

    fun isManageable(
        uid: Int,
        enabled: Boolean,
        hasInternet: Boolean,
        isSystem: Boolean,
        showSystemApps: Boolean
    ): Boolean = enabled &&
        hasInternet &&
        AppIds.isBlockable(uid) &&
        (showSystemApps || !isSystem)

    fun compute(context: Context, selectedPkgs: List<String>, showSystemApps: Boolean): WhitelistResult {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val selfPkg = context.packageName
        val toBlock = mutableListOf<String>()
        val toAllow = mutableListOf<String>()

        for (pInfo in packages) {
            val appInfo = pInfo.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            if (pInfo.packageName == selfPkg) continue
            if (ShizukuPackageResolver.isShizukuPackage(context, pInfo.packageName)) continue

            val hasInet = pInfo.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
            if (!isManageable(appInfo.uid, appInfo.enabled, hasInet, isSystem, showSystemApps)) continue

            if (selectedPkgs.contains(pInfo.packageName)) {
                toAllow.add(pInfo.packageName)
            } else {
                toBlock.add(pInfo.packageName)
            }
        }

        val perProfileSelection = MultiUserApps.isEnabled(context)
        for (app in MultiUserApps.cachedSnapshot(context).apps) {
            if (!AppIds.isBlockable(app.uid)) continue
            if (isSelected(selectedPkgs, app, perProfileSelection)) toAllow.add(app.key)
            else toBlock.add(app.key)
        }

        return WhitelistResult(toBlock, toAllow)
    }

    fun isSelected(
        selectedPkgs: List<String>,
        app: MultiUserApps.SecondaryApp,
        perProfileSelection: Boolean
    ): Boolean =
        if (perProfileSelection) selectedPkgs.contains(app.key)
        else selectedPkgs.contains(app.packageName)
}