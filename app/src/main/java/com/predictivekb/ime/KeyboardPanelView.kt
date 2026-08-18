package com.predictivekb.ime

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat

/**
 * The main letters keyboard. Built entirely in code (no Keyboard/KeyboardView
 * legacy classes) so every key's label and background can be swapped
 * instantly as the user types — that's what the prediction row and the
 * green space bar need.
 */
class KeyboardPanelView(context: Context) : LinearLayout(context) {

    private val ROW_QWERTY_1 = "qwertyuiop"
    private val ROW_QWERTY_2 = "asdfghjkl"
    private val ROW_QWERTY_3 = "zxcvbnm"

    private val predictionButtons = mutableListOf<Button>()
    private val letterButtons = mutableListOf<Button>()
    private lateinit var shiftButton: Button
    private lateinit var backspaceButton: Button
    private lateinit var spaceButton: Button
    private lateinit var symbolsButton: Button
    private lateinit var enterButton: Button

    var listener: KeyboardActionListener? = null
    private var shiftActive = false
    private var currentSoleWord: String? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(ContextCompat.getColor(context, R.color.keyboard_bg))
        setPadding(dp(4), dp(6), dp(4), dp(6))

        addView(buildPredictionRow())
        addView(buildLetterRow(ROW_QWERTY_1.toList(), 0))
        addView(buildLetterRow(ROW_QWERTY_2.toList(), dp(18)))
        addView(buildBottomLetterRow())
        addView(buildActionRow())
    }

    // ---- row builders -----------------------------------------------------

    private fun buildPredictionRow(): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).also {
                it.bottomMargin = dp(4)
            }
        }
        repeat(6) {
            val btn = makeKey("", R.drawable.key_bg_prediction, weight = 1f)
            btn.setOnClickListener {
                val label = btn.text.toString()
                if (label.isNotEmpty()) listener?.onPredictionKey(label[0].lowercaseChar())
            }
            predictionButtons.add(btn)
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
        shiftButton.setOnClickListener {
            shiftActive = !shiftActive
            applyShiftVisuals()
            listener?.onShiftToggled()
        }
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

        val comma = makeKey(",", R.drawable.key_bg_normal, weight = 0.8f)
        comma.setOnClickListener { listener?.onCharKey(',') }
        row.addView(comma)

        spaceButton = makeKey("space", R.drawable.key_bg_space_normal, weight = 3.4f)
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
            setPadding(0, 0, 0, 0)
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

    // ---- external updates ----------------------------------------------

    /** Updates the 6 prediction keys. [letters] should already be ranked most- to least-likely. */
    fun updatePredictions(letters: List<Char>, rtl: Boolean) {
        val display = if (rtl) letters.reversed() else letters
        for (i in predictionButtons.indices) {
            val btn = predictionButtons[i]
            if (i < display.size) {
                btn.text = applyCase(display[i].toString())
                btn.visibility = View.VISIBLE
            } else {
                btn.text = ""
                btn.visibility = View.INVISIBLE
            }
        }
    }

    /** Pass null when there's no unique completion; pass the full word when there is exactly one. */
    fun setSoleCompletion(word: String?) {
        currentSoleWord = word
        if (word != null) {
            spaceButton.text = word
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

    fun setShiftActive(active: Boolean) {
        shiftActive = active
        applyShiftVisuals()
    }

    fun isShiftActive(): Boolean = shiftActive

    private fun applyShiftVisuals() {
        shiftButton.setBackgroundResource(
            if (shiftActive) R.drawable.key_bg_prediction else R.drawable.key_bg_special
        )
        for (btn in letterButtons) {
            btn.text = applyCase(btn.text.toString().lowercase())
        }
        for (btn in predictionButtons) {
            val text = btn.text.toString()
            if (text.isNotEmpty()) btn.text = applyCase(text.lowercase())
        }
    }

    private fun applyCase(s: String): String = if (shiftActive) s.uppercase() else s.lowercase()
}
