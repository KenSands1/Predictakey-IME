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

    private var wordStartCapitalized = false
    private var activeRootFamily: String? = null

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
        wordStartCapitalized = false
        activeRootFamily = null
        showLetters()
        refreshPredictions()
    }

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

    private fun refreshPredictions() {
        maybeAutoCapitalize()
        val rtl = Prefs.isPredictionRowRtl(this)
        val prefix = currentWord.toString()
        val family = activeRootFamily
        val completions = if (family != null) {
            engine.familyMembers(family)
        } else {
            engine.topCompletions(prefix)
        }.sortedBy { it.length }
        lettersPanel.updateWordCompletions(completions, rtl)
        lettersPanel.setSoleCompletion(engine.soleCompletion(prefix).takeIf { prefix.isNotEmpty() })
    }

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

    private fun effectiveShiftStateForCompletion(): ShiftState {
        val live = lettersPanel.getShiftState()
        return when {
            live == ShiftState.CAPS_LOCK -> ShiftState.CAPS_LOCK
            wordStartCapitalized -> ShiftState.SHIFT_ONCE
            else -> live
        }
    }

    private fun markWordStartIfNeeded() {
        if (currentWord.isEmpty() && lettersPanel.getShiftState() == ShiftState.SHIFT_ONCE) {
            wordStartCapitalized = true
        }
    }

    override fun onCharKey(ch: Char) {
        val ic = currentInputConnection ?: return
        activeRootFamily = null
        if (ch.isLetter()) {
            markWordStartIfNeeded()
            val output = if (lettersPanel.isShiftActive()) ch.uppercaseChar() else ch
            ic.commitText(output.toString(), 1)
            currentWord.append(ch.lowercaseChar())
            lettersPanel.consumeShiftOnce()
            refreshPredictions()
        } else {
            if (ic.getTextBeforeCursor(1, 0)?.toString() == " ") {
                ic.deleteSurroundingText(1, 0)
            }
            ic.commitText(ch.toString(), 1)
            currentWord.clear()
            wordStartCapitalized = false
            refreshPredictions()
        }
    }

    override fun onWordSelected(word: String) {
        val ic = currentInputConnection ?: return
        val prefix = currentWord.toString()

        if (activeRootFamily == null) {
            markWordStartIfNeeded()
            if (prefix.isNotEmpty()) {
                ic.deleteSurroundingText(prefix.length, 0)
            }
            ic.commitText(WordCasing.apply(word, effectiveShiftStateForCompletion()), 1)
            currentWord.clear()
            currentWord.append(word.lowercase())
            activeRootFamily = word.lowercase()
            lettersPanel.consumeShiftOnce()
            refreshPredictions()
        } else {
            if (prefix.isNotEmpty()) {
                ic.deleteSurroundingText(prefix.length, 0)
            }
            ic.commitText(WordCasing.apply(word, effectiveShiftStateForCompletion()), 1)
            ic.commitText(" ", 1)
            currentWord.clear()
            wordStartCapitalized = false
            activeRootFamily = null
            lettersPanel.consumeShiftOnce()
            refreshPredictions()
        }
    }

    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        activeRootFamily = null
        ic.deleteSurroundingText(1, 0)
        if (currentWord.isNotEmpty()) {
            currentWord.deleteCharAt(currentWord.length - 1)
        }
        refreshPredictions()
    }

    override fun onSpace() {
        val ic = currentInputConnection ?: return
        activeRootFamily = null
        val sole = lettersPanel.getSoleCompletion()
        if (sole != null) {
            val prefix = currentWord.toString()
            if (prefix.isNotEmpty()) {
                ic.deleteSurroundingText(prefix.length, 0)
            }
            ic.commitText(WordCasing.apply(sole, effectiveShiftStateForCompletion()), 1)
            lettersPanel.consumeShiftOnce()
        }
        ic.commitText(" ", 1)
        currentWord.clear()
        wordStartCapitalized = false
        refreshPredictions()
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        activeRootFamily = null
        ic.commitText("\n", 1)
        currentWord.clear()
        wordStartCapitalized = false
        refreshPredictions()
    }

    override fun onShiftToggled() {
    }
}
