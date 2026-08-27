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
 * This class answers a couple of questions:
 *   1. What are the top N most frequent ROOT words starting with [prefix]?
 *      -> [topCompletions] — shown as tappable keys at the top level.
 *   2. Once a specific root has been picked, what are its own family
 *      members? -> [familyMembers] — the second-tier row shown after
 *      tapping a root.
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

        // LinkedHashSet: preserves frequency order, and naturally de-dupes
        // the flat word list. Family membership below is a separate
        // structure, so a word can still be listed in a family even if it
        // already has its own root line elsewhere.
        val allWords = LinkedHashSet<String>()
        val rootWords = ArrayList<String>(lines.size)
        val families = LinkedHashMap<String, List<String>>()
        val seenRoots = HashSet<String>()

        for (line in lines) {
            val parts = line.split(",").map { it.trim() }
            if (parts.isEmpty()) continue

            val root = parts[0].lowercase()
            if (!isValidWord(root) || !seenRoots.add(root)) continue

            allWords.add(root)
            rootWords.add(root)

            if (parts.size > 1) {
                val members = ArrayList<String>(parts.size)
                members.add(root)
                for (part in parts.subList(1, parts.size)) {
                    // Format is "[suffix]word" - only what's after ']' matters.
                    val closeBracket = part.indexOf("]")
                    val variant = (if (closeBracket >= 0) part.substring(closeBracket + 1) else part)
                        .trim().lowercase()
                    if (isValidVariant(variant)) {
                        allWords.add(variant) // no-op if already present
                        members.add(variant)
                    }
                }
                if (members.size > 1) {
                    families[root] = members
                }
            }
        }

        words = allWords.toList()
        roots = rootWords
        familyOf = families
        loaded = true
    }

    private fun isValidWord(word: String): Boolean =
        word.isNotEmpty() && word.all { it.isLetter() || it.code == 39 } // 39 = apostrophe

    /**
     * Same as [isValidWord] but also permits a phrase completion: exactly
     * two words separated by a single space (e.g. "for the"). Roots can
     * never be phrases - only family members - since a phrase isn't
     * something you type toward the same way a single word is.
     */
    private fun isValidVariant(variant: String): Boolean {
        if (isValidWord(variant)) return true
        val spaceIdx = variant.indexOf(' ')
        if (spaceIdx <= 0 || variant.indexOf(' ', spaceIdx + 1) >= 0) return false // 0 or 2+ spaces
        val first = variant.substring(0, spaceIdx)
        val second = variant.substring(spaceIdx + 1)
        return isValidWord(first) && isValidWord(second)
    }

    fun isLoaded(): Boolean = loaded

    companion object {
        /** Words at least this long require [MIN_PREFIX_FOR_LONG_WORD]
         * characters typed before they're offered - otherwise a handful of
         * long, less-common words can dominate the row before you've typed
         * enough to have actually narrowed things down much. Doesn't apply
         * to the reserved exact-match slot, since by definition you've
         * already typed the whole word by then. */
        private const val LONG_WORD_LENGTH = 8
        private const val MIN_PREFIX_FOR_LONG_WORD = 4
    }

    /**
     * Returns up to [max] whole ROOT words, ranked most- to least-frequent,
     * that start with [prefix]. Normally a candidate must be STRICTLY
     * LONGER than [prefix] - otherwise tapping it would save no letters.
     * Long words (see [LONG_WORD_LENGTH]) additionally require at least
     * [MIN_PREFIX_FOR_LONG_WORD] characters typed before they're offered.
     *
     * The one exception: if the prefix EXACTLY matches a root that has a
     * family, that root always gets a RESERVED slot - not just permission
     * to compete for one. Short words with a family (e.g. "ha", "pro") are
     * often also a literal prefix of several longer, more frequent words
     * ("half", "happy", "hand"...), so merely making them eligible still
     * let them get crowded out of all 6 slots. Reserving a slot guarantees
     * that typing a word out fully never cuts off access to its family,
     * regardless of how much competition exists at that exact prefix.
     * Inflected forms themselves are excluded here either way - see
     * [familyMembers] for those.
     */
    fun topCompletions(prefix: String, max: Int = 6): List<String> {
        val lower = prefix.lowercase()
        var exactMatchWithFamily: String? = null
        val longerMatches = ArrayList<String>()
        for (w in roots) {
            if (!w.startsWith(lower)) continue
            if (w.length == lower.length) {
                if (exactMatchWithFamily == null && hasFamilyVariants(w)) {
                    exactMatchWithFamily = w
                }
            } else if (w.length > lower.length) {
                if (w.length >= LONG_WORD_LENGTH && lower.length < MIN_PREFIX_FOR_LONG_WORD) {
                    continue
                }
                longerMatches.add(w)
            }
        }
        val result = ArrayList<String>(max)
        exactMatchWithFamily?.let { result.add(it) }
        for (w in longerMatches) {
            if (result.size >= max) break
            result.add(w)
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

    /** True if [root] has at least one listed inflected form. */
    fun hasFamilyVariants(root: String): Boolean = familyOf.containsKey(root.lowercase())

    /** True if any word in the dictionary starts with [prefix]. */
    fun hasPrefix(prefix: String): Boolean {
        val lower = prefix.lowercase()
        return words.any { it.startsWith(lower) }
    }
}
