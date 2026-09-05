package com.predictivekb.ime

/**
 * Words that should always start with a capital letter regardless of
 * sentence position - days of the week, months, and holidays.
 * Deliberately a small, explicit list ("just a text list change") rather
 * than general proper-noun/named-entity detection.
 *
 * "May" is deliberately NOT in this list, even though it's a month - it's
 * also an extremely common modal verb ("it may rain"), and forcing
 * capitalization there was wrong far more often than it was right. When
 * you specifically mean the month, capitalize it manually with shift, the
 * same way you would for any other proper noun not on this list.
 *
 * Holidays are limited to single words with little risk of a common,
 * legitimately-lowercase everyday meaning. Deliberately left off:
 * "Valentine" (commonly used lowercase - "will you be my valentine") and
 * "Independence" (extremely common in unrelated, non-holiday contexts -
 * "financial independence"). Multi-word holidays ("New Year's Day", "St.
 * Patrick's Day") aren't supported at all yet - this list only ever checks
 * a single word at a time.
 */
object WordCasing {

    private val ALWAYS_CAPITALIZE = setOf(
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "january", "february", "march", "april", "june", "july",
        "august", "september", "october", "november", "december",
        "christmas", "thanksgiving", "halloween", "easter", "hanukkah", "kwanzaa"
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
