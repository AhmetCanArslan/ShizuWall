package com.arslan.shizuwall.adapters

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.drawable.Animatable
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.arslan.shizuwall.model.AppInfo
import androidx.recyclerview.widget.RecyclerView
import com.arslan.shizuwall.FirewallMode
import com.arslan.shizuwall.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.color.MaterialColors
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
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
    private val onAppIconClick: (AppInfo) -> Unit = {},
    private val onAppSwitchLongClick: (AppInfo) -> Unit = {}
) : ListAdapter<AppInfo, AppListAdapter.AppViewHolder>(AppInfoDiffCallback()) {

    private val iconCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

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
    private var isWhitelistMode: Boolean = false

    fun setMode(mode: FirewallMode) {
        val hybrid = mode == FirewallMode.HYBRID
        val whitelist = mode == FirewallMode.WHITELIST
        if (isHybridMode == hybrid && isWhitelistMode == whitelist) return
        isHybridMode = hybrid
        isWhitelistMode = whitelist
        notifyItemRangeChanged(0, itemCount)
    }

    private var mobileOnlyPackages: Set<String> = emptySet()

    private fun Set<String>.symmetricDifference(other: Set<String>): Set<String> = (this - other) + (other - this)

    fun setMobileOnlyPackages(packages: Set<String>) {
        if (mobileOnlyPackages == packages) return
        val changed = mobileOnlyPackages.symmetricDifference(packages)
        mobileOnlyPackages = packages
        currentList.forEachIndexed { index, app -> if (app.key in changed) notifyItemChanged(index, PAYLOAD_SWITCH_ICON) }
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
        val favoriteBadge: ImageView = itemView.findViewById(R.id.favoriteBadge)
        private var boundKey: String? = null
        private var boundFavorite: Boolean? = null
        private var boundSelected: Boolean? = null
        private var cardColorAnimator: ValueAnimator? = null

        fun bind(appInfo: AppInfo) {
            val pkg = appInfo.packageName

            UiUtils.loadAppIcon(appIcon, pkg, appInfo.userId, iconCache)

            appName.text = appInfo.appName
            packageName.text = pkg

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

            appSwitch.setImageResource(switchIcon(appInfo.key, appInfo.isSelected, false))
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
            val animateSelection = boundKey == appInfo.key && boundSelected != null &&
                boundSelected != appInfo.isSelected
            boundSelected = appInfo.isSelected
            cardColorAnimator?.cancel()
            if (animateSelection) {
                animateSelection(card.cardBackgroundColor.defaultColor, cardBgColor)
            } else {
                card.setCardBackgroundColor(cardBgColor)
            }

            val accent = if (appInfo.isSelected) {
                MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnPrimaryContainer)
            } else {
                MaterialColors.getColor(itemView, android.R.attr.colorPrimary)
            }
            favoriteBadge.imageTintList = android.content.res.ColorStateList.valueOf(accent)
            favoriteBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(cardBgColor)
            val animateFavorite = boundKey == appInfo.key && boundFavorite != null &&
                boundFavorite != appInfo.isFavorite
            boundKey = appInfo.key
            boundFavorite = appInfo.isFavorite
            if (animateFavorite) {
                animateFavoriteBadge(appInfo.isFavorite)
            } else {
                showFavoriteBadge(appInfo.isFavorite)
            }

            if (favoriteEnabled) {
                itemView.setOnLongClickListener {
                    onAppLongClick(appInfo)
                    true
                }
            } else {
                itemView.setOnLongClickListener(null)
            }

            val dim = if (appInfo.isSelectable) 1f else 0.5f
            (appInfoButton.parent as ViewGroup).children.forEach { it.alpha = if (it === appInfoButton) 1f else dim }
            bindInteractions(appInfo, selectionEnabled)
        }

        private fun animateSelection(fromColor: Int, toColor: Int) {
            cardColorAnimator = ValueAnimator.ofArgb(fromColor, toColor).apply {
                duration = SELECTION_ANIM_MS
                addUpdateListener { card.setCardBackgroundColor(it.animatedValue as Int) }
                start()
            }
            appSwitch.setImageResource(switchIcon(boundKey.orEmpty(), boundSelected == true, true))
            (appSwitch.drawable as Animatable).start()
        }

        private fun switchIcon(key: String, selected: Boolean, animated: Boolean): Int {
            val mobile = key in mobileOnlyPackages
            val slashed = selected != isWhitelistMode
            return when {
                mobile && slashed -> if (animated) R.drawable.avd_mobile_draw else R.drawable.mobile_circle_block_24dp
                mobile -> if (animated) R.drawable.avd_mobile_erase else R.drawable.mobile_circle_24dp
                slashed -> if (animated) R.drawable.avd_slash_draw else R.drawable.circle_block_24dp
                else -> if (animated) R.drawable.avd_slash_erase else R.drawable.circle_24dp
            }
        }

        fun fadeSwitchIcon(appInfo: AppInfo) {
            appSwitch.animate().cancel()
            appSwitch.animate().alpha(0f).setDuration(SWITCH_FADE_MS).withEndAction {
                appSwitch.setImageResource(switchIcon(appInfo.key, appInfo.isSelected, false))
                appSwitch.animate().alpha(1f).setDuration(SWITCH_FADE_MS).start()
            }.start()
        }

        private fun showFavoriteBadge(favorite: Boolean) {
            favoriteBadge.animate().cancel()
            favoriteBadge.visibility = if (favorite) View.VISIBLE else View.GONE
            favoriteBadge.alpha = if (favorite) 1f else 0f
            favoriteBadge.scaleX = 1f
            favoriteBadge.scaleY = 1f
        }

        private fun animateFavoriteBadge(favorite: Boolean) {
            favoriteBadge.animate().cancel()
            if (favorite) {
                favoriteBadge.visibility = View.VISIBLE
                favoriteBadge.alpha = 0f
                favoriteBadge.scaleX = FAVORITE_SCALE_FROM
                favoriteBadge.scaleY = FAVORITE_SCALE_FROM
                favoriteBadge.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(FAVORITE_FADE_IN_MS)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            } else {
                favoriteBadge.animate()
                    .alpha(0f)
                    .scaleX(FAVORITE_SCALE_FROM)
                    .scaleY(FAVORITE_SCALE_FROM)
                    .setDuration(FAVORITE_FADE_OUT_MS)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction { favoriteBadge.visibility = View.GONE }
                    .start()
            }
        }

        private fun bindInteractions(appInfo: AppInfo, selectionEnabled: Boolean) {
            val blockable = appInfo.isSelectable
            if (selectionEnabled && blockable) {
                val toggled = appInfo.copy(isSelected = !appInfo.isSelected)
                appSwitch.isEnabled = true
                appSwitch.alpha = 1.0f
                itemView.isClickable = true
                itemView.setOnClickListener { onAppClick(toggled) }
                appSwitch.setOnClickListener { onAppClick(toggled) }
                appSwitch.setOnLongClickListener { onAppSwitchLongClick(appInfo); true }
                modeDropdownText.setOnClickListener { view -> showModePopupMenu(view, appInfo) }
                return
            }

            appSwitch.isEnabled = false
            appSwitch.setOnLongClickListener(null)
            appSwitch.alpha = 0.4f
            modeDropdownText.setOnClickListener(null)

            val explainUnsupported = selectionEnabled && !blockable
            itemView.isClickable = explainUnsupported
            if (explainUnsupported) {
                itemView.setOnClickListener {
                    Snackbar.make(itemView, if (appInfo.hasInternet) R.string.app_unsupported_system_uid else R.string.app_info_internet_no, Snackbar.LENGTH_LONG)
                        .setTextMaxLines(4)
                        .setAction(R.string.ok) { }
                        .show()
                }
            } else {
                itemView.setOnClickListener(null)
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
        popupMenu.setForceShowIcon(true)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            if (appInfo.appFirewallMode != menuItem.itemId) {
                onAppClick(appInfo.copy(appFirewallMode = menuItem.itemId))
            }
            true
        }
        popupMenu.show()
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int, payloads: MutableList<Any>) {
        if (PAYLOAD_SWITCH_ICON in payloads) holder.fadeSwitchIcon(getItem(position)) else super.onBindViewHolder(holder, position, payloads)
    }

    private companion object {
        const val FAVORITE_FADE_IN_MS = 260L
        const val FAVORITE_FADE_OUT_MS = 160L
        const val FAVORITE_SCALE_FROM = 0.4f
        const val SELECTION_ANIM_MS = 220L
        const val SWITCH_FADE_MS = 140L
        const val PAYLOAD_SWITCH_ICON = "switch_icon"
    }
}
