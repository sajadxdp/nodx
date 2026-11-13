package app.nodx.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodx.AppContainer
import app.nodx.data.ChatRepository
import app.nodx.data.EngineModeSetting
import app.nodx.data.Settings
import app.nodx.data.SettingsStore
import app.nodx.domain.ChatEngine
import app.nodx.domain.ChatMessage
import app.nodx.domain.ChatSession
import app.nodx.domain.EngineStats
import app.nodx.domain.GenerateRequest
import app.nodx.domain.LoadRequest
import app.nodx.domain.MessageRole
import app.nodx.domain.TokenEvent
import app.nodx.domain.toEngineMessages
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val sessionId: String? = null,
    val title: String = "nodx",
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val engineLabel: String = "",
    val stats: EngineStats? = null,
)

/** Orchestrates send → engine stream → UI state → persistence. */
class ChatViewModel(
    private val container: AppContainer,
    private val repo: ChatRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var genJob: Job? = null

    fun send(text: String) {
        val content = text.trim()
        if (content.isEmpty() || _state.value.isGenerating) return

        val sessionId = _state.value.sessionId ?: repo.newId()
        val existing = _state.value.sessionId != null
        val session = ChatSession(
            id = sessionId,
            title = if (existing) _state.value.title else content.take(40),
            engineMode = "chat",
        )

        val userMsg = ChatMessage(
            id = repo.newId(),
            sessionId = sessionId,
            role = MessageRole.USER,
            content = content,
        )

        _state.value = _state.value.copy(
            sessionId = sessionId,
            title = session.title,
            messages = _state.value.messages + userMsg,
            isGenerating = true,
        )

        genJob = viewModelScope.launch {
            repo.upsertSession(session)
            repo.insertMessage(userMsg)

            // Full conversation history — the engine sees the whole chat.
            val history = _state.value.messages
            val cfg = settings.current()
            val engine = pickEngine(cfg) ?: run {
                finishWithFailure(
                    sessionId,
                    "No engine available — set an API key in Settings, or download "
                        + "and load a local model in Models.",
                )
                return@launch
            }

            val started = System.currentTimeMillis()
            val reply = StringBuilder()
            var failure: String? = null

            try {
                engine.generate(
                    GenerateRequest(
                        messages = toEngineMessages(history, cfg.systemPrompt.ifBlank { null }),
                        temperature = cfg.temperature,
                        maxTokens = cfg.maxTokens,
                    )
                ).collect { event ->
                    when (event) {
                        is TokenEvent.Delta -> {
                            reply.append(event.text)
                            _state.value = _state.value.copy(
                                messages = withLiveAssistant(reply.toString()),
                                stats = engine.stats,
                            )
                        }
                        is TokenEvent.Failed ->
                            failure = event.error.message ?: "Generation failed"
                        is TokenEvent.Done ->
                            _state.value = _state.value.copy(stats = engine.stats)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                failure = e.message ?: e.toString()
            }

            val latency = System.currentTimeMillis() - started
            val finalMsg = ChatMessage(
                id = repo.newId(),
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = if (failure != null) "" else reply.toString(),
                error = failure,
                latencyMs = latency,
            )
            if (finalMsg.content.isNotEmpty() || finalMsg.error != null) {
                repo.insertMessage(finalMsg)
            }
            _state.value = _state.value.copy(
                messages = _state.value.messages.filter { it.id != LIVE_ID } + finalMsg,
                isGenerating = false,
            )
        }
    }

    fun stop() {
        genJob?.cancel()
        genJob = null
        // Engines observe their own cancel paths; the job cancel unwinds collect.
        viewModelScope.launch { container.localEngine.cancel() }
        container.cloudEngine.cancel()
    }

    fun newSession() {
        genJob?.cancel()
        _state.value = ChatUiState()
    }

    fun openSession(id: String) {
        genJob?.cancel()
        viewModelScope.launch {
            val msgs = repo.messages(id)
            val session = repo.sessions().firstOrNull { it.id == id }
            _state.value = ChatUiState(
                sessionId = id,
                title = session?.title ?: "nodx",
                messages = msgs,
            )
        }
    }

    private suspend fun pickEngine(cfg: Settings): ChatEngine? = when (cfg.engineMode) {
        EngineModeSetting.LOCAL ->
            if (container.localEngine.isReady) container.localEngine else null
        EngineModeSetting.CLOUD -> {
            val request = settings.cloudRequest()
            if (request != null) {
                container.cloudEngine.load(request)
                container.cloudEngine
            } else {
                null
            }
        }
    }

    private fun finishWithFailure(sessionId: String, message: String) {
        val finalMsg = ChatMessage(
            id = repo.newId(),
            sessionId = sessionId,
            role = MessageRole.ASSISTANT,
            content = "",
            error = message,
        )
        _state.value = _state.value.copy(
            messages = _state.value.messages + finalMsg,
            isGenerating = false,
        )
    }

    /** Assistant bubble streaming in progress: replace or append the live one. */
    private fun withLiveAssistant(content: String): List<ChatMessage> {
        val live = ChatMessage(
            id = LIVE_ID,
            sessionId = _state.value.sessionId.orEmpty(),
            role = MessageRole.ASSISTANT,
            content = content,
        )
        val msgs = _state.value.messages.toMutableList()
        val idx = msgs.indexOfFirst { it.id == LIVE_ID }
        if (idx >= 0) msgs[idx] = live else msgs.add(live)
        return msgs
    }

    companion object {
        const val LIVE_ID = "__live__"
    }
}
