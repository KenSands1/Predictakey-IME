package com.predictivekb.ime

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import kotlin.math.abs

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

    /** Raw (lowercase) words currently backing each word-completion button, index-aligned. */
    private var currentWords: List<String> = emptyList()

    /** Which of currentWords have their own family - shown with a trailing "+". */
    private var wordsWithFamily: Set<String> = emptySet()

    /** The word currently shown on the space key, if what's typed exactly matches a dictionary word. */
    private var exactWord: String? = null

    /**
     * The key the current word-completion row is customized under (the exact
     * typed prefix, or "family:<root>" for a root's second-stage swap row),
     * or null when the row isn't eligible for customization at all.
     * Non-null is what arms long-press-to-edit and
     * long-press-and-drag-to-reorder on the word row; it's also the key
     * [listener] is told to persist any resulting override under.
     */
    private var editablePrefix: String? = null

    // ---- word-row drag/long-press state ---------------------------------
    private val gestureHandler = Handler(Looper.getMainLooper())
    private var pendingLongPress: Runnable? = null
    private var dragArmed = false
    private var dragMoved = false
    private var dragSlotIndex = -1
    private var downX = 0f
    private var downY = 0f
    private val TOUCH_SLOP_PX = dpRaw(context, 10)
    private val LONG_PRESS_MS = 400L

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
     * least-frequent for whatever prefix has been typed so far. A plain tap
     * inserts the rest of that word (see [KeyboardActionListener.onWordSelected]).
     * When the row is showing plain prefix completions (not a root's family
     * - see [editablePrefix]), each slot also supports long-press-and-drag
     * to reorder, and a plain long-press (no drag) to assign or replace the
     * word in that slot, including an empty one.
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
            // Truncate from the START, not the end - when several family
            // members share a long common beginning ("administration" /
            // "administrative" / "administrator"), cutting off the end
            // would make them all display identically. The end is what
            // actually differs, so that's what needs to stay visible.
            btn.ellipsize = TextUtils.TruncateAt.START
            // Full words vary a lot in length ("we" vs "products"), so let the
            // text shrink to fit rather than truncating whenever possible.
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                btn, 8, 15, 1, TypedValue.COMPLEX_UNIT_SP
            )
            attachWordButtonGestures(btn, i)
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
     * most- to least-frequent (see [PredictionEngine.topCompletions]), and
     * may contain "" entries representing a deliberately-left-blank slot in
     * a user-customized row. Any unused keys beyond [words]'s size (normal
     * for rare prefixes, or a shorter engine-computed row) are hidden
     * rather than left showing stale words. [wordsWithFamily] marks which
     * of [words] have their own inflected forms - those get a trailing "+".
     *
     * [editablePrefix] is the exact typed prefix this row belongs to, or
     * null when this row isn't eligible for the long-press edit/reorder
     * gestures at all (currently: a root word's family-swap row). Passing
     * non-null arms those gestures and is what any resulting override gets
     * persisted under.
     */
    fun updateWordCompletions(
        words: List<String>,
        rtl: Boolean,
        wordsWithFamily: Set<String> = emptySet(),
        editablePrefix: String? = null
    ) {
        currentWords = if (rtl) words.reversed() else words
        this.wordsWithFamily = wordsWithFamily
        this.editablePrefix = editablePrefix
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
        if (word.isEmpty()) return ""
        val cased = WordCasing.apply(word, shiftState)
        return if (word in wordsWithFamily) "$cased+" else cased
    }

    /**
     * Shows [word] on the space key as confirmation that what's already
     * been typed is a real, complete dictionary word - or reverts to a
     * plain "space" label when [word] is null. This is purely visual:
     * pressing space always just adds a space after whatever's typed
     * either way, so there's no risk of substituting in something the
     * user didn't actually type.
     */
    fun setExactWordIndicator(word: String?) {
        exactWord = word
        if (word != null) {
            spaceButton.text = WordCasing.apply(word, shiftState)
            spaceButton.setBackgroundResource(R.drawable.key_bg_space_ready)
        } else {
            spaceButton.text = "space"
            spaceButton.setBackgroundResource(R.drawable.key_bg_space_normal)
        }
    }

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
        exactWord?.let { spaceButton.text = WordCasing.apply(it, shiftState) }
    }

    // ---- word-row long-press / drag-to-reorder / tap-to-select ---------

    /**
     * Installs one touch handler that covers all three gestures a
     * completion slot supports: a quick tap (select, unchanged from
     * before - no added delay), a long-press-and-release-in-place (edit
     * this slot via [showSlotEditDialog]), and a long-press-and-drag
     * (live reorder via [swapCompletionSlots], committed on release via
     * [commitReorder]). When [editablePrefix] is null (the family-swap
     * row), this degrades to a plain tap-to-select with no long-press
     * behavior at all, since there's nothing meaningful to persist there.
     */
    private fun attachWordButtonGestures(btn: Button, index: Int) {
        btn.setOnTouchListener { view, event ->
            if (editablePrefix == null) {
                return@setOnTouchListener handlePlainTapTouch(view, event, index)
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    view.isPressed = true
                    dragArmed = false
                    dragMoved = false
                    dragSlotIndex = index
                    val armRunnable = Runnable {
                        dragArmed = true
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                    pendingLongPress = armRunnable
                    gestureHandler.postDelayed(armRunnable, LONG_PRESS_MS)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragArmed) {
                        val targetIndex = buttonIndexAtRawX(event.rawX)
                        if (targetIndex != null && targetIndex != dragSlotIndex &&
                            targetIndex < currentWords.size
                        ) {
                            swapCompletionSlots(dragSlotIndex, targetIndex)
                            dragSlotIndex = targetIndex
                            dragMoved = true
                        }
                    } else if (!withinTouchSlop(event)) {
                        // Moved too far before the long-press timer fired -
                        // not a steady long-press, cancel it and do nothing
                        // special on release (not a clean tap either, so no
                        // selection fires - mirrors normal button behavior
                        // when a touch wanders off a key).
                        pendingLongPress?.let { gestureHandler.removeCallbacks(it) }
                        dragSlotIndex = -1
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    pendingLongPress?.let { gestureHandler.removeCallbacks(it) }
                    when {
                        dragArmed && dragMoved -> commitReorder()
                        dragArmed -> showSlotEditDialog(index)
                        dragSlotIndex == index && withinTouchSlop(event) ->
                            currentWords.getOrNull(index)?.takeIf { it.isNotEmpty() }
                                ?.let { listener?.onWordSelected(it) }
                    }
                    dragArmed = false
                    dragMoved = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    pendingLongPress?.let { gestureHandler.removeCallbacks(it) }
                    dragArmed = false
                    dragMoved = false
                    true
                }
                else -> true
            }
        }
    }

    /** Non-editable (family-swap) row: plain tap-to-select, no long-press behavior. */
    private fun handlePlainTapTouch(view: View, event: MotionEvent, index: Int): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                view.isPressed = true
                true
            }
            MotionEvent.ACTION_UP -> {
                view.isPressed = false
                if (withinTouchSlop(event)) {
                    currentWords.getOrNull(index)?.takeIf { it.isNotEmpty() }
                        ?.let { listener?.onWordSelected(it) }
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                view.isPressed = false
                true
            }
            else -> true
        }
    }

    private fun withinTouchSlop(event: MotionEvent): Boolean {
        return abs(event.rawX - downX) < TOUCH_SLOP_PX && abs(event.rawY - downY) < TOUCH_SLOP_PX
    }

    /** Which word-button index, if any, currently sits under [rawX] on screen. */
    private fun buttonIndexAtRawX(rawX: Float): Int? {
        val loc = IntArray(2)
        for (i in wordButtons.indices) {
            val btn = wordButtons[i]
            if (btn.visibility != View.VISIBLE) continue
            btn.getLocationOnScreen(loc)
            if (rawX >= loc[0] && rawX <= loc[0] + btn.width) return i
        }
        return null
    }

    /** Live-swaps two completion slots during a drag and redraws just those two labels. */
    private fun swapCompletionSlots(a: Int, b: Int) {
        if (a !in currentWords.indices || b !in currentWords.indices) return
        val mutable = currentWords.toMutableList()
        val tmp = mutable[a]
        mutable[a] = mutable[b]
        mutable[b] = tmp
        currentWords = mutable
        wordButtons[a].text = displayLabel(currentWords[a])
        wordButtons[b].text = displayLabel(currentWords[b])
    }

    /** Persists the row's current (post-drag) order as this prefix's override. */
    private fun commitReorder() {
        val prefix = editablePrefix ?: return
        val logicalOrder = if (Prefs.isPredictionRowRtl(context)) currentWords.reversed() else currentWords
        listener?.onCompletionsChanged(prefix, logicalOrder)
    }

    /**
     * A plain long-press (no drag) on [index]: offers to assign, replace,
     * or clear the word in that slot - including a currently-empty one,
     * which is how a word gets added to a slot that had nothing in it.
     */
    private fun showSlotEditDialog(index: Int) {
        val prefix = editablePrefix ?: return
        val current = currentWords.getOrNull(index).orEmpty()
        val input = EditText(context).apply {
            setText(current)
            setSingleLine(true)
            hint = "Word for this slot"
            setSelection(text.length)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(if (current.isEmpty()) "Add a word" else "Change this word")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                applySlotEdit(index, input.text.toString().trim().lowercase())
            }
            .setNeutralButton("Reset row") { _, _ ->
                listener?.onCompletionOverrideCleared(prefix)
            }
            .setNegativeButton("Cancel", null)
            .create()
        // An InputMethodService's context isn't an Activity, so a plain
        // AlertDialog needs its window explicitly attached to the keyboard's
        // own window (by type + token) or it won't be allowed to show.
        dialog.window?.apply {
            setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
            attributes = attributes.apply { token = this@KeyboardPanelView.windowToken }
        }
        dialog.show()
    }

    private fun applySlotEdit(index: Int, newWord: String) {
        val prefix = editablePrefix ?: return
        val slots = currentWords.toMutableList()
        while (slots.size < wordButtons.size) slots.add("")
        // Collapse stray spaces so a phrase like "they   will" is stored as "they will".
        if (index < slots.size) slots[index] = newWord.replace(Regex("\\s+"), " ")
        val logicalOrder = if (Prefs.isPredictionRowRtl(context)) slots.reversed() else slots
        listener?.onCompletionsChanged(prefix, logicalOrder)
    }
}

/** dp()-equivalent usable before the instance's own resources-backed dp() is available (field initializers). */
private fun dpRaw(context: Context, value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()





