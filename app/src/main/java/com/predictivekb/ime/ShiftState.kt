package com.predictivekb.ime

/**
 * OFF        - lowercase typing.
 * SHIFT_ONCE - next letter typed (or next word completed) is capitalized,
 *              then automatically reverts to OFF.
 * CAPS_LOCK  - everything typed is capitalized until toggled off.
 */
enum class ShiftState {
    OFF, SHIFT_ONCE, CAPS_LOCK
}
