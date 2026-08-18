package com.predictivekb.ime

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val group = findViewById<RadioGroup>(R.id.direction_group)
        val ltrId = R.id.direction_ltr
        val rtlId = R.id.direction_rtl

        group.check(if (Prefs.isPredictionRowRtl(this)) rtlId else ltrId)
        group.setOnCheckedChangeListener { _, checkedId ->
            Prefs.setPredictionRowRtl(this, checkedId == rtlId)
        }

        findViewById<Button>(R.id.open_keyboard_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
    }
}
