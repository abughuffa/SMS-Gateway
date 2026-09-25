package com.example.smsgateway.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.smsgateway.model.InboxMessage

class InboxDb(ctx: Context) : SQLiteOpenHelper(ctx, "gateway.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE inbox (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender TEXT NOT NULL,
                body TEXT NOT NULL,
                ts INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_ts ON inbox(ts)")
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
        db.execSQL("DROP TABLE IF EXISTS inbox")
        db.execSQL("DROP TABLE IF EXISTS meta")
        onCreate(db)
    }

    fun insert(sender: String, body: String, ts: Long): Long {
        val v = ContentValues().apply {
            put("sender", sender); put("body", body); put("ts", ts)
        }
        return writableDatabase.insert("inbox", null, v)
    }

    fun since(lastId: Long, limit: Int = 200): List<InboxMessage> {
        val out = mutableListOf<InboxMessage>()
        readableDatabase.rawQuery(
            "SELECT id, sender, body, ts FROM inbox WHERE id > ? ORDER BY id ASC LIMIT ?",
            arrayOf(lastId.toString(), limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += InboxMessage(
                    c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)
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