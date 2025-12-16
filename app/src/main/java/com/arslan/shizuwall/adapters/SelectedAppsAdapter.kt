package com.arslan.shizuwall.adapters

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
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
import com.arslan.shizuwall.model.AppInfo

class SelectedAppsAdapter(
    private val appList: List<AppInfo>,
    private val typeface: Typeface? = null
) : RecyclerView.Adapter<SelectedAppsAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val appName: TextView = itemView.findViewById(R.id.appName)

        init {
            typeface?.let {
                appName.typeface = it
            }
        }

        fun bind(appInfo: AppInfo) {
            val pkg = appInfo.packageName
            appIcon.tag = pkg
            appIcon.setImageDrawable(null)

            getLifecycleOwner(itemView.context)?.lifecycleScope?.launch(Dispatchers.IO) {
                try {
                    val pm = itemView.context.packageManager
                    val drawable = pm.getApplicationIcon(pkg)
                    val bitmap = drawableToBitmap(drawable)
                    withContext(Dispatchers.Main) {
                        if (appIcon.tag == pkg) {
                            appIcon.setImageBitmap(bitmap)
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }

            appName.text = appInfo.appName
        }
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

    private fun getLifecycleOwner(context: android.content.Context): LifecycleOwner? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is LifecycleOwner) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selected_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(appList[position])
    }

    override fun getItemCount(): Int = appList.size
}
