package com.predictivekb.ime

/**
 * Words that should always start with a capital letter regardless of
 * sentence position - days of the week and months, per the user's request.
 * Deliberately a small, explicit list ("just a text list change") rather
 * than general proper-noun/named-entity detection.
 */
object WordCasing {

    private val ALWAYS_CAPITALIZE = setOf(
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "january", "february", "march", "april", "may", "june", "july",
        "august", "september", "october", "november", "december"
    )

    fun isAlwaysCapitalized(word: String): Boolean = ALWAYS_CAPITALIZE.contains(word.lowercase())

    /**
     * Applies the correct casing to a whole word given the current shift
     * state, also honoring the always-capitalized list regardless of shift
     * state (so "monday" -> "Monday" even when shift is off).
     */
    fun apply(word: String, shiftState: ShiftState): String {
        return when {
            shiftState == ShiftState.CAPS_LOCK -> word.uppercase()
            shiftState == ShiftState.SHIFT_ONCE || isAlwaysCapitalized(word) ->
                word.replaceFirstChar { it.uppercaseChar() }
            else -> word
        }
    }
}
