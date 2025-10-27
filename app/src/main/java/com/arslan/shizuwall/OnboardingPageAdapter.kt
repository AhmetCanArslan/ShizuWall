package com.arslan.shizuwall

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class OnboardingPageAdapter(private val activity: OnboardingActivity) : RecyclerView.Adapter<OnboardingPageAdapter.PageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.onboarding_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        when (position) {
            0 -> {
                holder.title.text = "Welcome to ShizuWall"
                holder.message.text = "This app needs Shizuku to work. Since it's using Shizuku, it can harm your device. I don't accept any responsibility for damage. You must accept this to continue."
                holder.button.text = "Accept and Continue"
                holder.button.setOnClickListener { activity.goToNextPage() }
            }
            1 -> {
                holder.title.text = "About ShizuWall"
                holder.message.text = "The firewall will be disabled when the device reboots. Rebooting will revert all changes made by this app.\n\nGitHub: https://github.com/yourusername/ShizuWall"
                holder.button.text = "Continue"
                holder.button.setOnClickListener { activity.finishOnboarding() }
                // Make GitHub link clickable
                holder.message.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yourusername/ShizuWall"))
                    activity.startActivity(intent)
                }
            }
        }
    }

    override fun getItemCount(): Int = 2

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.page_title)
        val message: TextView = itemView.findViewById(R.id.page_message)
        val button: MaterialButton = itemView.findViewById(R.id.page_button)
    }
}
