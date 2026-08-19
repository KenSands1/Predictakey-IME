package com.predictivekb.ime

import java.io.BufferedReader
import java.io.InputStream

/**
 * Frequency-ranked word predictor, loaded from a single combined word list.
 *
 * File format: one line per ROOT word, most-to-least frequent top to
 * bottom. A root with inflected forms lists them on the same line as
 * "[suffix]word" entries, e.g.:
 *
 *   happen, [s]happens, [ing]happening, [ed]happened
 *
 * The bracketed suffix is purely a human-readable label for whoever's
 * editing the file - only the text after ']' is used by the app. A plain
 * line with no commas is just a standalone root with no variants.
 *
 * This class answers a few questions:
 *   1. What are the top N most frequent ROOT words starting with [prefix]?
 *      -> [topCompletions] — shown as tappable keys at the top level.
 *   2. Once a specific root has been picked, what are its own family
 *      members? -> [familyMembers] — the second-tier row shown after
 *      tapping a root.
 *   3. Does exactly one dictionary word (root or variant) match [prefix]?
 *      -> [soleCompletion] — what the green space bar completes.
 *
 * Dictionary sizes here are small (thousands of words), so plain linear
 * scans per keystroke are simple and fast enough - no trie needed. This
 * class has no Android dependencies so it can be unit tested on its own.
 */
class PredictionEngine {

    /** Every word (roots and variants together), in the file's frequency order. */
    private var words: List<String> = emptyList()

    /** Just the root words, in frequency order - what topCompletions draws from. */
    private var roots: List<String> = emptyList()

    /** root -> [root, variant1, variant2, ...], for words that have a family. */
    private var familyOf: Map<String, List<String>> = emptyMap()

    private var loaded = false

    fun load(stream: InputStream) {
        val lines = stream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readLines)
