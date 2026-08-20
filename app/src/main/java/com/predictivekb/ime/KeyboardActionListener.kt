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
}

