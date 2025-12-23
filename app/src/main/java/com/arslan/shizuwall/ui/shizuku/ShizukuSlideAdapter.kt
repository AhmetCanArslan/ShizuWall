package com.arslan.shizuwall.ui.shizuku

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.arslan.shizuwall.R
import java.io.IOException

class ShizukuSlideAdapter(
    private val slides: List<ShizukuSlide>,
    private val context: Context
) : RecyclerView.Adapter<ShizukuSlideAdapter.SlideViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.dialog_shizuku_slide, parent, false)
        return SlideViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
        holder.bind(slides[position])
    }

    override fun getItemCount(): Int = slides.size

    inner class SlideViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.slide_image)

        fun bind(slide: ShizukuSlide) {
            val name = slide.imageName
            if (name != null) {
                val resId = context.resources.getIdentifier(name.substringBeforeLast('.'), "drawable", context.packageName)
                if (resId != 0) {
                    val drawable = context.resources.getDrawable(resId, null)
                    imageView.setImageDrawable(drawable)
                    imageView.scaleType = ImageView.ScaleType.CENTER
                    val lp = imageView.layoutParams
                    lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    imageView.layoutParams = lp
                    return
                }

                try {
                    val `is` = context.assets.open("shizuku_setup/$name")
                    val bmp = BitmapFactory.decodeStream(`is`)
                    `is`.close()
                    imageView.setImageBitmap(bmp)
                    imageView.scaleType = ImageView.ScaleType.CENTER
                    val lp = imageView.layoutParams
                    lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    imageView.layoutParams = lp
                    return
                } catch (_: IOException) {
                    // ignore and fallback
                }
            }

            // Fallback icon
            imageView.setImageResource(R.drawable.ic_shizuku_black)
        }
    }
}
