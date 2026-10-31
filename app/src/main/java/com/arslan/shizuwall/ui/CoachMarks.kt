package com.arslan.shizuwall.ui

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.doOnLayout
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.arslan.shizuwall.R
import com.google.android.material.button.MaterialButton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class CoachMarks private constructor(
    activity: ComponentActivity,
    private val steps: List<Step>
) : FrameLayout(activity) {

    class Step(
        val target: () -> View?,
        val title: CharSequence,
        val message: CharSequence,
        val onTarget: ((View) -> Unit)? = null,
        val onShow: (() -> Unit)? = null
    )

    private val dp = resources.displayMetrics.density
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SCRIM }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * dp
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }
    private val hole = RectF()
    private val fromHole = RectF()
    private val toHole = RectF()
    private val scrim = Path()
    private val arrow = Path()
    private val arrowDrawn = Path()
    private val measure = PathMeasure()
    private var target: View? = null
    private var index = -1
    private val bubble = LayoutInflater.from(activity).inflate(R.layout.coach_mark_bubble, this, false)
    private val title: TextView = bubble.findViewById(R.id.coachTitle)
    private val message: TextView = bubble.findViewById(R.id.coachMessage)
    private val next: MaterialButton = bubble.findViewById(R.id.coachNext)
    private val back = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = dismiss()
    }
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = STEP_MS
        interpolator = FastOutSlowInInterpolator()
        addUpdateListener { frame(it.animatedValue as Float) }
    }

    init {
        setWillNotDraw(false)
        addView(bubble)
        isClickable = true
        bubble.findViewById<View>(R.id.coachSkip).setOnClickListener { dismiss() }
        next.setOnClickListener { if (index == steps.lastIndex) act() else advance() }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(scrim, scrimPaint)
        canvas.drawPath(arrowDrawn, arrowPaint)
    }

    private fun act() {
        val view = target ?: return
        steps[index].onTarget?.invoke(view)
        advance()
    }

    private fun advance() {
        index++
        val step = steps.getOrNull(index) ?: return dismiss()
        val view = step.target()?.takeIf { it.isShown && it.width > 0 } ?: return advance()
        target = view
        title.text = step.title
        message.text = step.message
        next.setText(if (index == steps.lastIndex && step.onTarget == null) R.string.get_started else R.string.next)
        view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
        val loc = IntArray(2).also(view::getLocationInWindow)
        val own = IntArray(2).also(::getLocationInWindow)
        val pad = HOLE_PAD * dp
        val left = (loc[0] - own[0]).toFloat()
        val top = (loc[1] - own[1]).toFloat()
        toHole.set(left - pad, top - pad, left + view.width + pad, top + view.height + pad)
        fromHole.set(hole.takeUnless { it.isEmpty } ?: RectF(toHole.centerX(), toHole.centerY(), toHole.centerX(), toHole.centerY()))
        placeBubble()
        animator.start()
        step.onShow?.invoke()
    }

    private fun placeBubble() {
        measureChildWithMargins(bubble, MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), 0,
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST), 0)
        val h = bubble.measuredHeight
        val margin = EDGE * dp
        val below = toHole.centerY() < height / 2f
        val wanted = if (below) toHole.bottom + GAP * dp else toHole.top - GAP * dp - h
        val y = wanted.coerceAtMost(height - h - margin).coerceAtLeast(margin)
        bubble.translationY = y
        bubble.alpha = 0f
        bubble.animate().alpha(1f).setStartDelay(BUBBLE_DELAY_MS).setDuration(BUBBLE_MS).start()
        val endX = toHole.centerX()
        val endY = if (below) toHole.bottom + ARROW_PAD * dp else toHole.top - ARROW_PAD * dp
        val startX = endX + (width / 2f - endX) * ARROW_BEND
        val startY = if (below) y - ARROW_PAD * dp else y + h + ARROW_PAD * dp
        val midY = (startY + endY) / 2
        val angle = atan2(endY - midY, 0f)
        val head = HEAD * dp
        arrow.rewind()
        arrow.moveTo(startX, startY)
        arrow.cubicTo(startX, midY, endX, midY, endX, endY)
        arrow.lineTo(endX - head * cos(angle - HEAD_ANGLE), endY - head * sin(angle - HEAD_ANGLE))
        arrow.lineTo(endX, endY)
        arrow.lineTo(endX - head * cos(angle + HEAD_ANGLE), endY - head * sin(angle + HEAD_ANGLE))
        measure.setPath(arrow, false)
    }

    private fun frame(t: Float) {
        val h = (t / HOLE_PART).coerceAtMost(1f)
        hole.set(
            fromHole.left + (toHole.left - fromHole.left) * h,
            fromHole.top + (toHole.top - fromHole.top) * h,
            fromHole.right + (toHole.right - fromHole.right) * h,
            fromHole.bottom + (toHole.bottom - fromHole.bottom) * h
        )
        val r = (minOf(hole.width(), hole.height()) / 2).coerceAtMost(RADIUS * dp)
        scrim.rewind()
        scrim.fillType = Path.FillType.EVEN_ODD
        scrim.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        scrim.addRoundRect(hole, r, r, Path.Direction.CW)
        arrowDrawn.rewind()
        val p = ((t - HOLE_PART) / (1 - HOLE_PART)).coerceIn(0f, 1f)
        measure.getSegment(0f, measure.length * p, arrowDrawn, true)
        invalidate()
    }

    private fun dismiss() {
        animator.cancel()
        back.remove()
        animate().alpha(0f).setDuration(BUBBLE_MS).withEndAction { (parent as? ViewGroup)?.removeView(this) }.start()
    }

    companion object {
        const val EXTRA_TOUR = "coach_marks_tour"
        private const val SCRIM = 0xB3000000.toInt()
        private const val STEP_MS = 700L
        private const val BUBBLE_MS = 180L
        private const val BUBBLE_DELAY_MS = 120L
        private const val HOLE_PART = 0.4f
        private const val HOLE_PAD = 10
        private const val RADIUS = 28
        private const val EDGE = 48
        private const val GAP = 80
        private const val ARROW_PAD = 8
        private const val ARROW_BEND = 0.35f
        private const val HEAD = 12
        private const val HEAD_ANGLE = 0.5f

        fun show(activity: ComponentActivity, steps: List<Step>, delay: Long = 0L) {
            val decor = activity.window.decorView as ViewGroup
            decor.postDelayed({
                val marks = CoachMarks(activity, steps)
                marks.alpha = 0f
                decor.addView(marks, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                activity.onBackPressedDispatcher.addCallback(activity, marks.back)
                marks.animate().alpha(1f).setDuration(BUBBLE_MS).start()
                marks.doOnLayout { marks.advance() }
            }, delay)
        }
    }
}
