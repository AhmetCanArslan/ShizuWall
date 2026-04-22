package com.arslan.shizuwall.adapters

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.arslan.shizuwall.model.AppInfo
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.graphics.createBitmap
import com.arslan.shizuwall.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import androidx.core.graphics.ColorUtils

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
    private val onAppLongClick: (AppInfo) -> Unit
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

    private var isHybridMode: Boolean = false

    fun setHybridModeEnabled(enabled: Boolean) {
        if (isHybridMode != enabled) {
            isHybridMode = enabled
            notifyItemRangeChanged(0, itemCount)
        }
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView as MaterialCardView
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val appName: TextView = itemView.findViewById(R.id.appName)
        val packageName: TextView = itemView.findViewById(R.id.packageName)
        val appSwitch: com.google.android.material.materialswitch.MaterialSwitch = itemView.findViewById(R.id.appSwitch)
        val favoriteIcon: ImageView = itemView.findViewById(R.id.favoriteIcon)
        val modeDropdownText: MaterialButton = itemView.findViewById(R.id.modeDropdownText)

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

            if (appInfo.isSelected && isHybridMode) {
                modeDropdownText.visibility = View.VISIBLE
                val iconRes = when (appInfo.appFirewallMode) {
                    1 -> R.drawable.intelligence_24px
                    2 -> R.drawable.mobile_lock_portrait_24px
                    else -> R.drawable.wifi_off_24px
                }
                modeDropdownText.icon = itemView.context.getDrawable(iconRes)
            } else {
                modeDropdownText.visibility = View.GONE
            }

            // Avoid triggering listener when recycling views
            appSwitch.setOnCheckedChangeListener(null)
            appSwitch.isChecked = appInfo.isSelected

            val surfaceColor = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorSurface)
            val surfaceVariantColor = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorSurfaceVariant)
            val cardBgColor = if (appInfo.isSelected) {
                val primaryContainer = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorPrimaryContainer)
                ColorUtils.blendARGB(surfaceColor, primaryContainer, 0.55f)
            } else {
                ColorUtils.blendARGB(surfaceColor, surfaceVariantColor, 0.25f)
            }
            card.setCardBackgroundColor(cardBgColor)

            if (selectionEnabled) {
                appSwitch.isEnabled = true
                appSwitch.setOnCheckedChangeListener { _, isChecked ->
                    onAppClick(appInfo.copy(isSelected = isChecked))
                }
                itemView.isClickable = true
                itemView.setOnClickListener {
                    appSwitch.isChecked = !appSwitch.isChecked
                }
                itemView.setOnLongClickListener {
                    onAppLongClick(appInfo)
                    true
                }
                
                modeDropdownText.setOnClickListener { view ->
                    val popupMenu = android.widget.PopupMenu(view.context, view)
                    popupMenu.menu.add(0, 0, 0, view.context.getString(R.string.hybrid_mode_default_block)).setIcon(R.drawable.wifi_off_24px)
                    popupMenu.menu.add(0, 1, 1, view.context.getString(R.string.hybrid_mode_smart_foreground)).setIcon(R.drawable.intelligence_24px)
                    popupMenu.menu.add(0, 2, 2, view.context.getString(R.string.hybrid_mode_screen_lock)).setIcon(R.drawable.mobile_lock_portrait_24px)
                    
                    // Force show icons in PopupMenu
                    try {
                        val fields = popupMenu.javaClass.declaredFields
                        for (field in fields) {
                            if ("mPopup" == field.name) {
                                field.isAccessible = true
                                val menuPopupHelper = field.get(popupMenu)
                                val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                                val setForceIcons = classPopupHelper.getMethod("setForceShowIcon", java.lang.Boolean.TYPE)
                                setForceIcons.invoke(menuPopupHelper, true)
                                break
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    popupMenu.setOnMenuItemClickListener { menuItem ->
                        val newMode = menuItem.itemId
                        if (appInfo.appFirewallMode != newMode) {
                            onAppClick(appInfo.copy(appFirewallMode = newMode))
                        }
                        true
                    }
                    popupMenu.show()
                }
            } else {
                // disable interactions while firewall active
                appSwitch.isEnabled = false
                appSwitch.setOnCheckedChangeListener(null)
                itemView.setOnClickListener(null)
                itemView.setOnLongClickListener(null)
                itemView.isClickable = false
                modeDropdownText.setOnClickListener(null)
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
