package com.predictivekb.ime

enum class MacroType {
    TEXT, QR, IMAGE
}

/**
 * One configured macro button. [page] and [slot] identify its position in
 * the grid (slot is 0..23 for a 4x6 grid, row-major). [label] is what's
 * shown on the button - user-editable, independent of [content].
 *
 * - TEXT: [content] is the literal text inserted when tapped.
 * - QR:   [content] is the text/URL encoded into a QR code image when tapped.
 * - IMAGE: [imagePath] points to a stored image file inserted when tapped.
 */
data class Macro(
    val page: Int,
    val slot: Int,
    val label: String,
    val type: MacroType,
    val content: String?,
    val imagePath: String?
)
