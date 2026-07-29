package com.arslan.shizuwall.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.arslan.shizuwall.R
import com.arslan.shizuwall.trackers.TrackerScanner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

object AppInfoDialog {

    fun show(context: Context, owner: LifecycleOwner, packageName: String, appName: String) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_app_info, null)

        val iconView = view.findViewById<ImageView>(R.id.infoAppIcon)
        val nameView = view.findViewById<TextView>(R.id.infoAppName)
        val packageView = view.findViewById<TextView>(R.id.infoPackageName)
        val metaView = view.findViewById<TextView>(R.id.infoMeta)
        val loadingView = view.findViewById<View>(R.id.infoTrackerLoading)
        val summaryView = view.findViewById<TextView>(R.id.infoTrackerSummary)
        val listView = view.findViewById<TextView>(R.id.infoTrackerList)
        val noteView = view.findViewById<TextView>(R.id.infoTrackerNote)

        val pm = context.packageManager
        val packageInfo: PackageInfo? = try {
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

        nameView.text = appName
        packageView.text = packageName
        try {
            iconView.setImageDrawable(pm.getApplicationIcon(packageName))
        } catch (_: Exception) {
            iconView.setImageDrawable(null)
        }
        metaView.text = buildMeta(context, packageInfo)

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(view)
            .setPositiveButton(R.string.close, null)
            .setNeutralButton(R.string.app_info_open_settings) { _, _ ->
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                    // Some OEM builds hide this screen; nothing else to do.
                }
            }
            .create()
        dialog.show()

        owner.lifecycleScope.launch {
            val result = TrackerScanner.scan(context, packageName)
            if (!dialog.isShowing) return@launch

            loadingView.visibility = View.GONE
            summaryView.visibility = View.VISIBLE

            val attribution = context.getString(R.string.tracker_data_attribution)
            noteView.text = attribution
            noteView.visibility = View.VISIBLE

            when (result) {
                is TrackerScanner.ScanResult.Failed -> {
                    summaryView.text = context.getString(R.string.app_info_trackers_failed)
                }

                is TrackerScanner.ScanResult.Success -> {
                    val trackers = result.trackers
                    if (trackers.isEmpty()) {
                        summaryView.text = context.getString(R.string.app_info_trackers_none)
                    } else {
                        summaryView.text =
                            context.getString(R.string.app_info_trackers_count, trackers.size)
                        listView.text = trackers.joinToString("\n") { tracker ->
                            if (tracker.categories.isEmpty()) {
                                "• ${tracker.name}"
                            } else {
                                "• ${tracker.name} (${tracker.categories.joinToString(", ")})"
                            }
                        }
                        listView.visibility = View.VISIBLE
                        noteView.text = context.getString(R.string.app_info_trackers_note) +
                            "\n\n" + attribution
                    }
                }
            }
        }
    }

    private fun buildMeta(context: Context, packageInfo: PackageInfo?): String {
        if (packageInfo == null) return context.getString(R.string.app_info_unavailable)

        val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
        val lines = mutableListOf<String>()

        val version = packageInfo.versionName
        if (!version.isNullOrBlank()) {
            lines.add(context.getString(R.string.app_info_version, version))
        }
        lines.add(
            context.getString(
                R.string.app_info_installed,
                dateFormat.format(Date(packageInfo.firstInstallTime))
            )
        )
        if (packageInfo.lastUpdateTime > packageInfo.firstInstallTime) {
            lines.add(
                context.getString(
                    R.string.app_info_updated,
                    dateFormat.format(Date(packageInfo.lastUpdateTime))
                )
            )
        }
        packageInfo.applicationInfo?.let { appInfo ->
            lines.add(context.getString(R.string.app_info_uid, appInfo.uid))
        }

        val hasInternet = packageInfo.requestedPermissions
            ?.contains(android.Manifest.permission.INTERNET) == true
        lines.add(
            context.getString(
                if (hasInternet) R.string.app_info_internet_yes else R.string.app_info_internet_no
            )
        )

        return lines.joinToString("\n")
    }
}
