package com.predictivekb.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout

class PredictiveKeyboardService : InputMethodService(), KeyboardActionListener {

    private lateinit var container: FrameLayout
    private lateinit var lettersPanel: KeyboardPanelView
    private lateinit var symbolsPanel: SymbolKeyboardView

    private val engine = PredictionEngine()
    private val currentWord = StringBuilder()
    private var showingSymbols = false

    override fun onCreate() {
        super.onCreate()
        if (!engine.isLoaded()) {
            assets.open("wordlist.txt").use { engine.load(it) }
        }
    }

    override fun onCreateInputView(): View {
        container = FrameLayout(this)

        lettersPanel = KeyboardPanelView(this).apply { listener = this@PredictiveKeyboardService }
        symbolsPanel = SymbolKeyboardView(this).apply { listener = this@PredictiveKeyboardService }

        container.addView(lettersPanel)
        container.addView(symbolsPanel)
        showLetters()
        return container
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentWord.clear()
        showLetters()
        refreshPredictions()
    }

    // ---- panel switching --------------------------------------------------

    private fun showLetters() {
        showingSymbols = false
        lettersPanel.visibility = View.VISIBLE
        symbolsPanel.visibility = View.GONE
    }

    private fun showSymbols() {
        showingSymbols = true
        lettersPanel.visibility = View.GONE
        symbolsPanel.visibility = View.VISIBLE
    }

    override fun onSwitchToSymbols() = showSymbols()
    override fun onSwitchToLetters() = showLetters()

    // ---- prediction refresh -------------------------------------------

    private fun refreshPredictions() {
        val rtl = Prefs.isPredictionRowRtl(this)
        val prefix = currentWord.toString()
        val completions = engine.topCompletions(prefix)
        lettersPanel.updateWordCompletions(completions, rtl)
        lettersPanel.setSoleCompletion(engine.soleCompletion(prefix).takeIf { prefix.isNotEmpty() })
    }

    // ---- KeyboardActionListener -----------------------------------------

    override fun onCharKey(ch: Char) {
        val ic = currentInputConnection ?: return
        if (ch.isLetter()) {
            val output = if (lettersPanel.isShiftActive()) ch.uppercaseChar() else ch
            ic.commitText(output.toString(), 1)
            currentWord.append(ch.lowercaseChar())
            refreshPredictions()
        } else {
            // Punctuation resets the word buffer.
            ic.commitText(ch.toString(), 1)
            currentWord.clear()
            refreshPredictions()
        }
    }

    /**
     * A top-row word-completion key was tapped. Commits whatever's left of
     * [word] beyond what's already been typed, plus a trailing space — the
     * same "finish the word and move on" behavior as tapping the green
     * space bar, just reachable one keystroke earlier.
     */
    override fun onWordSelected(word: String) {
        val ic = currentInputConnection ?: return
        val prefix = currentWord.toString()
        if (word.length > prefix.length) {
            val remainder = word.substring(prefix.length)
            ic.commitText(remainder, 1)
        }
        ic.commitText(" ", 1)
        currentWord.clear()
        refreshPredictions()
    }

    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        ic.deleteSurroundingText(1, 0)
        if (currentWord.isNotEmpty()) {
            currentWord.deleteCharAt(currentWord.length - 1)
        }
        refreshPredictions()
    }

    override fun onSpace() {
        val ic = currentInputConnection ?: return
        val sole = lettersPanel.getSoleCompletion()
        if (sole != null && sole.length > currentWord.length) {
            val remainder = sole.substring(currentWord.length)
            ic.commitText(remainder, 1)
        }
        ic.commitText(" ", 1)
        currentWord.clear()
        refreshPredictions()
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        ic.commitText("\n", 1)
        currentWord.clear()
        refreshPredictions()
    }

    override fun onShiftToggled() {
        // Visual state is already flipped inside KeyboardPanelView; nothing
        // else to do unless you want shift to auto-release after one letter.
    }
}
