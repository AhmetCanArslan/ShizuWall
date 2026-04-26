package com.arslan.shizuwall.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.arslan.shizuwall.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.arslan.shizuwall.model.AppInfo
import com.arslan.shizuwall.utils.UiUtils

class SelectedAppsAdapter(
    private val appList: List<AppInfo>
) : RecyclerView.Adapter<SelectedAppsAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val appName: TextView = itemView.findViewById(R.id.appName)

        fun bind(appInfo: AppInfo) {
            val pkg = appInfo.packageName
            appIcon.tag = pkg
            appIcon.setImageDrawable(null)

            UiUtils.getLifecycleOwner(itemView.context)?.lifecycleScope?.launch(Dispatchers.IO) {
                try {
                    val pm = itemView.context.packageManager
                    val drawable = pm.getApplicationIcon(pkg)
                    val bitmap = UiUtils.drawableToBitmap(drawable)
                    withContext(Dispatchers.Main) {
                        if (appIcon.tag == pkg) {
                            appIcon.setImageBitmap(bitmap)
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }

            appName.text = appInfo.appName
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selected_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(appList[position])
    }

    override fun getItemCount(): Int = appList.size
}
