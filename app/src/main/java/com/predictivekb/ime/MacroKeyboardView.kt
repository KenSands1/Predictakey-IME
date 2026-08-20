package com.predictivekb.ime

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat

/**
 * Third keyboard panel: a grid of user-defined macro buttons. Tapping a
 * configured button runs it (inserts text, shows/inserts a QR code, or
 * inserts an image); tapping an empty slot (shown as "+") does nothing
 * useful on its own, same as long-pressing any slot - both open the editor,
 * since a tap on an empty button has nothing to run anyway.
 *
 * This view only renders whatever [Macro]s it's given via [showPage] - it
 * has no idea about pages, storage, or how many total pages exist. The
 * service owns all of that and just tells this view what to draw.
 */
class MacroKeyboardView(context: Context) : LinearLayout(context) {

    companion object {
        const val ROWS = 4
        const val COLUMNS = 6
    }

    var listener: KeyboardActionListener? = null

    private val slotButtons = mutableListOf<Button>()
    private lateinit var pageLabel: TextView

    init {
        orientation = VERTICAL
        setBackgroundColor(ContextCompat.getColor(context, R.color.keyboard_bg))
        setPadding(dp(4), dp(6), dp(4), dp(6))

        for (row in 0 until ROWS) {
            addView(buildGridRow(row))
        }
        addView(buildActionRow())
    }

    private fun buildGridRow(rowIndex: Int): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).also {
                it.topMargin = dp(2)
                it.bottomMargin = dp(2)
            }
        }
        for (col in 0 until COLUMNS) {
            val slot = rowIndex * COLUMNS + col
            val btn = Button(context).apply {
                text = "+"
                isAllCaps = false
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(ContextCompat.getColor(context, R.color.key_text))
                setBackgroundResource(R.drawable.key_bg_normal)
                setPadding(dp(2), 0, dp(2), 0)
                minWidth = 0
                minHeight = 0
                stateListAnimator = null
                elevation = 0f
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, 8, 14, 1, TypedValue.COMPLEX_UNIT_SP
                )
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).also {
                    it.marginStart = dp(2)
                    it.marginEnd = dp(2)
                }
                setOnClickListener { listener?.onMacroTapped(slot) }
                setOnLongClickListener { listener?.onMacroLongPressed(slot); true }
            }
            slotButtons.add(btn)
            row.addView(btn)
        }
        return row
    }

    private fun buildActionRow(): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).also {
                it.topMargin = dp(3)
            }
        }

        val abcButton = makeActionKey("ABC", 1.3f)
        abcButton.setOnClickListener { listener?.onSwitchToLetters() }
        row.addView(abcButton)

        pageLabel = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.key_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2f)
        }
        row.addView(pageLabel)

        val nextButton = makeActionKey("→", 1.3f)
        nextButton.setOnClickListener { listener?.onNextMacroPage() }
        row.addView(nextButton)

        return row
    }

    private fun makeActionKey(label: String, weight: Float): Button {
        return Button(context).apply {
            text = label
            isAllCaps = false
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(ContextCompat.getColor(context, R.color.key_text))
            setBackgroundResource(R.drawable.key_bg_special)
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

    /**
     * Renders one page: [macrosBySlot] maps slot index (0..23) to its
     * configured Macro. Slots with no entry show "+". [pageNumber] and
     * [pageCount] are 1-based, just for the "Page X/Y" label.
     */
    fun showPage(macrosBySlot: Map<Int, Macro>, pageNumber: Int, pageCount: Int) {
        for (slot in slotButtons.indices) {
            val macro = macrosBySlot[slot]
            slotButtons[slot].text = if (macro != null && macro.label.isNotBlank()) macro.label else "+"
        }
        pageLabel.text = "Page $pageNumber/$pageCount"
    }
}
