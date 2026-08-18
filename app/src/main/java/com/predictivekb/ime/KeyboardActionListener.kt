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
}
