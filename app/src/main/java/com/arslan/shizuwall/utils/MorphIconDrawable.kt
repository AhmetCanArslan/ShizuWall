package com.arslan.shizuwall.utils

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser
import org.xmlpull.v1.XmlPullParser

class MorphIconDrawable(context: Context, fromRes: Int, toRes: Int) : Drawable() {

    private class Point(val x: Float, val y: Float, val start: Boolean)

    private val from = trace(context, fromRes)
    private val to = trace(context, toRes)
    private val count = maxOf(from.size, to.size)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private var tint: ColorStateList? = null
    private val size = (24 * context.resources.displayMetrics.density).toInt()

    var progress = 0f
        set(value) {
            field = value
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        path.rewind()
        val scale = bounds.width() / VIEWPORT
        val t = progress
        var open = false
        repeat(count) {
            val a = from[it * (from.size - 1) / (count - 1)]
            val b = to[it * (to.size - 1) / (count - 1)]
            val x = bounds.left + (a.x + (b.x - a.x) * t) * scale
            val y = bounds.top + (a.y + (b.y - a.y) * t) * scale
            val start = if (t < 0.5f) a.start else b.start
            if (start) {
                if (open) path.close()
                path.moveTo(x, y)
                open = true
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        paint.color = tint?.getColorForState(state, tint!!.defaultColor) ?: DEFAULT_COLOR
        canvas.drawPath(path, paint)
    }

    private fun trace(context: Context, res: Int): List<Point> {
        val parser = context.resources.getXml(res)
        var data = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT && data.isEmpty()) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "path") {
                data = parser.getAttributeValue(ANDROID_NS, "pathData").orEmpty()
            }
        }
        val measure = PathMeasure(PathParser.createPathFromPathData(data), false)
        val contours = buildList {
            do add(measure.length) while (measure.nextContour())
        }
        val total = contours.sum()
        val remeasure = PathMeasure(PathParser.createPathFromPathData(data), false)
        val spot = FloatArray(2)
        return buildList {
            contours.forEach { length ->
                val steps = (length / total * SAMPLES).toInt().coerceAtLeast(4)
                repeat(steps) {
                    remeasure.getPosTan(length * it / steps, spot, null)
                    add(Point(spot[0], spot[1], it == 0))
                }
                remeasure.nextContour()
            }
        }
    }

    override fun setTintList(tint: ColorStateList?) {
        this.tint = tint
        invalidateSelf()
    }

    override fun isStateful() = tint?.isStateful == true
    override fun onStateChange(state: IntArray) = tint?.isStateful == true
    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val VIEWPORT = 960f
        const val SAMPLES = 480
        const val DEFAULT_COLOR = 0xFF000000.toInt()
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
