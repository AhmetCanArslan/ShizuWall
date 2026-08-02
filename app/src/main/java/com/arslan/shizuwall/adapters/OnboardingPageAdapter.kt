package com.arslan.shizuwall.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.arslan.shizuwall.R
import com.google.android.material.button.MaterialButton
import com.arslan.shizuwall.ui.OnboardingPage

class OnboardingPageAdapter(
    private val pages: List<OnboardingPage>
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
        private val secondaryButton: MaterialButton = itemView.findViewById(R.id.page_button_secondary)
        private val tertiaryButton: MaterialButton = itemView.findViewById(R.id.page_button_tertiary)

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

            if (page.secondaryButtonText != null && page.onSecondaryButtonClick != null) {
                secondaryButton.visibility = View.VISIBLE
                secondaryButton.text = page.secondaryButtonText
                secondaryButton.isEnabled = page.isSecondaryButtonEnabled
                secondaryButton.alpha = if (page.isSecondaryButtonEnabled) 1.0f else 0.5f
                secondaryButton.setOnClickListener { 
                    if (page.isSecondaryButtonEnabled) {
                        page.onSecondaryButtonClick.invoke()
                    }
                }
            } else {
                secondaryButton.visibility = View.GONE
            }

            if (page.tertiaryButtonText != null && page.onTertiaryButtonClick != null) {
                tertiaryButton.visibility = View.VISIBLE
                tertiaryButton.text = page.tertiaryButtonText
                tertiaryButton.isEnabled = page.isTertiaryButtonEnabled
                tertiaryButton.alpha = if (page.isTertiaryButtonEnabled) 1.0f else 0.5f
                tertiaryButton.setOnClickListener { 
                    if (page.isTertiaryButtonEnabled) {
                        page.onTertiaryButtonClick.invoke()
                    }
                }
            } else {
                tertiaryButton.visibility = View.GONE
            }
        }
    }
}
