package com.arslan.shizuwall.utils

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object UiUtils {

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            drawable.bitmap?.let { return it }
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun loadAppIcon(view: ImageView, packageName: String, userId: Int, cache: LruCache<String, Bitmap>? = null) {
        val key = AppKey.of(userId, packageName)
        view.tag = key
        view.setImageDrawable(null)
        cache?.get(key)?.let { view.setImageBitmap(it); return }
        val context = view.context
        getLifecycleOwner(context)?.lifecycleScope?.launch(Dispatchers.IO) {
            try {
                val drawable = CrossUserAppInfo.icon(context, packageName, userId)
                    ?: ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
                    ?: return@launch
                val bitmap = drawableToBitmap(drawable)
                cache?.put(key, bitmap)
                withContext(Dispatchers.Main) { if (view.tag == key) view.setImageBitmap(bitmap) }
            } catch (_: Exception) {
            }
        }
    }

    fun getLifecycleOwner(context: Context): LifecycleOwner? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is LifecycleOwner) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
