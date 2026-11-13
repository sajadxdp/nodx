package app.nodx.domain

import kotlinx.coroutines.flow.Flow

/** What an engine needs to become ready. */
sealed interface LoadRequest {
    data class Local(
        val modelPath: String,
        val contextLength: Int = 2048,
        val gpuLayers: Int = -1, // -1 = engine decides from device tier
        val threads: Int = 0,
    ) : LoadRequest

    data class Cloud(
        val provider: String,
        val baseUrl: String,
        val apiKey: String,
        val model: String,
    ) : LoadRequest
}

/** A message as engines consume it. */
data class EngineMessage(val role: EngineRole, val text: String)

enum class EngineRole { system, user, assistant }

/** Parameters for one generation. */
data class GenerateRequest(
    val messages: List<EngineMessage>,
    val temperature: Double = 0.7,
    val maxTokens: Int = 1024,
)

/** What engines emit while generating. */
sealed interface TokenEvent {
    data class Delta(val text: String) : TokenEvent

    /** Terminal success; the stream closes after this. */
    data class Done(val promptTokens: Int? = null) : TokenEvent

    /** Terminal failure; the stream closes after this. */
    data class Failed(val error: Throwable) : TokenEvent
}

/** Read-only runtime stats for the UI (tok/s, context fill). */
data class EngineStats(
    val tokensPerSecond: Double,
    val contextUsed: Int,
    val contextTotal: Int,
)

/**
 * The one contract every inference backend implements: llama.cpp on-device
 * (GGUF), cloud providers (OpenAI-compat, Anthropic, Google), or the fake
 * engine used in tests and before configuration.
 *
 * Callers must be able to call [cancel] at any point, including mid-stream;
 * after a cancel the stream closes without a terminal event.
 */
interface ChatEngine {
    /** Prepares the engine. Idempotent: calling again re-prepares. */
    suspend fun load(request: LoadRequest)

    val isReady: Boolean

    /**
     * Streams one generation. Exactly one terminal event ([Done] or [Failed])
     * is emitted unless the stream was canceled.
     */
    fun generate(request: GenerateRequest): Flow<TokenEvent>

    /** Cancels an in-flight generation. Must be safe to call when idle. */
    fun cancel()

    val stats: EngineStats?
}

/** Builds the [GenerateRequest] message list from a conversation. */
fun toEngineMessages(
    history: List<ChatMessage>,
    systemPrompt: String? = null,
): List<EngineMessage> = buildList {
    if (!systemPrompt.isNullOrEmpty()) add(EngineMessage(EngineRole.system, systemPrompt))
    history
        .filter { it.error == null }
        .forEach {
            add(
                EngineMessage(
                    role = when (it.role) {
                        MessageRole.SYSTEM -> EngineRole.system
                        MessageRole.USER -> EngineRole.user
                        MessageRole.ASSISTANT -> EngineRole.assistant
                    },
                    text = it.content,
                )
            )
        }
}
