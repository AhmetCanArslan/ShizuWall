package com.arslan.shizuwall

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Bitmap
import android.graphics.Typeface

class SelectedAppsAdapter(
    private val appList: List<AppInfo>,
    private val typeface: Typeface? = null
) : RecyclerView.Adapter<SelectedAppsAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val appName: TextView = itemView.findViewById(R.id.appName)

        init {
            typeface?.let {
                appName.typeface = it
            }
        }

        fun bind(appInfo: AppInfo) {
            if (appInfo.iconBitmap != null) {
                appIcon.setImageBitmap(appInfo.iconBitmap)
            } else {
                appIcon.setImageDrawable(null)
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
