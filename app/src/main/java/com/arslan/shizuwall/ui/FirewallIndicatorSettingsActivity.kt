package com.arslan.shizuwall.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.slider.Slider
import com.arslan.shizuwall.R

class FirewallIndicatorSettingsActivity : BaseActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var sliderX: Slider
    private lateinit var sliderY: Slider
    private lateinit var sliderSize: Slider
    private lateinit var btnXMinus: ImageButton
    private lateinit var btnXPlus: ImageButton
    private lateinit var btnYMinus: ImageButton
    private lateinit var btnYPlus: ImageButton
    private lateinit var btnSizeMinus: ImageButton
    private lateinit var btnSizePlus: ImageButton
    private lateinit var valueX: TextView
    private lateinit var valueY: TextView
    private lateinit var valueSize: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        enableEdgeToEdge()
        setContentView(R.layout.activity_firewall_indicator_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarFirewallIndicator)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.firewallIndicatorSettingsRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val toolbarParams = toolbar.layoutParams as android.view.ViewGroup.MarginLayoutParams
            toolbarParams.topMargin = systemBars.top
            toolbar.layoutParams = toolbarParams
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        sliderX = findViewById(R.id.sliderIndicatorX)
        sliderY = findViewById(R.id.sliderIndicatorY)
        sliderSize = findViewById(R.id.sliderIndicatorSize)
        btnXMinus = findViewById(R.id.btnIndicatorXMinus)
        btnXPlus = findViewById(R.id.btnIndicatorXPlus)
        btnYMinus = findViewById(R.id.btnIndicatorYMinus)
        btnYPlus = findViewById(R.id.btnIndicatorYPlus)
        btnSizeMinus = findViewById(R.id.btnIndicatorSizeMinus)
        btnSizePlus = findViewById(R.id.btnIndicatorSizePlus)
        valueX = findViewById(R.id.tvIndicatorXValue)
        valueY = findViewById(R.id.tvIndicatorYValue)
        valueSize = findViewById(R.id.tvIndicatorSizeValue)

        setupSlider(sliderX, 0, 2000, prefs.getInt(MainActivity.KEY_FIREWALL_INDICATOR_X, 24)) { value ->
            prefs.edit().putInt(MainActivity.KEY_FIREWALL_INDICATOR_X, value).apply()
            valueX.text = value.toString()
        }
        setupSlider(sliderY, -600, 3000, prefs.getInt(MainActivity.KEY_FIREWALL_INDICATOR_Y, 120)) { value ->
            prefs.edit().putInt(MainActivity.KEY_FIREWALL_INDICATOR_Y, value).apply()
            valueY.text = value.toString()
        }
        setupSlider(sliderSize, 24, 180, prefs.getInt(MainActivity.KEY_FIREWALL_INDICATOR_SIZE, 42)) { value ->
            prefs.edit().putInt(MainActivity.KEY_FIREWALL_INDICATOR_SIZE, value).apply()
            valueSize.text = value.toString()
        }

        valueX.text = sliderX.value.toInt().toString()
        valueY.text = sliderY.value.toInt().toString()
        valueSize.text = sliderSize.value.toInt().toString()

        bindStepButtons(sliderX, btnXMinus, btnXPlus)
        bindStepButtons(sliderY, btnYMinus, btnYPlus)
        bindStepButtons(sliderSize, btnSizeMinus, btnSizePlus)
    }

    private fun setupSlider(
        slider: Slider,
        min: Int,
        max: Int,
        initial: Int,
        onValueChanged: (Int) -> Unit
    ) {
        slider.valueFrom = min.toFloat()
        slider.valueTo = max.toFloat()
        slider.stepSize = 1f
        slider.value = initial.coerceIn(min, max).toFloat()
        slider.addOnChangeListener { _, value, _ ->
            onValueChanged(value.toInt())
        }
    }

    private fun bindStepButtons(slider: Slider, minus: ImageButton, plus: ImageButton) {
        minus.setOnClickListener {
            val next = (slider.value - slider.stepSize).coerceAtLeast(slider.valueFrom)
            slider.value = next
        }
        plus.setOnClickListener {
            val next = (slider.value + slider.stepSize).coerceAtMost(slider.valueTo)
            slider.value = next
        }
    }
}
