package com.predictivekb.ime

/**
 * Approximate horizontal position of each QWERTY letter key, used to order
 * word-completion buttons left-to-right by where the "next letter" sits on
 * the physical keyboard - so muscle memory for reaching that key can help
 * guide which button to tap, rather than an unrelated ordering like length.
 *
 * Values follow the standard physical keyboard stagger: row 2 (asdf...) is
 * offset about a quarter-key right of row 1 (qwerty...), row 3 (zxcv...)
 * about three-quarters - matching how real keyboards (and this app's own
 * letter rows) are laid out.
 */
object QwertyLayout {
    private val COLUMN: Map<Char, Float> = buildMap {
        "qwertyuiop".forEachIndexed { i, c -> put(c, i.toFloat()) }
        "asdfghjkl".forEachIndexed { i, c -> put(c, i + 0.25f) }
        "zxcvbnm".forEachIndexed { i, c -> put(c, i + 0.75f) }
    }

    /** Left-to-right position of [ch] on the keyboard. Unknown characters sort last. */
    fun columnOf(ch: Char): Float = COLUMN[ch.lowercaseChar()] ?: Float.MAX_VALUE
}
