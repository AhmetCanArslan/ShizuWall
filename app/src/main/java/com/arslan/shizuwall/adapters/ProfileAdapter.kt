package com.arslan.shizuwall.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arslan.shizuwall.R
import com.arslan.shizuwall.model.Profile
import com.arslan.shizuwall.profiles.ProfileIcons
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class ProfileAdapter(
    private val onProfileClick: (Profile) -> Unit,
    private val onMenuClick: (Profile, View) -> Unit
) : ListAdapter<Profile, ProfileAdapter.ProfileViewHolder>(DiffCallback()) {

    private var activeProfileId: String? = null
    private var animateInId: String? = null

    fun setActiveProfileId(id: String?, animate: Boolean = false) {
        if (activeProfileId == id) return
        val previous = activeProfileId
        activeProfileId = id
        animateInId = if (animate) id else null
        currentList.forEachIndexed { index, profile ->
            if (profile.id == id || profile.id == previous) notifyItemChanged(index)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Profile>() {
        override fun areItemsTheSame(oldItem: Profile, newItem: Profile) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Profile, newItem: Profile) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_profile, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val profile = getItem(position)
        val playAnim = profile.id == animateInId
        if (playAnim) animateInId = null
        holder.bind(profile, profile.id == activeProfileId, playAnim, onProfileClick, onMenuClick)
    }

    class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.profileCard)
        private val avatar: View = itemView.findViewById(R.id.profileAvatar)
        private val glyph: ImageView = itemView.findViewById(R.id.profileGlyph)
        private val check: ImageView = itemView.findViewById(R.id.profileActiveCheck)
        private val name: TextView = itemView.findViewById(R.id.profileName)
        private val subtitle: TextView = itemView.findViewById(R.id.profileSubtitle)
        private val badge: TextView = itemView.findViewById(R.id.profileActiveBadge)
        private val menu: ImageButton = itemView.findViewById(R.id.profileMenu)

        fun bind(
            profile: Profile,
            isActive: Boolean,
            animate: Boolean,
            onProfileClick: (Profile) -> Unit,
            onMenuClick: (Profile, View) -> Unit
        ) {
            val ctx = itemView.context
            name.text = profile.name

            val count = profile.packages.size
            val appsLabel = ctx.resources.getQuantityString(R.plurals.profile_app_count, count, count)
            val modeLabel = friendlyModeName(ctx, profile.firewallMode)
            subtitle.text = ctx.getString(R.string.profile_subtitle_format, appsLabel, modeLabel)

            glyph.setImageResource(ProfileIcons.resFor(profile.icon))

            avatar.setBackgroundResource(
                if (isActive) R.drawable.profile_avatar_bg_active else R.drawable.profile_avatar_bg
            )
            glyph.visibility = if (isActive) View.GONE else View.VISIBLE
            check.visibility = if (isActive) View.VISIBLE else View.GONE
            badge.visibility = if (isActive) View.VISIBLE else View.GONE

            val stroke = if (isActive) {
                MaterialColors.getColor(card, android.R.attr.colorPrimary)
            } else {
                ContextCompat.getColor(ctx, android.R.color.transparent)
            }
            card.strokeColor = stroke
            card.strokeWidth = if (isActive) dp(ctx, 1.5f) else 0

            if (isActive && animate) {
                check.scaleX = 0f
                check.scaleY = 0f
                check.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(360)
                    .setInterpolator(OvershootInterpolator(2.2f))
                    .start()
            } else {
                check.scaleX = 1f
                check.scaleY = 1f
            }

            card.setOnClickListener { onProfileClick(profile) }
            menu.setOnClickListener { onMenuClick(profile, it) }
        }

        private fun dp(ctx: android.content.Context, value: Float): Int =
            (value * ctx.resources.displayMetrics.density).toInt()

        private fun friendlyModeName(ctx: android.content.Context, mode: String): String {
            val resId = when (mode) {
                "DEFAULT" -> R.string.firewall_mode_default
                "ADAPTIVE" -> R.string.firewall_mode_adaptive
                "SCREEN_LOCK_MODE" -> R.string.firewall_mode_screen_lock
                "SMART_FOREGROUND" -> R.string.firewall_mode_smart_foreground
                "WHITELIST" -> R.string.firewall_mode_whitelist
                "FOCUS_TRACKER" -> R.string.firewall_mode_focus_tracker
                "HYBRID" -> R.string.firewall_mode_hybrid
                else -> R.string.firewall_mode_default
            }
            return ctx.getString(resId)
        }
    }
}
