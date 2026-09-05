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

    /** Same words as [words], but as a Set for fast exact-match lookups. */
    private var wordSet: Set<String> = emptySet()

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
        wordSet = allWords
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
        /**
         * The "primary" candidate-length range at each prefix length - the
         * range most likely to actually help, given how many characters
         * are left to save. A candidate outside this range for the current
         * prefix length can still appear (see [topCompletions]'s soft
         * gate), but only after every primary candidate has had a chance,
         * and always ranked below them regardless of frequency.
         *
         * At 5+ characters typed, there's no restriction at all - by then
         * prefix matching alone has almost always narrowed things down to
         * a handful of candidates, so there's nothing left to protect.
         */
        private fun isPrimaryLength(prefixLength: Int, wordLength: Int): Boolean = when (prefixLength) {
            1 -> wordLength <= 4
            2 -> wordLength <= 5
            3 -> wordLength in 5..6      // 4-letter words excluded from primary here
            4 -> wordLength >= 6         // 5-letter words excluded from primary here
            else -> true
        }
    }

    /**
     * Returns up to [max] whole ROOT words, ranked most- to least-frequent,
     * that start with [prefix]. A candidate must be STRICTLY LONGER than
     * [prefix] - otherwise tapping it would save no letters.
     *
     * Candidates in the current prefix length's "primary" range (see
     * [isPrimaryLength]) are always shown first. If they don't fill all
     * [max] slots, the row widens to include candidates outside that
     * range - but only to fill what's left over, and always ranked below
     * every primary candidate, regardless of frequency. Both groups are
     * separately frequency-ordered within themselves.
     *
     * The one exception: if the prefix EXACTLY matches a root that has a
     * family, that root always gets a RESERVED slot - not just permission
     * to compete for one. Short words with a family (e.g. "ha", "pro") are
     * often also a literal prefix of several longer, more frequent words
     * ("half", "happy", "hand"...), so merely making them eligible still
     * let them get crowded out of all 6 slots. Reserving a slot guarantees
     * that typing a word out fully never cuts off access to its family,
     * regardless of how much competition exists at that exact prefix. A
     * family-less exact match never gets this reserved slot either way -
     * the space-key indicator already covers finishing it, so a button
     * would be redundant.
     * Inflected forms themselves are excluded here either way - see
     * [familyMembers] for those.
     */
    fun topCompletions(prefix: String, max: Int = 6): List<String> {
        val lower = prefix.lowercase()
        var exactMatchWithFamily: String? = null
        val primary = ArrayList<String>()
        val fallback = ArrayList<String>()
        for (w in roots) {
            if (!w.startsWith(lower)) continue
            if (w.length == lower.length) {
                if (exactMatchWithFamily == null && hasFamilyVariants(w)) {
                    exactMatchWithFamily = w
                }
            } else if (w.length > lower.length) {
                if (isPrimaryLength(lower.length, w.length)) {
                    primary.add(w)
                } else {
                    fallback.add(w)
                }
            }
        }
        val result = ArrayList<String>(max)
        exactMatchWithFamily?.let { result.add(it) }
        for (w in primary) {
            if (result.size >= max) break
            result.add(w)
        }
        for (w in fallback) {
            if (result.size >= max) break
            result.add(w)
        }
        return result
    }

    /**
     * All of [root]'s OTHER family members (not including the root itself -
     * you already picked it by tapping it), up to [max], in the file's
     * order. Empty if [root] has no family.
     *
     * Excludes the root BEFORE truncating to [max] - not after. Truncating
     * first and filtering the root out afterward (as an earlier version of
     * this did) silently drops whichever member landed in the max-th slot
     * whenever a family has exactly [max] total members including the
     * root, since the root always gets filtered out downstream regardless
     * of where it lands in that list.
     */
    fun familyMembers(root: String, max: Int = 6): List<String> {
        val lower = root.lowercase()
        val members = familyOf[lower] ?: return emptyList()
        return members.asSequence().filterNot { it == lower }.take(max).toList()
    }

    /** True if [root] has at least one listed inflected form. */
    fun hasFamilyVariants(root: String): Boolean = familyOf.containsKey(root.lowercase())

    /**
     * True if [text] is, itself, a complete word already (not just a
     * prefix of a longer one) - either a root or an inflected form.
     * Powers the space-key indicator: pressing space always just adds a
     * space after whatever's already typed, so this changes nothing about
     * what gets committed - it's purely a "yes, that's a real word"
     * confirmation shown before you do.
     */
    fun isExactWord(text: String): Boolean = wordSet.contains(text.lowercase())

    /** True if any word in the dictionary starts with [prefix]. */
    fun hasPrefix(prefix: String): Boolean {
        val lower = prefix.lowercase()
        return words.any { it.startsWith(lower) }
    }
}

