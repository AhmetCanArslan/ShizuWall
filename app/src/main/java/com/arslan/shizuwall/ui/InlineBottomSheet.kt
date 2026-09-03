package com.arslan.shizuwall.ui

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class InlineBottomSheet(private val activity: Activity, private val content: View) {

    private val root = activity.findViewById<ViewGroup>(android.R.id.content)
    private val container = FrameLayout(activity)
    private val scrim = View(activity)

    private var dismissListener: (() -> Unit)? = null
    private var dismissed = false
    private var backCallback: OnBackPressedCallback? = null

    fun setOnDismissListener(listener: () -> Unit) {
        dismissListener = listener
    }

    fun show() {
        scrim.setBackgroundColor(Color.BLACK)
        scrim.alpha = 0f
        scrim.isClickable = true
        scrim.setOnClickListener { dismiss() }
        container.addView(
            scrim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        content.visibility = View.INVISIBLE
        container.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bottom)
            insets
        }

        root.addView(
            container,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        ViewCompat.requestApplyInsets(content)

        content.post {
            content.visibility = View.VISIBLE
            content.translationY = content.height.toFloat()
            content.animate()
                .translationY(0f)
                .setDuration(ENTER_DURATION)
                .setInterpolator(ENTER_INTERPOLATOR)
                .start()
            scrim.animate().alpha(SCRIM_ALPHA).setDuration(ENTER_DURATION).start()
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = dismiss()
        }
        backCallback = callback
        (activity as? ComponentActivity)?.onBackPressedDispatcher?.addCallback(callback)
    }

    fun dismiss() {
        if (dismissed) return
        dismissed = true
        backCallback?.remove()
        scrim.animate().alpha(0f).setDuration(EXIT_DURATION).start()
        content.animate()
            .translationY(content.height.toFloat())
            .setDuration(EXIT_DURATION)
            .setInterpolator(EXIT_INTERPOLATOR)
            .withEndAction { detach() }
            .start()
    }

    private fun detach() {
        if (container.parent == null) return
        root.removeView(container)
        dismissListener?.invoke()
    }

    companion object {
        private const val SCRIM_ALPHA = 0.32f
        private const val ENTER_DURATION = 250L
        private const val EXIT_DURATION = 200L
        private val ENTER_INTERPOLATOR = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        private val EXIT_INTERPOLATOR = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
    }
}
