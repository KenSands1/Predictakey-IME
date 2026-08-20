package com.predictivekb.ime

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Simple SQLite-backed store for macros. Each macro is uniquely addressed
 * by (page, slot) - a grid position, not an arbitrary ID the UI has to
 * track. Reading a whole page at once (see [macrosForPage]) is the common
 * case, since that's what happens every time the macro panel is shown or
 * paged.
 */
class MacroStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "macros.db"
        private const val DB_VERSION = 1
        private const val TABLE = "macros"

        /** Grid is fixed at 4 rows x 6 columns = 24 slots per page. */
        const val SLOTS_PER_PAGE = 24

        /** Always at least this many pages available, even if all empty. */
        const val MIN_PAGE_COUNT = 3
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                page INTEGER NOT NULL,
                slot INTEGER NOT NULL,
                label TEXT NOT NULL,
                type TEXT NOT NULL,
                content TEXT,
                image_path TEXT,
                PRIMARY KEY (page, slot)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No prior versions yet - nothing to migrate.
    }

    /** All configured macros on [page], keyed by slot. Empty slots simply have no entry. */
    fun macrosForPage(page: Int): Map<Int, Macro> {
        val result = HashMap<Int, Macro>()
        readableDatabase.rawQuery(
            "SELECT slot, label, type, content, image_path FROM $TABLE WHERE page = ?",
            arrayOf(page.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val slot = cursor.getInt(0)
                result[slot] = Macro(
                    page = page,
                    slot = slot,
                    label = cursor.getString(1),
                    type = MacroType.valueOf(cursor.getString(2)),
                    content = cursor.getString(3),
                    imagePath = cursor.getString(4)
                )
            }
        }
        return result
    }

    fun getMacro(page: Int, slot: Int): Macro? {
        readableDatabase.rawQuery(
            "SELECT label, type, content, image_path FROM $TABLE WHERE page = ? AND slot = ?",
            arrayOf(page.toString(), slot.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return Macro(
                    page = page,
                    slot = slot,
                    label = cursor.getString(0),
                    type = MacroType.valueOf(cursor.getString(1)),
                    content = cursor.getString(2),
                    imagePath = cursor.getString(3)
                )
            }
        }
        return null
    }

    fun saveMacro(macro: Macro) {
        val values = ContentValues().apply {
            put("page", macro.page)
            put("slot", macro.slot)
            put("label", macro.label)
            put("type", macro.type.name)
            put("content", macro.content)
            put("image_path", macro.imagePath)
        }
        writableDatabase.insertWithOnConflict(
            TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun deleteMacro(page: Int, slot: Int) {
        writableDatabase.delete(TABLE, "page = ? AND slot = ?", arrayOf(page.toString(), slot.toString()))
    }

    /** Highest page number with any macro on it, or -1 if nothing's configured yet. */
    fun highestUsedPage(): Int {
        readableDatabase.rawQuery("SELECT MAX(page) FROM $TABLE", null).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getInt(0)
            }
        }
        return -1
    }

    /** Total pages to offer: always at least MIN_PAGE_COUNT, one more than the highest used page if that's more. */
    fun pageCount(): Int = maxOf(MIN_PAGE_COUNT, highestUsedPage() + 1)
}
