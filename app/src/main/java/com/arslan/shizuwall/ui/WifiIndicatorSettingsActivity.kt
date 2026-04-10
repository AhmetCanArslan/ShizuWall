package com.arslan.shizuwall.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.NumberPicker
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.arslan.shizuwall.R

class WifiIndicatorSettingsActivity : BaseActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var pickerX: NumberPicker
    private lateinit var pickerY: NumberPicker
    private lateinit var pickerSize: NumberPicker
    private lateinit var valueX: TextView
    private lateinit var valueY: TextView
    private lateinit var valueSize: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)

        enableEdgeToEdge()
        setContentView(R.layout.activity_wifi_indicator_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarWifiIndicator)
        toolbar.setNavigationOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.wifiIndicatorSettingsRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val toolbarParams = toolbar.layoutParams as android.view.ViewGroup.MarginLayoutParams
            toolbarParams.topMargin = systemBars.top
            toolbar.layoutParams = toolbarParams
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        pickerX = findViewById(R.id.pickerIndicatorX)
        pickerY = findViewById(R.id.pickerIndicatorY)
        pickerSize = findViewById(R.id.pickerIndicatorSize)
        valueX = findViewById(R.id.tvIndicatorXValue)
        valueY = findViewById(R.id.tvIndicatorYValue)
        valueSize = findViewById(R.id.tvIndicatorSizeValue)

        setupPicker(pickerX, 0, 2000, prefs.getInt(MainActivity.KEY_WIFI_INDICATOR_X, 24)) { value ->
            prefs.edit().putInt(MainActivity.KEY_WIFI_INDICATOR_X, value).apply()
            valueX.text = value.toString()
        }
        setupPicker(pickerY, 0, 3000, prefs.getInt(MainActivity.KEY_WIFI_INDICATOR_Y, 120)) { value ->
            prefs.edit().putInt(MainActivity.KEY_WIFI_INDICATOR_Y, value).apply()
            valueY.text = value.toString()
        }
        setupPicker(pickerSize, 24, 180, prefs.getInt(MainActivity.KEY_WIFI_INDICATOR_SIZE, 42)) { value ->
            prefs.edit().putInt(MainActivity.KEY_WIFI_INDICATOR_SIZE, value).apply()
            valueSize.text = value.toString()
        }

        valueX.text = pickerX.value.toString()
        valueY.text = pickerY.value.toString()
        valueSize.text = pickerSize.value.toString()
    }

    private fun setupPicker(
        picker: NumberPicker,
        min: Int,
        max: Int,
        initial: Int,
        onValueChanged: (Int) -> Unit
    ) {
        picker.minValue = min
        picker.maxValue = max
        picker.wrapSelectorWheel = false
        picker.value = initial.coerceIn(min, max)
        picker.setOnValueChangedListener { _, _, newVal ->
            onValueChanged(newVal)
        }
    }
}
