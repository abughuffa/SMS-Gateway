

package com.example.smsgateway.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.smsgateway.model.InboxMessage

class InboxDb private constructor(ctx: Context) :
    SQLiteOpenHelper(ctx.applicationContext, "gateway.db", null, DB_VERSION) {

    companion object {
        private const val DB_VERSION = 2          // was 1

        @Volatile private var INSTANCE: InboxDb? = null

        fun get(ctx: Context): InboxDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: InboxDb(ctx.applicationContext).also { INSTANCE = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE inbox (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                sender      TEXT NOT NULL,
                body        TEXT NOT NULL,
                ts          INTEGER NOT NULL,
                sim_slot    INTEGER,
                concat_ref  INTEGER,
                part_number INTEGER,
                total_parts INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_ts ON inbox(ts)")

        // Helps the C# side assemble faster when it isn't polled.
        db.execSQL(
            "CREATE INDEX idx_concat ON inbox(sender, concat_ref, part_number)"
        )

        db.execSQL(
            """
            CREATE TABLE meta (
                k TEXT PRIMARY KEY,
                v TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
        // Non-destructive: only the schema migrates. Existing rows keep
        // their data; the four new columns become NULL, which the C# side
        // treats as "single-part, fast path".
        if (oldV < 2) {
            db.execSQL("ALTER TABLE inbox ADD COLUMN sim_slot    INTEGER")
            db.execSQL("ALTER TABLE inbox ADD COLUMN concat_ref  INTEGER")
            db.execSQL("ALTER TABLE inbox ADD COLUMN part_number INTEGER")
            db.execSQL("ALTER TABLE inbox ADD COLUMN total_parts INTEGER")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_concat " +
                        "ON inbox(sender, concat_ref, part_number)"
            )
        }
        // If you ever truly need a destructive reset, gate it behind an
        // explicit user action — not a version bump.
    }

    fun insert(
        from: String,
        body: String,
        ts: Long,
        simSlot:    Int? = null,
        concatRef:  Int? = null,
        partNumber: Int? = null,
        totalParts: Int? = null,
    ): Long {
        val v = ContentValues().apply {
            put("sender", from)
            put("body",   body)
            put("ts",     ts)
            // ContentValues.put(String, Int?) accepts null and writes SQL NULL.
            put("sim_slot",    simSlot)
            put("concat_ref",  concatRef)
            put("part_number", partNumber)
            put("total_parts", totalParts)
        }
        return writableDatabase.insert("inbox", null, v)
    }

    fun since(lastId: Long, limit: Int = 200): List<InboxMessage> {
        val out = mutableListOf<InboxMessage>()
        readableDatabase.rawQuery(
            """
            SELECT id, sender, body, ts,
                   sim_slot, concat_ref, part_number, total_parts
            FROM inbox
            WHERE id > ?
            ORDER BY id ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf(lastId.toString(), limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += InboxMessage(
                    id         = c.getLong(0),
                    from       = c.getString(1),
                    body       = c.getString(2),
                    ts         = c.getLong(3),
                    simSlot    = if (c.isNull(4)) null else c.getInt(4),
                    concatRef  = if (c.isNull(5)) null else c.getInt(5),
                    partNumber = if (c.isNull(6)) null else c.getInt(6),
                    totalParts = if (c.isNull(7)) null else c.getInt(7),
                )
            }
        }
        return out
    }

    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM inbox", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun getMeta(key: String): String? {
        readableDatabase.rawQuery(
            "SELECT v FROM meta WHERE k = ?", arrayOf(key)
        ).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    fun setMeta(key: String, value: String?) {
        if (value == null) {
            writableDatabase.delete("meta", "k = ?", arrayOf(key))
        } else {
            val v = ContentValues().apply { put("k", key); put("v", value) }
            writableDatabase.insertWithOnConflict(
                "meta", null, v, SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    @Synchronized
    fun incr(key: String, by: Long = 1): Long {
        val cur = getMeta(key)?.toLongOrNull() ?: 0L
        val nv = cur + by
        setMeta(key, nv.toString())
        return nv
    }
}