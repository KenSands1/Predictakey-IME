package com.predictivekb.ime

import java.io.BufferedReader
import java.io.InputStream

/**
 * Trie-based prefix predictor.
 *
 * Loaded once from a plain word list (one word per line). For any typed
 * prefix it can answer two questions cheaply:
 *
 *   1. What are the 6 most likely NEXT letters? -> [topNextLetters]
 *   2. Does exactly one word in the dictionary still match this prefix,
 *      and if so, what is it? -> [soleCompletion]
 *
 * Ranking is deliberately simple: for a given prefix, each candidate next
 * letter is ranked purely by how many dictionary words share that
 * prefix+letter combination. It does NOT weight by how common the word
 * itself is overall — the prefix the user has already typed is the only
 * input to the ranking, which is exactly what "next letter frequency given
 * what's typed so far" means. This also means the word list no longer
 * needs to be frequency-ordered; a plain, unordered word list works fine.
 *
 * This class has no Android dependencies so it can be unit tested on its
 * own (e.g. with a plain JVM test using kotlin.test / JUnit).
 */
class PredictionEngine {

    private class Node {
        val children = HashMap<Char, Node>()
        var isWord: Boolean = false
        /** Number of distinct dictionary words that pass through this node. */
        var subtreeCount: Int = 0
    }

    private val root = Node()
    private var loaded = false

    /**
     * Loads the dictionary from an [InputStream] of a UTF-8 text file, one
     * word per line. Blank lines and lines starting with '#' are ignored.
     */
    fun load(stream: InputStream) {
        val lines = stream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readLines)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        val seen = HashSet<String>()
        for (line in lines) {
            val word = line.lowercase()
            if (word.isNotEmpty() && word.all { it.isLetter() } && seen.add(word)) {
                insert(word)
            }
        }
        loaded = true
    }

    fun isLoaded(): Boolean = loaded

    private fun insert(word: String) {
        var node = root
        root.subtreeCount += 1
        for (ch in word) {
            node = node.children.getOrPut(ch) { Node() }
            node.subtreeCount += 1
        }
        node.isWord = true
    }

    private fun nodeFor(prefix: String): Node? {
        var node = root
        for (ch in prefix.lowercase()) {
            node = node.children[ch] ?: return null
        }
        return node
    }

    /**
     * Returns up to [max] letters, ranked most- to least-likely, that could
     * follow [prefix] according to the loaded dictionary. The ranking is a
     * simple count of how many words share each prefix+letter continuation
     * — no overall word-frequency weighting involved. Empty prefix returns
     * the most common starting letters. Returns an empty list if the prefix
     * doesn't match anything in the dictionary.
     */
    fun topNextLetters(prefix: String, max: Int = 6): List<Char> {
        val node = nodeFor(prefix) ?: return emptyList()
        return node.children.entries
            .sortedWith(compareByDescending { it.value.subtreeCount })
            .take(max)
            .map { it.key }
    }

    /**
     * If exactly one word in the dictionary matches [prefix] (as a prefix
     * of that word, or the word itself), returns that word. Otherwise null.
     */
    fun soleCompletion(prefix: String): String? {
        val lower = prefix.lowercase()
        val node = nodeFor(lower) ?: return null
        if (node.subtreeCount != 1) return null
        val sb = StringBuilder(lower)
        var cur = node
        while (!(cur.isWord && cur.children.isEmpty())) {
            val next = cur.children.entries.firstOrNull { it.value.subtreeCount > 0 } ?: break
            sb.append(next.key)
            cur = next.value
        }
        return sb.toString()
    }

    /** True if any word in the dictionary starts with [prefix]. */
    fun hasPrefix(prefix: String): Boolean = nodeFor(prefix) != null
}
