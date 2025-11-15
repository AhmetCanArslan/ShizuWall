package com.arslan.shizuwall

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Bitmap
import android.graphics.Typeface

class AppInfoDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
    override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
        return oldItem.packageName == newItem.packageName
    }

    override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
        return oldItem == newItem
    }
}

class AppListAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo) -> Unit,
    private val typeface: Typeface? = null
) : ListAdapter<AppInfo, AppListAdapter.AppViewHolder>(AppInfoDiffCallback()) {

    // controls whether user can change selection
    private var selectionEnabled: Boolean = true

    fun setSelectionEnabled(enabled: Boolean) {
        selectionEnabled = enabled
        notifyDataSetChanged()
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val appName: TextView = itemView.findViewById(R.id.appName)
        val packageName: TextView = itemView.findViewById(R.id.packageName)
        val checkbox: CheckBox = itemView.findViewById(R.id.appCheckbox)
        val favoriteIcon: ImageView = itemView.findViewById(R.id.favoriteIcon)

        init {
            typeface?.let {
                appName.typeface = it
                packageName.typeface = it
            }
        }

        fun bind(appInfo: AppInfo) {
            // use cached bitmap (fast) instead of resolving drawable each bind
            if (appInfo.iconBitmap != null) {
                appIcon.setImageBitmap(appInfo.iconBitmap)
            } else {
                appIcon.setImageDrawable(null)
            }

            appName.text = appInfo.appName
            packageName.text = appInfo.packageName

            favoriteIcon.visibility = if (appInfo.isFavorite) View.VISIBLE else View.GONE

            // Avoid triggering listener when recycling views
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = appInfo.isSelected

            if (selectionEnabled) {
                checkbox.isEnabled = true
                checkbox.setOnCheckedChangeListener { _, isChecked ->
                    onAppClick(appInfo.copy(isSelected = isChecked))
                }
                itemView.isClickable = true
                itemView.setOnClickListener {
                    checkbox.isChecked = !checkbox.isChecked
                }
                itemView.setOnLongClickListener {
                    onAppLongClick(appInfo)
                    true
                }
            } else {
                // disable interactions while firewall active
                checkbox.isEnabled = false
                checkbox.setOnCheckedChangeListener(null)
                itemView.setOnClickListener(null)
                itemView.setOnLongClickListener(null)
                itemView.isClickable = false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
