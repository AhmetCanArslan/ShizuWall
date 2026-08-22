package com.arslan.shizuwall.adapters

import android.graphics.Bitmap
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.arslan.shizuwall.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.arslan.shizuwall.utils.CrossUserAppInfo
import com.arslan.shizuwall.utils.MultiUserApps
import com.arslan.shizuwall.utils.UiUtils

class AppInfoDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
    override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
        return oldItem.key == newItem.key
    }

    override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
        return oldItem == newItem
    }
}

class AppListAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo) -> Unit,
    private val onAppIconClick: (AppInfo) -> Unit = {}
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

    private var favoriteEnabled: Boolean = true

    fun setFavoriteEnabled(enabled: Boolean) {
        if (favoriteEnabled != enabled) {
            favoriteEnabled = enabled
            notifyItemRangeChanged(0, itemCount)
        }
    }

    private var infoButtonEnabled: Boolean = true

    fun setInfoButtonEnabled(enabled: Boolean) {
        if (infoButtonEnabled != enabled) {
            infoButtonEnabled = enabled
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
        val appSwitch: ImageView = itemView.findViewById(R.id.appSwitch)
        val profileBadge: TextView = itemView.findViewById(R.id.profileBadge)
        val modeDropdownText: MaterialButton = itemView.findViewById(R.id.modeDropdownText)
        val appInfoButton: ImageView = itemView.findViewById(R.id.appInfoButton)


        private fun buildFavoriteName(name: String): CharSequence {
            val context = itemView.context
            val starSize = (appName.textSize * 0.9f).toInt()
            val drawable = ContextCompat.getDrawable(
                context, android.R.drawable.btn_star_big_on
            )?.mutate() ?: return name
            drawable.setBounds(0, 0, starSize, starSize)
            drawable.setTint(MaterialColors.getColor(itemView, android.R.attr.colorPrimary))

            val builder = SpannableStringBuilder(name).append(" ★")
            val start = builder.length - 1
            builder.setSpan(
                ImageSpan(drawable, ImageSpan.ALIGN_BASELINE),
                start, builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            return builder
        }

        fun bind(appInfo: AppInfo) {
            val pkg = appInfo.packageName
            val iconKey = appInfo.key
            appIcon.tag = iconKey
            appIcon.setImageDrawable(null) // Clear previous

            val cached = iconCache.get(iconKey)
            if (cached != null) {
                appIcon.setImageBitmap(cached)
            } else {
                UiUtils.getLifecycleOwner(itemView.context)?.lifecycleScope?.launch(Dispatchers.IO) {
                    try {
                        val context = itemView.context
                        val drawable = CrossUserAppInfo.icon(context, pkg, appInfo.userId)
                            ?: ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
                            ?: return@launch
                        val bitmap = UiUtils.drawableToBitmap(drawable)
                        iconCache.put(iconKey, bitmap)
                        withContext(Dispatchers.Main) {
                            if (appIcon.tag == iconKey) {
                                appIcon.setImageBitmap(bitmap)
                            }
                        }
                    } catch (_: Exception) {
                        // ignore
                    }
                }
            }

            appName.text = if (appInfo.isFavorite) {
                buildFavoriteName(appInfo.appName)
            } else {
                appInfo.appName
            }
            packageName.text = appInfo.packageName

            if (appInfo.userId != 0) {
                profileBadge.visibility = View.VISIBLE
                profileBadge.text = MultiUserApps.userLabel(itemView.context, appInfo.userId)
            } else {
                profileBadge.visibility = View.GONE
            }

            appInfoButton.visibility = if (infoButtonEnabled) View.VISIBLE else View.GONE
            appInfoButton.setOnClickListener { onAppIconClick(appInfo) }
            appInfoButton.contentDescription = itemView.context.getString(
                R.string.app_info_icon_content_description, appInfo.appName
            )
            appInfoButton.imageTintList = android.content.res.ColorStateList.valueOf(
                MaterialColors.getColor(
                    itemView, com.google.android.material.R.attr.colorOnSurfaceVariant
                )
            )

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

            // Set icon based on selection state
            val iconRes = if (appInfo.isSelected) R.drawable.check_circle_24dp else R.drawable.circle_24dp
            appSwitch.setImageResource(iconRes)
            val tintColor = if (appInfo.isSelected) {
                MaterialColors.getColor(itemView, android.R.attr.colorPrimary)
            } else {
                MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurfaceVariant)
            }
            appSwitch.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)
            itemView.contentDescription = itemView.context.getString(
                if (appInfo.isSelected) R.string.app_switch_selected else R.string.app_switch_unselected,
                appInfo.appName, appInfo.packageName
            )

            val surfaceColor = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorSurface)
            val surfaceVariantColor = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorSurfaceVariant)
            val cardBgColor = if (appInfo.isSelected) {
                val primaryContainer = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorPrimaryContainer)
                ColorUtils.blendARGB(surfaceColor, primaryContainer, 0.55f)
            } else {
                ColorUtils.blendARGB(surfaceColor, surfaceVariantColor, 0.25f)
            }
            card.setCardBackgroundColor(cardBgColor)

            if (favoriteEnabled) {
                itemView.setOnLongClickListener {
                    onAppLongClick(appInfo)
                    true
                }
            } else {
                itemView.setOnLongClickListener(null)
            }

            if (selectionEnabled) {
                val isCurrentlySelected = appInfo.isSelected
                appSwitch.isEnabled = true
                appSwitch.alpha = 1.0f
                itemView.isClickable = true
                itemView.setOnClickListener {
                    onAppClick(appInfo.copy(isSelected = !isCurrentlySelected))
                }
                appSwitch.setOnClickListener {
                    onAppClick(appInfo.copy(isSelected = !isCurrentlySelected))
                }

                modeDropdownText.setOnClickListener { view ->
                    showModePopupMenu(view, appInfo)
                }
            } else {
                // disable selection interactions while firewall active
                appSwitch.isEnabled = false
                appSwitch.alpha = 0.4f
                itemView.setOnClickListener(null)
                itemView.isClickable = false
                modeDropdownText.setOnClickListener(null)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    private fun showModePopupMenu(anchor: android.view.View, appInfo: AppInfo) {
        val popupMenu = android.widget.PopupMenu(anchor.context, anchor)
        popupMenu.menu.add(0, 0, 0, anchor.context.getString(R.string.hybrid_mode_default_block)).setIcon(R.drawable.wifi_off_24px)
        popupMenu.menu.add(0, 1, 1, anchor.context.getString(R.string.hybrid_mode_smart_foreground)).setIcon(R.drawable.intelligence_24px)
        popupMenu.menu.add(0, 2, 2, anchor.context.getString(R.string.hybrid_mode_screen_lock)).setIcon(R.drawable.mobile_lock_portrait_24px)
        forcePopupMenuIcons(popupMenu)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            if (appInfo.appFirewallMode != menuItem.itemId) {
                onAppClick(appInfo.copy(appFirewallMode = menuItem.itemId))
            }
            true
        }
        popupMenu.show()
    }

    private fun forcePopupMenuIcons(popupMenu: android.widget.PopupMenu) {
        try {
            for (field in popupMenu.javaClass.declaredFields) {
                if ("mPopup" == field.name) {
                    field.isAccessible = true
                    val menuPopupHelper = field.get(popupMenu)
                    val clazz = Class.forName(menuPopupHelper.javaClass.name)
                    clazz.getMethod("setForceShowIcon", java.lang.Boolean.TYPE).invoke(menuPopupHelper, true)
                    break
                }
            }
        } catch (_: Exception) {}
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
