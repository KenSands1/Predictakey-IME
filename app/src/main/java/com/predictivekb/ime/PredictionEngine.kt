package com.predictivekb.ime

import java.io.BufferedReader
import java.io.InputStream

/**
 * Frequency-ranked word predictor.
 *
 * Loaded once from a word list ordered most-to-least frequent (line 1 =
 * most common word). Every key on the keyboard's top row is a full-word
 * completion shortcut, so this class answers two questions:
 *
 *   1. What are the top N most frequent words starting with [prefix]? ->
 *      [topCompletions] — these are what get shown as tappable keys.
 *   2. Does exactly one dictionary word still match [prefix]? -> if so,
 *      that's the word the space bar completes when it turns green ->
 *      [soleCompletion].
 *
 * The word list's order IS the ranking signal here — that's intentional.
 * Unlike the earlier next-letter design (which deliberately ignored overall
 * word frequency), ranking whole-word suggestions by real-world frequency
 * is the only sensible way to pick e.g. "the" over an obscure word sharing
 * the same prefix.
 *
 * Dictionary sizes here are small (thousands, not millions of words), so a
 * plain linear scan per keystroke is simple and fast enough — no trie
 * needed. This class has no Android dependencies so it can be unit tested
 * on its own.
 */
class PredictionEngine {

    /** Frequency-ordered, most common first. */
    private var words: List<String> = emptyList()
    private var loaded = false

    /**
     * Loads the dictionary from an [InputStream] of a UTF-8 text file, one
     * word per line, ordered most-to-least frequent. Blank lines and lines
     * starting with '#' are ignored.
     */
    fun load(stream: InputStream) {
        val lines = stream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readLines)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        val seen = HashSet<String>()
        val result = ArrayList<String>(lines.size)
        for (line in lines) {
            val word = line.lowercase()
            if (word.isNotEmpty() && word.all { it.isLetter() } && seen.add(word)) {
                result.add(word)
            }
        }
        words = result
        loaded = true
    }

    fun isLoaded(): Boolean = loaded

    /**
     * Returns up to [max] whole words, ranked most- to least-frequent, that
     * start with [prefix] and are STRICTLY LONGER than it — so once the
     * user has typed a full word ("the"), that word itself drops out and
     * only genuine continuations ("them", "then", "there"...) remain. An
     * empty prefix returns the most frequent words overall, useful as
     * sentence-starter shortcuts before anything's been typed.
     */
    fun topCompletions(prefix: String, max: Int = 6): List<String> {
        val lower = prefix.lowercase()
        val result = ArrayList<String>(max)
        for (w in words) {
            if (w.length > lower.length && w.startsWith(lower)) {
                result.add(w)
                if (result.size >= max) break
            }
        }
        return result
    }

    /**
     * If exactly one dictionary word matches [prefix] — either equal to it
     * or extending it — returns that word. Otherwise null. Unlike
     * [topCompletions], this intentionally DOES count an exact match (the
     * prefix being a complete word with no other word sharing it).
     */
    fun soleCompletion(prefix: String): String? {
        val lower = prefix.lowercase()
        var found: String? = null
        var count = 0
        for (w in words) {
            if (w == lower || (w.length > lower.length && w.startsWith(lower))) {
                found = w
                count++
                if (count > 1) return null
            }
        }
        return if (count == 1) found else null
    }

    /** True if any word in the dictionary starts with [prefix]. */
    fun hasPrefix(prefix: String): Boolean {
        val lower = prefix.lowercase()
        return words.any { it.startsWith(lower) }
    }
}
