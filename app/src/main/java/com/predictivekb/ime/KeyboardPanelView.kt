package com.predictivekb.ime

import android.content.Context
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat

/**
 * The main letters keyboard. Built entirely in code (no Keyboard/KeyboardView
 * legacy classes) so every key's label and background can be swapped
 * instantly as the user types — that's what the word-completion row and the
 * green space bar need.
 */
class KeyboardPanelView(context: Context) : LinearLayout(context) {

    private val ROW_QWERTY_1 = "qwertyuiop"
    private val ROW_QWERTY_2 = "asdfghjkl"
    private val ROW_QWERTY_3 = "zxcvbnm"

    private val wordButtons = mutableListOf<Button>()
    private val letterButtons = mutableListOf<Button>()
    private lateinit var shiftButton: Button
    private lateinit var backspaceButton: Button
    private lateinit var spaceButton: Button
    private lateinit var symbolsButton: Button
    private lateinit var enterButton: Button

    var listener: KeyboardActionListener? = null
    private var shiftState = ShiftState.OFF
    private var lastShiftTapTime = 0L
    private val DOUBLE_TAP_WINDOW_MS = 350L

    private var currentSoleWord: String? = null

    /** Raw (lowercase) words currently backing each word-completion button, index-aligned. */
    private var currentWords: List<String> = emptyList()

    /** Which of currentWords have their own family - shown with a trailing "+". */
    private var wordsWithFamily: Set<String> = emptySet()

    init {
        orientation = VERTICAL
        setBackgroundColor(ContextCompat.getColor(context, R.color.keyboard_bg))
        setPadding(dp(4), dp(6), dp(4), dp(6))

        addView(buildWordRow())
        addView(buildLetterRow(ROW_QWERTY_1.toList(), 0))
        addView(buildLetterRow(ROW_QWERTY_2.toList(), dp(18)))
        addView(buildBottomLetterRow())
        addView(buildActionRow())
    }

    // ---- row builders -----------------------------------------------------

