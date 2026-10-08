package com.predictivekb.ime

interface KeyboardActionListener {
    /** A regular letter/character key was tapped. */
    fun onCharKey(ch: Char)

    /**
     * One of the 6 top-row word-completion keys was tapped. [word] is the
     * full dictionary word (e.g. "what") regardless of how much of it the
     * user had already typed — the service figures out the remainder.
     */
    fun onWordSelected(word: String)

    fun onBackspace()
    fun onSpace()
    fun onEnter()
    fun onShiftToggled()

    /** Switch from the letters panel to the numbers/symbols panel, or back. */
    fun onSwitchToSymbols()
    fun onSwitchToLetters()

    // ---- Macro panel -----------------------------------------------
    // Default (empty) bodies so KeyboardPanelView/SymbolKeyboardView don't
    // need to implement what doesn't apply to them - only the service and
    // MacroKeyboardView actually use these.

    /** Switch to the macro panel (third keyboard). */
    fun onSwitchToMacros() {}

    /** A macro button was tapped - run it (insert text / show QR / insert image). */
    fun onMacroTapped(slot: Int) {}

    /** A macro button was long-pressed - open its editor. */
    fun onMacroLongPressed(slot: Int) {}

    /** The macro panel's page-forward arrow was tapped. */
    fun onNextMacroPage() {}

    // ---- Word-completion customization -------------------------------
    // Default (empty) bodies so other implementers don't need to care -
    // only the service acts on these. Fired by KeyboardPanelView's
    // long-press-and-drag (reorder) and long-press-and-release (edit a
    // slot, including an empty one) gestures on the word-completion row.

    /**
     * The completion row for [prefix] was reordered (drag) or had a slot
     * assigned/changed (long-press edit). [newLogicalOrder] is the FULL
     * resulting row - not just whichever slot changed - in left-to-right
     * logical order (already un-reversed if the display is RTL), with ""
     * for any slot the user left deliberately blank. Meant to be persisted
     * (see CustomCompletionsStore) and used in place of the engine's own
     * computed completions whenever this exact prefix comes up again.
     */
    fun onCompletionsChanged(prefix: String, newLogicalOrder: List<String>) {}

    /** "Reset row" was chosen from the slot-edit dialog - drop any saved override for [prefix]. */
    fun onCompletionOverrideCleared(prefix: String) {}
}


