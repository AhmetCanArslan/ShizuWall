package com.arslan.shizuwall

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.graphics.createBitmap

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

    // Cache icons to avoid reloading. Max size 1/8th of available memory.
    private val iconCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    // controls whether user can change selection
    private var selectionEnabled: Boolean = true

    fun setSelectionEnabled(enabled: Boolean) {
        if (selectionEnabled != enabled) {
            selectionEnabled = enabled
            notifyItemRangeChanged(0, itemCount)
        }
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
            // Load icon async
            val pkg = appInfo.packageName
            appIcon.tag = pkg
            appIcon.setImageDrawable(null) // Clear previous

            val cached = iconCache.get(pkg)
            if (cached != null) {
                appIcon.setImageBitmap(cached)
            } else {
                getLifecycleOwner(itemView.context)?.lifecycleScope?.launch(Dispatchers.IO) {
                    try {
                        val pm = itemView.context.packageManager
                        val drawable = pm.getApplicationIcon(pkg)
                        val bitmap = drawableToBitmap(drawable)
                        iconCache.put(pkg, bitmap)
                        withContext(Dispatchers.Main) {
                            if (appIcon.tag == pkg) {
                                appIcon.setImageBitmap(bitmap)
                            }
                        }
                    } catch (_: Exception) {
                        // ignore
                    }
                }
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

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            drawable.bitmap?.let { return it }
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun getLifecycleOwner(context: android.content.Context): LifecycleOwner? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is LifecycleOwner) return ctx
            ctx = ctx.baseContext
        }
        return null
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
