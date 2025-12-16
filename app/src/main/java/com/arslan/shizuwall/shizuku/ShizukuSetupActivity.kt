package com.arslan.shizuwall.shizuku

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.button.MaterialButton
import com.google.android.material.appbar.MaterialToolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.enableEdgeToEdge
import com.arslan.shizuwall.R
import com.google.android.material.color.DynamicColors

class ShizukuSetupActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var descriptionView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_shizuku_setup)

        val root = findViewById<android.view.View>(R.id.shizukuSetupRoot)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // apply top inset to toolbar margin
            val toolbarParams = toolbar.layoutParams as android.view.ViewGroup.MarginLayoutParams
            toolbarParams.topMargin = systemBars.top
            toolbar.layoutParams = toolbarParams
            // ensure bottom nav doesn't overlap content: add bottom padding
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)
            insets
        }

        // Ensure toolbar colors match theme
        val typedValue = android.util.TypedValue()
        val theme = theme
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        val onSurfaceColor = typedValue.data
        toolbar.setTitleTextColor(onSurfaceColor)
        toolbar.navigationIcon?.setTint(onSurfaceColor)
        toolbar.setNavigationOnClickListener { finish() }

        viewPager = findViewById(R.id.shizukuViewPager)
        tabLayout = findViewById(R.id.shizukuTabLayout)
        descriptionView = findViewById(R.id.shizukuSlideDescription)

        val slides = loadSlides()

        viewPager.adapter = ShizukuSlideAdapter(slides, this)
        descriptionView.text = slides.firstOrNull()?.description ?: ""

        // center the current page's ImageView if visible (adapter uses wrap_content and center)
        // ensure ViewPager uses match_parent height and content is centered by the slide layout

        TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                descriptionView.text = slides.getOrNull(position)?.description ?: ""
            }
        })

        val prev = findViewById<MaterialButton>(R.id.prevButton)
        val next = findViewById<MaterialButton>(R.id.nextButton)

        // Ensure button colors match Material theme attributes in case XML tints didn't apply
        val tvPrimaryContainer = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, tvPrimaryContainer, true)
        val colorPrimaryContainer = tvPrimaryContainer.data
        val tvOnPrimaryContainer = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, tvOnPrimaryContainer, true)
        val colorOnPrimaryContainer = tvOnPrimaryContainer.data

        prev.setBackgroundColor(colorPrimaryContainer)
        prev.setTextColor(colorOnPrimaryContainer)
        next.setBackgroundColor(colorPrimaryContainer)
        next.setTextColor(colorOnPrimaryContainer)

        prev.setOnClickListener {
            if (viewPager.currentItem > 0) viewPager.currentItem = viewPager.currentItem - 1
        }

        next.setOnClickListener {
            if (viewPager.currentItem < (slides.size - 1)) {
                viewPager.currentItem = viewPager.currentItem + 1
            } else {
                finish()
            }
        }
    }

    private fun loadSlides(): List<ShizukuSlide> {
        val slides = mutableListOf<ShizukuSlide>()
        try {
            val rawAssets = assets.list("shizuku_setup") ?: emptyArray()
            val assetFiles = rawAssets.sortedWith(compareBy { filename ->
                Regex("(\\d+)").find(filename)?.value?.toIntOrNull() ?: Int.MAX_VALUE
            })

            if (assetFiles.isNotEmpty()) {
                for ((index, file) in assetFiles.withIndex()) {
                    if (index >= 11) break
                    val descRes = resources.getIdentifier("shizuku_step_${index + 1}", "string", packageName)
                    val desc = if (descRes != 0) getString(descRes) else ""
                    slides.add(ShizukuSlide(file, desc))
                }
                return slides
            }
        } catch (_: Exception) {
            // ignore and fall back
        }

        return slides
    }
}
