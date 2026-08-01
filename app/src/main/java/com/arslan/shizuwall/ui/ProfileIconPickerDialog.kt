package com.arslan.shizuwall.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arslan.shizuwall.R
import com.arslan.shizuwall.profiles.ProfileIcons
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object ProfileIconPickerDialog {

    fun show(context: Context, currentKey: String, onPicked: (String) -> Unit) {
        val recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 5)
            val pad = (12 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            clipToPadding = false
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.profile_change_icon)
            .setView(recyclerView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        recyclerView.adapter = IconAdapter(currentKey) { key ->
            onPicked(key)
            dialog.dismiss()
        }

        dialog.show()
    }

    private class IconAdapter(
        private val currentKey: String,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<IconAdapter.IconViewHolder>() {

        private val keys = ProfileIcons.keys

        override fun getItemCount() = keys.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_profile_icon, parent, false)
            return IconViewHolder(view)
        }

        override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
            holder.bind(keys[position], keys[position] == currentKey, onClick)
        }

        class IconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val image: ImageView = itemView.findViewById(R.id.iconImage)

            fun bind(key: String, isSelected: Boolean, onClick: (String) -> Unit) {
                image.setImageResource(ProfileIcons.resFor(key))
                val colorAttr =
                    if (isSelected) androidx.appcompat.R.attr.colorPrimary
                    else com.google.android.material.R.attr.colorOnSurfaceVariant
                image.setColorFilter(MaterialColors.getColor(image, colorAttr))
                itemView.setBackgroundResource(
                    if (isSelected) R.drawable.profile_avatar_bg_active else 0
                )
                itemView.setOnClickListener { onClick(key) }
            }
        }
    }
}
