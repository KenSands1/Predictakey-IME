package com.predictivekb.ime

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Persists user-customized completion rows: per exact typed prefix, an
 * ordered list of up to 6 words that overrides whatever the engine would
 * otherwise compute for that prefix. Created by either dragging a
 * completion into a new position, or long-pressing a slot to assign/
 * replace the word shown there (including a previously-empty slot).
 *
 * The override is keyed to the literal prefix string, same as the rest of
 * the completion system - typing one more letter moves to a different
 * prefix with its own (possibly unset) override.
 */
class CustomCompletionsStore(context: Context) :
    SQLiteOpenHelper(context, "custom_completions.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE custom_completions (
                prefix TEXT NOT NULL,
                slot_index INTEGER NOT NULL,
                word TEXT NOT NULL,
                PRIMARY KEY (prefix, slot_index)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No prior versions yet.
    }

    /**
     * The saved override row for [prefix], in slot order, or null if this
     * prefix has never been customized (so the caller should fall back to
     * the engine's own computed completions).
     */
    fun getOverride(prefix: String): List<String>? {
        val lower = prefix.lowercase()
        val result = sortedMapOf<Int, String>()
        readableDatabase.query(
            "custom_completions", arrayOf("slot_index", "word"),
            "prefix = ?", arrayOf(lower), null, null, "slot_index ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.getInt(0)] = cursor.getString(1)
            }
        }
        return if (result.isEmpty()) null else result.values.toList()
    }

    /**
     * Replaces the entire override row for [prefix] with [words], in order.
     * Called after a drag-reorder or a slot edit, passing the full,
     * resulting row (not just the one slot that changed) so slot indices
     * stay consistent. An empty list clears the override, reverting that
     * prefix back to automatic completions.
     */
    fun setOverride(prefix: String, words: List<String>) {
        val lower = prefix.lowercase()
        writableDatabase.run {
            beginTransaction()
            try {
                delete("custom_completions", "prefix = ?", arrayOf(lower))
                words.forEachIndexed { index, word ->
                    val values = ContentValues().apply {
                        put("prefix", lower)
                        put("slot_index", index)
                        put("word", word)
                    }
                    insert("custom_completions", null, values)
                }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
    }

    /** Clears any override for [prefix], reverting it to automatic completions. */
    fun clearOverride(prefix: String) {
        writableDatabase.delete("custom_completions", "prefix = ?", arrayOf(prefix.lowercase()))
    }
}
