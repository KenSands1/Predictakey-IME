package com.predictivekb.ime

interface KeyboardActionListener {
    /** A regular letter/character key was tapped. */
    fun onCharKey(ch: Char)

    /** One of the 6 dynamic prediction keys was tapped — behaves like [onCharKey]. */
    fun onPredictionKey(ch: Char)

    fun onBackspace()
    fun onSpace()
    fun onEnter()
    fun onShiftToggled()

    /** Switch from the letters panel to the numbers/symbols panel, or back. */
    fun onSwitchToSymbols()
    fun onSwitchToLetters()
}
