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
        maybeAutoCapitalize()
        val rtl = Prefs.isPredictionRowRtl(this)
        val prefix = currentWord.toString()
        // Selection stays frequency-based (topCompletions already ranks by
        // real-world frequency); this only reorders those same 6 words for
        // display, shortest first, so they're easier to visually scan.
        val completions = engine.topCompletions(prefix).sortedBy { it.length }
        lettersPanel.updateWordCompletions(completions, rtl)
        lettersPanel.setSoleCompletion(engine.soleCompletion(prefix).takeIf { prefix.isNotEmpty() })
    }

    /**
     * Arms SHIFT_ONCE whenever the cursor sits at the start of a sentence -
     * an empty field, or after ". "/"! "/"? "/a newline (ignoring trailing
     * spaces). Only acts when shift is currently OFF, so it never overrides
     * a state the user (or this same check, earlier) already set - e.g. if
     * the user manually cancels an auto-armed shift, this won't immediately
     * re-arm it, since nothing new gets committed until they type.
     */
    private fun maybeAutoCapitalize() {
        if (lettersPanel.getShiftState() != ShiftState.OFF) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(40, 0)?.toString() ?: ""
        val trimmed = before.trimEnd(' ', '\t')
        val shouldCap = trimmed.isEmpty() || trimmed.last() in charArrayOf('.', '!', '?', '\n')
        if (shouldCap) {
            lettersPanel.setShiftState(ShiftState.SHIFT_ONCE)
        }
    }

    // ---- KeyboardActionListener -----------------------------------------

    override fun onCharKey(ch: Char) {
        val ic = currentInputConnection ?: return
        if (ch.isLetter()) {
            val output = if (lettersPanel.isShiftActive()) ch.uppercaseChar() else ch
            ic.commitText(output.toString(), 1)
            currentWord.append(ch.lowercaseChar())
            lettersPanel.consumeShiftOnce()
            refreshPredictions()
        } else {
            // Punctuation resets the word buffer.
            ic.commitText(ch.toString(), 1)
            currentWord.clear()
            refreshPredictions()
        }
    }

    /**
     * A top-row word-completion key was tapped. Deletes whatever's already
     * been typed of the word and re-commits the whole thing correctly
     * cased (via [WordCasing]) plus a trailing space - replacing rather
     * than just appending, so proper-noun/shift capitalization is correct
     * even if the already-typed prefix was lowercase.
     */
    override fun onWordSelected(word: String) {
        val ic = currentInputConnection ?: return
        val prefix = currentWord.toString()
        if (prefix.isNotEmpty()) {
            ic.deleteSurroundingText(prefix.length, 0)
        }
        ic.commitText(WordCasing.apply(word, lettersPanel.getShiftState()), 1)
        ic.commitText(" ", 1)
        currentWord.clear()
        lettersPanel.consumeShiftOnce()
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
        if (sole != null) {
            val prefix = currentWord.toString()
            if (prefix.isNotEmpty()) {
                ic.deleteSurroundingText(prefix.length, 0)
            }
            ic.commitText(WordCasing.apply(sole, lettersPanel.getShiftState()), 1)
            lettersPanel.consumeShiftOnce()
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
        // else to do. Deliberately NOT calling refreshPredictions() here -
        // doing so would let maybeAutoCapitalize() immediately re-arm
        // SHIFT_ONCE right after the user manually cancels it.
    }
}
