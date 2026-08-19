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
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        val allWords = ArrayList<String>(lines.size)
        val rootWords = ArrayList<String>(lines.size)
        val families = LinkedHashMap<String, List<String>>()
        val seen = HashSet<String>()

        for (line in lines) {
            val parts = line.split(",").map { it.trim() }
            if (parts.isEmpty()) continue

            val root = parts[0].lowercase()
            if (!isValidWord(root) || !seen.add(root)) continue

            allWords.add(root)
            rootWords.add(root)

            if (parts.size > 1) {
                val members = ArrayList<String>(parts.size)
                members.add(root)
                for (part in parts.subList(1, parts.size)) {
                    // Format is "[suffix]word" - only what's after ']' matters.
                    val closeBracket = part.indexOf(']')
                    val variant = (if (closeBracket >= 0) part.substring(closeBracket + 1) else part)
                        .trim().lowercase()
                    if (isValidWord(variant) && seen.add(variant)) {
                        allWords.add(variant)
                        members.add(variant)
                    }
                }
                if (members.size > 1) {
                    families[root] = members
                }
            }
        }

        words = allWords
        roots = rootWords
        familyOf = families
        loaded = true
    }

    private fun isValidWord(word: String): Boolean =
        word.isNotEmpty() && word.all { it.isLetter() || it == '\'' }

    fun isLoaded(): Boolean = loaded

    /**
     * Returns up to [max] whole ROOT words, ranked most- to least-frequent,
     * that start with [prefix] and are STRICTLY LONGER than it. Inflected
     * forms are excluded here - see [familyMembers] for those.
     */
    fun topCompletions(prefix: String, max: Int = 6): List<String> {
        val lower = prefix.lowercase()
        val result = ArrayList<String>(max)
        for (w in roots) {
            if (w.length > lower.length && w.startsWith(lower)) {
                result.add(w)
                if (result.size >= max) break
            }
        }
        return result
    }

    /**
     * All dictionary words belonging to [root]'s family (the root itself
     * plus its listed variants), in the file's order. If [root] has no
     * family, just returns the root itself.
     */
    fun familyMembers(root: String, max: Int = 6): List<String> {
        val lower = root.lowercase()
        val members = familyOf[lower] ?: listOf(lower)
        return members.take(max)
    }

    /**
     * If exactly one dictionary word matches [prefix] - either equal to it
     * or extending it - returns that word. Otherwise null. Searches the
     * WHOLE dictionary (not just roots) - an inflected word can absolutely
     * be the sole completion.
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
