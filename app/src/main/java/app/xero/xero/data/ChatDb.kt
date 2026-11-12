package app.xero.xero.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.xero.xero.domain.ChatMessage
import app.xero.xero.domain.ChatSession
import app.xero.xero.domain.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Local persistence: sessions + messages, plain SQLite (no codegen).
 * Schema mirrors the PLAN.md drift tables. */
class ChatDb(context: Context) :
    SQLiteOpenHelper(context, "xero.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE sessions(
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                engine_mode TEXT NOT NULL,
                model_id TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE messages(
                id TEXT PRIMARY KEY NOT NULL,
                session_id TEXT NOT NULL REFERENCES sessions(id),
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                error TEXT,
                token_count INTEGER NOT NULL DEFAULT 0,
                latency_ms INTEGER,
                created_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX idx_messages_session ON messages(session_id)")
        db.execSQL(
            """CREATE TABLE downloaded_models(
                id TEXT PRIMARY KEY NOT NULL,
                filename TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                context_length INTEGER,
                chat_template TEXT,
                added_at INTEGER NOT NULL)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 — nothing to migrate yet.
    }
}

class ChatRepository(private val db: ChatDb) {

    suspend fun sessions(): List<ChatSession> = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            "SELECT id, title, engine_mode, model_id, created_at, updated_at " +
                "FROM sessions ORDER BY updated_at DESC",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        ChatSession(
                            id = c.getString(0),
                            title = c.getString(1),
                            engineMode = c.getString(2),
                            modelId = c.getStringOrNull(3),
                            createdAt = c.getLong(4),
                            updatedAt = c.getLong(5),
                        )
                    )
                }
            }
        }
    }

    suspend fun messages(sessionId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            "SELECT id, session_id, role, content, error, latency_ms, created_at " +
                "FROM messages WHERE session_id = ? ORDER BY created_at ASC",
            arrayOf(sessionId),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        ChatMessage(
                            id = c.getString(0),
                            sessionId = c.getString(1),
                            role = MessageRole.valueOf(c.getString(2)),
                            content = c.getString(3),
                            error = c.getStringOrNull(4),
                            latencyMs = if (c.isNull(5)) null else c.getLong(5),
                            createdAt = c.getLong(6),
                        )
                    )
                }
            }
        }
    }

    suspend fun upsertSession(session: ChatSession) = withContext(Dispatchers.IO) {
        db.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO sessions(id, title, engine_mode, model_id, created_at, updated_at) " +
                "VALUES(?,?,?,?,?,?)",
            arrayOf<Any?>(session.id, session.title, session.engineMode, session.modelId, session.createdAt, session.updatedAt),
        )
    }

    suspend fun insertMessage(message: ChatMessage) = withContext(Dispatchers.IO) {
        db.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO messages(id, session_id, role, content, error, token_count, latency_ms, created_at) " +
                "VALUES(?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(
                message.id, message.sessionId, message.role.name, message.content,
                message.error, 0, message.latencyMs, message.createdAt,
            ),
        )
    }

    suspend fun deleteSession(id: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.execSQL("DELETE FROM messages WHERE session_id = ?", arrayOf(id))
        db.writableDatabase.execSQL("DELETE FROM sessions WHERE id = ?", arrayOf(id))
    }

    fun newId(): String = UUID.randomUUID().toString()
}

private fun android.database.Cursor.getStringOrNull(index: Int): String? =
    if (isNull(index)) null else getString(index)