    /**
     * The top row: 6 whole-word completion shortcuts, ranked most- to
     * least-frequent for whatever prefix has been typed so far. Tapping one
     * inserts the rest of that word (see [KeyboardActionListener.onWordSelected]).
     */
    private fun buildWordRow(): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).also {
                it.bottomMargin = dp(4)
            }
        }
        for (i in 0 until 6) {
            val btn = makeKey("", R.drawable.key_bg_prediction, weight = 1f)
            btn.maxLines = 1
            btn.ellipsize = TextUtils.TruncateAt.END
            // Full words vary a lot in length ("we" vs "products"), so let the
            // text shrink to fit rather than truncating whenever possible.
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                btn, 8, 15, 1, TypedValue.COMPLEX_UNIT_SP
            )
            btn.setOnClickListener {
                currentWords.getOrNull(i)?.let { word -> listener?.onWordSelected(word) }
            }
            wordButtons.add(btn)
            row.addView(btn)
        }
        return row
    }

    private fun buildLetterRow(keys: List<Char>, sideMarginPx: Int): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).also {
                it.leftMargin = sideMarginPx
                it.rightMargin = sideMarginPx
                it.topMargin = dp(3)
                it.bottomMargin = dp(3)
            }
        }
        for (ch in keys) {
            val btn = makeKey(ch.uppercase(), R.drawable.key_bg_normal, weight = 1f)
            btn.setOnClickListener {
                val label = btn.text.toString()
                if (label.isNotEmpty()) listener?.onCharKey(label[0].lowercaseChar())
            }
            letterButtons.add(btn)
            row.addView(btn)
        }
        return row
    }

    private fun buildBottomLetterRow(): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).also {
                it.topMargin = dp(3)
                it.bottomMargin = dp(3)
            }
        }
        shiftButton = makeKey("⇧", R.drawable.key_bg_special, weight = 1.5f)
        shiftButton.setOnClickListener { onShiftTapped() }
        row.addView(shiftButton)

        for (ch in ROW_QWERTY_3) {
            val btn = makeKey(ch.uppercase(), R.drawable.key_bg_normal, weight = 1f)
            btn.setOnClickListener {
                val label = btn.text.toString()
                if (label.isNotEmpty()) listener?.onCharKey(label[0].lowercaseChar())
            }
            letterButtons.add(btn)
            row.addView(btn)
        }

        backspaceButton = makeKey("⌫", R.drawable.key_bg_special, weight = 1.5f)
        backspaceButton.setOnClickListener { listener?.onBackspace() }
        row.addView(backspaceButton)
        return row
    }

    private fun buildActionRow(): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).also {
                it.topMargin = dp(3)
            }
        }

        symbolsButton = makeKey("?123", R.drawable.key_bg_special, weight = 1.3f)
        symbolsButton.setOnClickListener { listener?.onSwitchToSymbols() }
        row.addView(symbolsButton)

        val macrosButton = makeKey("⊞", R.drawable.key_bg_special, weight = 1.0f)
        macrosButton.setOnClickListener { listener?.onSwitchToMacros() }
        row.addView(macrosButton)

        val comma = makeKey(",", R.drawable.key_bg_normal, weight = 0.8f)
        comma.setOnClickListener { listener?.onCharKey(',') }
        row.addView(comma)

        spaceButton = makeKey("space", R.drawable.key_bg_space_normal, weight = 2.6f)
        spaceButton.setOnClickListener { listener?.onSpace() }
        row.addView(spaceButton)

        val period = makeKey(".", R.drawable.key_bg_normal, weight = 0.8f)
        period.setOnClickListener { listener?.onCharKey('.') }
        row.addView(period)

        enterButton = makeKey("⏎", R.drawable.key_bg_special, weight = 1.3f)
        enterButton.setOnClickListener { listener?.onEnter() }
        row.addView(enterButton)

        return row
    }

    // ---- key factory --------------------------------------------------

    private fun makeKey(label: String, bg: Int, weight: Float): Button {
        return Button(context).apply {
            text = label
            isAllCaps = false
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(ContextCompat.getColor(context, R.color.key_text))
            setBackgroundResource(bg)
            setPadding(dp(2), 0, dp(2), 0)
            minWidth = 0
            minHeight = 0
            stateListAnimator = null
            elevation = 0f
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).also {
                it.marginStart = dp(2)
                it.marginEnd = dp(2)
            }
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    // ---- shift key state machine ---------------------------------------

    /**
     * OFF --tap--> SHIFT_ONCE --tap--> OFF
     * A second tap arriving within [DOUBLE_TAP_WINDOW_MS] of the previous
     * one (from either OFF or SHIFT_ONCE) jumps straight to CAPS_LOCK.
     * CAPS_LOCK --tap--> OFF.
     */
    private fun onShiftTapped() {
        val now = SystemClock.uptimeMillis()
        val isDoubleTap = (now - lastShiftTapTime) <= DOUBLE_TAP_WINDOW_MS
        lastShiftTapTime = now

        shiftState = when {
            isDoubleTap -> ShiftState.CAPS_LOCK
            shiftState == ShiftState.OFF -> ShiftState.SHIFT_ONCE
            else -> ShiftState.OFF // was SHIFT_ONCE or CAPS_LOCK, single tap cancels it
        }
        applyShiftVisuals()
        listener?.onShiftToggled()
    }

    fun getShiftState(): ShiftState = shiftState

    /** Service calls this after committing text so external (auto-cap) logic can arm SHIFT_ONCE. */
    fun setShiftState(state: ShiftState) {
        shiftState = state
        applyShiftVisuals()
    }

    /** Called after a single letter is typed: SHIFT_ONCE spends itself, CAPS_LOCK persists. */
    fun consumeShiftOnce() {
        if (shiftState == ShiftState.SHIFT_ONCE) {
            shiftState = ShiftState.OFF
            applyShiftVisuals()
        }
    }

    /** True if the next typed letter should be uppercase (either SHIFT_ONCE or CAPS_LOCK). */
    fun isShiftActive(): Boolean = shiftState != ShiftState.OFF

    // ---- external updates ----------------------------------------------

    /**
     * Updates the 6 word-completion keys. [words] should already be ranked
     * most- to least-frequent (see [PredictionEngine.topCompletions]). Any
     * unused keys (fewer than 6 candidates — normal for rare prefixes) are
     * simply hidden rather than left showing stale words. [wordsWithFamily]
     * marks which of [words] have their own inflected forms - those get a
     * trailing "+" so it's clear tapping them opens more options rather
     * than just finishing the word.
     */
    fun updateWordCompletions(words: List<String>, rtl: Boolean, wordsWithFamily: Set<String> = emptySet()) {
        currentWords = if (rtl) words.reversed() else words
        this.wordsWithFamily = wordsWithFamily
        for (i in wordButtons.indices) {
            val btn = wordButtons[i]
            if (i < currentWords.size) {
                btn.text = displayLabel(currentWords[i])
                btn.visibility = View.VISIBLE
            } else {
                btn.text = ""
                btn.visibility = View.INVISIBLE
            }
        }
    }

    private fun displayLabel(word: String): String {
        val cased = WordCasing.apply(word, shiftState)
        return if (word in wordsWithFamily) "$cased+" else cased
    }

    /** Pass null when there's no unique completion; pass the full word when there is exactly one. */
    fun setSoleCompletion(word: String?) {
        currentSoleWord = word
        if (word != null) {
            spaceButton.text = WordCasing.apply(word, shiftState)
            spaceButton.setBackgroundResource(R.drawable.key_bg_space_ready)
            spaceButton.setTextColor(ContextCompat.getColor(context, R.color.space_text_ready))
            spaceButton.setTypeface(spaceButton.typeface, Typeface.BOLD)
        } else {
            spaceButton.text = "space"
            spaceButton.setBackgroundResource(R.drawable.key_bg_space_normal)
            spaceButton.setTextColor(ContextCompat.getColor(context, R.color.key_text))
            spaceButton.setTypeface(spaceButton.typeface, Typeface.NORMAL)
        }
    }

    fun getSoleCompletion(): String? = currentSoleWord

    private fun applyShiftVisuals() {
        shiftButton.text = if (shiftState == ShiftState.CAPS_LOCK) "⇪" else "⇧"
        shiftButton.setBackgroundResource(
            when (shiftState) {
                ShiftState.OFF -> R.drawable.key_bg_special
                ShiftState.SHIFT_ONCE -> R.drawable.key_bg_prediction
                ShiftState.CAPS_LOCK -> R.drawable.key_bg_space_ready
            }
        )
        val letterCase = shiftState != ShiftState.OFF
        for (btn in letterButtons) {
            val lower = btn.text.toString().lowercase()
            btn.text = if (letterCase) lower.uppercase() else lower
        }
        for (i in wordButtons.indices) {
            if (i < currentWords.size) {
                wordButtons[i].text = displayLabel(currentWords[i])
            }
        }
        currentSoleWord?.let { spaceButton.text = WordCasing.apply(it, shiftState) }
    }
}


