package com.arslan.shizuwall.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import com.arslan.shizuwall.R
import com.arslan.shizuwall.services.FloatingButtonService
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider


class FloatingButtonSettingsActivity : BaseActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var sliderOpacity: Slider
    private lateinit var sliderSize: Slider
    private lateinit var sliderFadeDelay: Slider
    private lateinit var switchEdgeSnap: MaterialSwitch
    private lateinit var switchDisableDim: MaterialSwitch
    private lateinit var tvOpacityValue: TextView
    private lateinit var tvSizeValue: TextView
    private lateinit var tvFadeDelayValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        enableEdgeToEdge()
        setContentView(R.layout.activity_floating_button_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupSettingsChrome(R.id.floatingSettingsRoot, toolbar)

        sliderOpacity = findViewById(R.id.sliderOpacity)
        sliderSize = findViewById(R.id.sliderSize)
        sliderFadeDelay = findViewById(R.id.sliderFadeDelay)
        switchEdgeSnap = findViewById(R.id.switchEdgeSnap)
        switchDisableDim = findViewById(R.id.switchDisableDim)
        tvOpacityValue = findViewById(R.id.tvOpacityValue)
        tvSizeValue = findViewById(R.id.tvSizeValue)
        tvFadeDelayValue = findViewById(R.id.tvFadeDelayValue)

        loadValues()
        setupListeners()
    }

    private fun loadValues() {
        val opacity = sharedPreferences.getInt(
            FloatingButtonService.KEY_FLOATING_IDLE_OPACITY, FloatingButtonService.DEFAULT_IDLE_OPACITY
        ).coerceIn(0, 100)
        val size = sharedPreferences.getInt(
            FloatingButtonService.KEY_FLOATING_SIZE, FloatingButtonService.DEFAULT_SIZE_DP
        ).coerceIn(40, 96)
        val fadeDelay = sharedPreferences.getInt(
            FloatingButtonService.KEY_FLOATING_FADE_DELAY, FloatingButtonService.DEFAULT_FADE_DELAY
        ).coerceIn(1, 15)

        sliderOpacity.value = opacity.toFloat()
        sliderSize.value = size.toFloat()
        sliderFadeDelay.value = fadeDelay.toFloat()
        switchEdgeSnap.isChecked = sharedPreferences.getBoolean(
            FloatingButtonService.KEY_FLOATING_EDGE_SNAP, false
        )
        switchDisableDim.isChecked = sharedPreferences.getBoolean(
            FloatingButtonService.KEY_FLOATING_DISABLE_DIM, false
        )

        updateOpacityLabel(opacity)
        updateSizeLabel(size)
        updateFadeDelayLabel(fadeDelay)
    }

    private fun setupListeners() {
        sliderOpacity.addOnChangeListener { _, value, _ ->
            val v = value.toInt()
            updateOpacityLabel(v)
            sharedPreferences.edit().putInt(FloatingButtonService.KEY_FLOATING_IDLE_OPACITY, v).apply()
        }
        sliderSize.addOnChangeListener { _, value, _ ->
            val v = value.toInt()
            updateSizeLabel(v)
            sharedPreferences.edit().putInt(FloatingButtonService.KEY_FLOATING_SIZE, v).apply()
        }
        sliderFadeDelay.addOnChangeListener { _, value, _ ->
            val v = value.toInt()
            updateFadeDelayLabel(v)
            sharedPreferences.edit().putInt(FloatingButtonService.KEY_FLOATING_FADE_DELAY, v).apply()
        }
        switchEdgeSnap.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(FloatingButtonService.KEY_FLOATING_EDGE_SNAP, isChecked).apply()
        }
        switchDisableDim.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(FloatingButtonService.KEY_FLOATING_DISABLE_DIM, isChecked).apply()
        }
    }

    private fun updateOpacityLabel(value: Int) {
        tvOpacityValue.text = getString(R.string.floating_percent_format, value)
    }

    private fun updateSizeLabel(value: Int) {
        tvSizeValue.text = getString(R.string.floating_dp_format, value)
    }

    private fun updateFadeDelayLabel(value: Int) {
        tvFadeDelayValue.text = getString(R.string.floating_seconds_format, value)
    }
}
