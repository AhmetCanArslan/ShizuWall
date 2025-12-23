package com.arslan.shizuwall.adapter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.arslan.shizuwall.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ErrorEntry(
    val appName: String,
    val packageName: String,
    val errorText: String
)

class ErrorDetailsAdapter(
    private val errorList: List<ErrorEntry>,
    private val typeface: android.graphics.Typeface? = null
) : RecyclerView.Adapter<ErrorDetailsAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val appName: TextView = itemView.findViewById(R.id.appName)
        val errorText: TextView = itemView.findViewById(R.id.errorText)

        init {
            typeface?.let { appName.typeface = it }
            typeface?.let { errorText.typeface = it }
        }

        fun bind(entry: ErrorEntry) {
            val pkg = entry.packageName
            appIcon.tag = pkg
            appIcon.setImageDrawable(null)
            appName.text = entry.appName
            errorText.text = entry.errorText

            getLifecycleOwner(itemView.context)?.lifecycleScope?.launch(Dispatchers.IO) {
                try {
                    val pm = itemView.context.packageManager
                    val drawable = pm.getApplicationIcon(pkg)
                    val bitmap = drawableToBitmap(drawable)
                    withContext(Dispatchers.Main) {
                        if (appIcon.tag == pkg) appIcon.setImageBitmap(bitmap)
                    }
                } catch (_: Exception) {
                    // ignore
                }
            }
        }
    }

    private fun getLifecycleOwner(context: android.content.Context): LifecycleOwner? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is LifecycleOwner) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            drawable.bitmap?.let { return it }
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_error_detail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(errorList[position])
    }

    override fun getItemCount(): Int = errorList.size
}
