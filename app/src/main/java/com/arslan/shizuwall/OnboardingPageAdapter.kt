package com.arslan.shizuwall

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class OnboardingPageAdapter(
    private val pages: List<OnboardingPage>,
    private val activity: OnboardingActivity
) : RecyclerView.Adapter<OnboardingPageAdapter.PageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.onboarding_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(pages[position])
    }

    override fun getItemCount(): Int = pages.size

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView? = itemView.findViewById(R.id.page_image)
        private val titleText: TextView = itemView.findViewById(R.id.page_title)
        private val messageText: TextView = itemView.findViewById(R.id.page_message)
        private val actionButton: MaterialButton = itemView.findViewById(R.id.page_button)

        fun bind(page: OnboardingPage) {
            // Show/hide image based on whether imageResId is provided
            if (page.imageResId != null) {
                imageView?.visibility = View.VISIBLE
                imageView?.setImageResource(page.imageResId)
            } else {
                imageView?.visibility = View.GONE
            }

            titleText.text = page.title
            messageText.text = page.message
            actionButton.text = page.buttonText
            actionButton.setOnClickListener { page.onButtonClick() }
        }
    }
}
