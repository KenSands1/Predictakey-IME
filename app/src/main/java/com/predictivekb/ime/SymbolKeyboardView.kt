package com.predictivekb.ime

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat

/**
 * The numbers/symbols panel: digits where the prediction row would be on
 * the letters panel, then two rows of punctuation, then a bottom action row
 * matching the letters keyboard (comma / space / period / enter) plus the
 * key that switches back to letters.
 */
class SymbolKeyboardView(context: Context) : LinearLayout(context) {

    private val ROW_DIGITS = "1234567890"
    private val ROW_SYMBOLS_1 = "@#$_&-+()/"
    private val ROW_SYMBOLS_2 = "*\"':;!?~"

    var listener: KeyboardActionListener? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(ContextCompat.getColor(context, R.color.keyboard_bg))
        setPadding(dp(4), dp(6), dp(4), dp(6))

        addView(buildRow(ROW_DIGITS.toList(), dp(46), 0))
        addView(buildRow(ROW_SYMBOLS_1.toList(), dp(44), 0))
        addView(buildSymbolsRow2())
        addView(buildActionRow())
    }

    private fun buildRow(keys: List<Char>, height: Int, sideMargin: Int): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height).also {
                it.leftMargin = sideMargin
                it.rightMargin = sideMargin
                it.topMargin = dp(3)
                it.bottomMargin = dp(3)
            }
        }
        for (ch in keys) {
            val btn = makeKey(ch.toString(), R.drawable.key_bg_normal, 1f)
            btn.setOnClickListener { listener?.onCharKey(ch) }
            row.addView(btn)
        }
        return row
    }

    private fun buildSymbolsRow2(): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).also {
                it.topMargin = dp(3)
                it.bottomMargin = dp(3)
            }
        }
        val toLetters = makeKey("ABC", R.drawable.key_bg_special, 1.5f)
        toLetters.setOnClickListener { listener?.onSwitchToLetters() }
        row.addView(toLetters)

        for (ch in ROW_SYMBOLS_2) {
            val btn = makeKey(ch.toString(), R.drawable.key_bg_normal, 1f)
            btn.setOnClickListener { listener?.onCharKey(ch) }
            row.addView(btn)
        }

        val backspace = makeKey("⌫", R.drawable.key_bg_special, 1.5f)
        backspace.setOnClickListener { listener?.onBackspace() }
        row.addView(backspace)
        return row
    }

    private fun buildActionRow(): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).also {
                it.topMargin = dp(3)
            }
        }
        val abcButton = makeKey("ABC", R.drawable.key_bg_special, 1.3f)
        abcButton.setOnClickListener { listener?.onSwitchToLetters() }
        row.addView(abcButton)

        val comma = makeKey(",", R.drawable.key_bg_normal, 0.8f)
        comma.setOnClickListener { listener?.onCharKey(',') }
        row.addView(comma)

        val space = makeKey("space", R.drawable.key_bg_space_normal, 3.4f)
        space.setOnClickListener { listener?.onSpace() }
        row.addView(space)

        val period = makeKey(".", R.drawable.key_bg_normal, 0.8f)
        period.setOnClickListener { listener?.onCharKey('.') }
        row.addView(period)

        val enter = makeKey("⏎", R.drawable.key_bg_special, 1.3f)
        enter.setOnClickListener { listener?.onEnter() }
        row.addView(enter)
        return row
    }

    private fun makeKey(label: String, bg: Int, weight: Float): Button {
        return Button(context).apply {
            text = label
            isAllCaps = false
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(ContextCompat.getColor(context, R.color.key_text))
            setBackgroundResource(bg)
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
}
