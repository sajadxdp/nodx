package app.nodx.domain

/** Role of a persisted message. */
enum class MessageRole { SYSTEM, USER, ASSISTANT }

/** A persisted chat message. */
data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    /** Non-null when this message represents a failed generation. */
    val error: String? = null,
    val latencyMs: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A conversation with its engine preference ('local' or 'cloud'). */
data class ChatSession(
    val id: String,
    val title: String,
    val engineMode: String,
    val modelId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
