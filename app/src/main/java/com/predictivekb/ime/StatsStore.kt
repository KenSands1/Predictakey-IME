package com.predictivekb.ime

import android.content.Context

data class TypingStats(
    val totalCharactersTyped: Long,
    val backspaces: Long,
    val charactersSaved: Long,
    val wordsCompleted: Long,
    val macroUses: Long,
    val averageWpm: Float,
    val bestWpm: Float,
    val sessionCount: Int
)

/**
 * Tracks typing statistics, gated entirely behind [isEnabled] - every
 * record* method is a no-op when tracking is off, so callers don't need to
 * check the flag themselves everywhere.
 *
 * WPM is measured per typing SESSION rather than "since the keyboard
 * opened", since that would tank the number every time the user pauses to
 * think. A session is a burst of activity with no gap longer than
 * [SESSION_IDLE_GAP_MS]; when a gap that long is detected (or the keyboard
 * is dismissed - see [flushSession]), the just-finished session's WPM
 * (characters / 5, divided by its duration) gets folded into a running
 * average and compared against the best-ever session.
 *
 * "Characters saved" counts letters the user didn't have to type because a
 * suggestion, family swap, or macro filled them in - this deliberately
 * does NOT subtract anything for the tap itself; a single tap is treated
 * as comparable effort to one keystroke, so the full gap counts as saved.
 */
class StatsStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "typing_stats"
        private const val SESSION_IDLE_GAP_MS = 4000L
        private const val MIN_SESSION_MINUTES = 0.02 // ~1.2 seconds; ignore degenerate sessions
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Runtime-only session state - not persisted per keystroke, only when a session ends.
    private var sessionStartTime = 0L
    private var sessionCharCount = 0
    private var lastActivityTime = 0L

    fun isEnabled(): Boolean = prefs.getBoolean("enabled", false)

    fun setEnabled(enabled: Boolean) {
        if (!enabled) flushSession()
        prefs.edit().putBoolean("enabled", enabled).apply()
    }

    /** Call for every character that ends up in the text field, from any source. */
    fun recordCharactersTyped(count: Int) {
        if (!isEnabled() || count <= 0) return
        val now = System.currentTimeMillis()
        if (sessionStartTime == 0L || now - lastActivityTime > SESSION_IDLE_GAP_MS) {
            flushSession()
            sessionStartTime = now
            sessionCharCount = 0
        }
        sessionCharCount += count
        lastActivityTime = now
        prefs.edit().putLong("total_chars", prefs.getLong("total_chars", 0L) + count).apply()
    }

    fun recordBackspace() {
        if (!isEnabled()) return
        prefs.edit().putLong("backspaces", prefs.getLong("backspaces", 0L) + 1).apply()
    }

    fun recordCharactersSaved(count: Int) {
        if (!isEnabled() || count <= 0) return
        prefs.edit().putLong("chars_saved", prefs.getLong("chars_saved", 0L) + count).apply()
    }

    fun recordWordCompleted() {
        if (!isEnabled()) return
        prefs.edit().putLong("words_completed", prefs.getLong("words_completed", 0L) + 1).apply()
    }

    fun recordMacroUsed() {
        if (!isEnabled()) return
        prefs.edit().putLong("macro_uses", prefs.getLong("macro_uses", 0L) + 1).apply()
    }

    /**
     * Ends the current typing session (if any) and folds its WPM into the
     * running average/best. Safe to call even with no session active.
     * Called automatically on a long-enough idle gap, and should also be
     * called when the keyboard is dismissed so a session isn't left
     * dangling unrecorded.
     */
    fun flushSession() {
        if (sessionStartTime == 0L || sessionCharCount == 0) {
            sessionStartTime = 0L
            sessionCharCount = 0
            return
        }
        val durationMinutes = (lastActivityTime - sessionStartTime) / 60000.0
        if (durationMinutes >= MIN_SESSION_MINUTES) {
            val wpm = (sessionCharCount / 5.0) / durationMinutes
            val priorCount = prefs.getInt("session_count", 0)
            val priorAvg = prefs.getFloat("avg_wpm", 0f)
            val newAvg = ((priorAvg * priorCount) + wpm) / (priorCount + 1)
            val bestWpm = prefs.getFloat("best_wpm", 0f)
            prefs.edit()
                .putFloat("avg_wpm", newAvg.toFloat())
                .putInt("session_count", priorCount + 1)
                .putFloat("best_wpm", maxOf(bestWpm, wpm.toFloat()))
                .apply()
        }
        sessionStartTime = 0L
        sessionCharCount = 0
    }

    fun getStats(): TypingStats {
        flushSession() // fold in whatever's pending so the numbers are current
        return TypingStats(
            totalCharactersTyped = prefs.getLong("total_chars", 0L),
            backspaces = prefs.getLong("backspaces", 0L),
            charactersSaved = prefs.getLong("chars_saved", 0L),
            wordsCompleted = prefs.getLong("words_completed", 0L),
            macroUses = prefs.getLong("macro_uses", 0L),
            averageWpm = prefs.getFloat("avg_wpm", 0f),
            bestWpm = prefs.getFloat("best_wpm", 0f),
            sessionCount = prefs.getInt("session_count", 0)
        )
    }

    /** Clears all recorded numbers. Leaves the enabled/disabled setting untouched. */
    fun reset() {
        val wasEnabled = isEnabled()
        prefs.edit().clear().putBoolean("enabled", wasEnabled).apply()
        sessionStartTime = 0L
        sessionCharCount = 0
    }
}
