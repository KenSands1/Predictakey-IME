package com.predictivekb.ime

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var statsStore: StatsStore

    private lateinit var wpmView: TextView
    private lateinit var bestWpmView: TextView
    private lateinit var totalCharsView: TextView
    private lateinit var charsSavedView: TextView
    private lateinit var wordsCompletedView: TextView
    private lateinit var backspacesView: TextView
    private lateinit var macroUsesView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        statsStore = StatsStore(this)

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

        wpmView = findViewById(R.id.stat_wpm)
        bestWpmView = findViewById(R.id.stat_best_wpm)
        totalCharsView = findViewById(R.id.stat_total_chars)
        charsSavedView = findViewById(R.id.stat_chars_saved)
        wordsCompletedView = findViewById(R.id.stat_words_completed)
        backspacesView = findViewById(R.id.stat_backspaces)
        macroUsesView = findViewById(R.id.stat_macro_uses)

        val statsSwitch = findViewById<Switch>(R.id.stats_enabled_switch)
        statsSwitch.isChecked = statsStore.isEnabled()
        statsSwitch.setOnCheckedChangeListener { _, checked ->
            statsStore.setEnabled(checked)
        }

        findViewById<Button>(R.id.reset_stats_button).setOnClickListener {
            statsStore.reset()
            refreshStatsDisplay()
        }
    }

    override fun onResume() {
        super.onResume()
        // Catches stats from typing that happened since this screen was last shown.
        refreshStatsDisplay()
    }

    private fun refreshStatsDisplay() {
        val stats = statsStore.getStats()
        wpmView.text = "Average speed: %.1f WPM (%d sessions)".format(stats.averageWpm, stats.sessionCount)
        bestWpmView.text = "Best session: %.1f WPM".format(stats.bestWpm)
        totalCharsView.text = "Total characters typed: ${stats.totalCharactersTyped}"
        charsSavedView.text = "Characters saved by suggestions/macros: ${stats.charactersSaved}"
        wordsCompletedView.text = "Words completed: ${stats.wordsCompleted}"
        backspacesView.text = "Backspaces: ${stats.backspaces}"
        macroUsesView.text = "Macros used: ${stats.macroUses}"
    }
}
