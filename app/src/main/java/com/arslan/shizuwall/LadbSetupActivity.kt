package com.arslan.shizuwall

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.arslan.shizuwall.R
import com.google.android.material.appbar.MaterialToolbar

class LadbSetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ladb_setup)

        val toolbar = findViewById<MaterialToolbar?>(R.id.ladbToolbar)
        toolbar?.setNavigationOnClickListener { finish() }
    }
}
