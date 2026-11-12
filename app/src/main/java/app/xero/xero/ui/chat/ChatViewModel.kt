package app.xero.xero.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xero.xero.data.ChatRepository
import app.xero.xero.data.SettingsStore
import app.xero.xero.data.EngineModeSetting
import app.xero.xero.domain.ChatEngine
import app.xero.xero.domain.ChatMessage
import app.xero.xero.domain.ChatSession
import app.xero.xero.domain.GenerateRequest
import app.xero.xero.domain.MessageRole
import app.xero.xero.domain.TokenEvent
import app.xero.xero.domain.toEngineMessages
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val sessionId: String? = null,
    val title: String = "Xero",
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
)

/** Orchestrates send → engine stream → UI state → persistence. */
class ChatViewModel(
    private val cloudEngine: ChatEngine,
    private val fakeEngine: ChatEngine,
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
        val session = _state.value.sessionId?.let {
            _state.value.messages.firstOrNull()?.let { m ->
                ChatSession(id = sessionId, title = _state.value.title, engineMode = "cloud")
            }
        } ?: ChatSession(id = sessionId, title = content, engineMode = "cloud")

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
            // Persist session + user message
            repo.upsertSession(session)
            repo.insertMessage(userMsg)

            val engine = pickEngine()
            val started = System.currentTimeMillis()
            var reply = StringBuilder()
            var failure: String? = null

            try {
                engine.generate(
                    GenerateRequest(messages = toEngineMessages(history = listOf(userMsg)))
                ).collect { event ->
                    when (event) {
                        is TokenEvent.Delta -> {
                            reply.append(event.text)
                            _state.value = _state.value.copy(messages = withAssistant(reply))
                        }
                        is TokenEvent.Failed -> failure = event.error.message ?: "Generation failed"
                        is TokenEvent.Done -> Unit
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    failure = e.message ?: e.toString()
                } else throw e
            }

            val latency = System.currentTimeMillis() - started
            val finalMsg = if (failure != null) {
                ChatMessage(
                    id = repo.newId(),
                    sessionId = sessionId,
                    role = MessageRole.ASSISTANT,
                    content = "",
                    error = failure,
                    latencyMs = latency,
                )
            } else {
                ChatMessage(
                    id = repo.newId(),
                    sessionId = sessionId,
                    role = MessageRole.ASSISTANT,
                    content = reply.toString(),
                    latencyMs = latency,
                )
            }
            if (finalMsg.content.isNotEmpty() || finalMsg.error != null) {
                repo.insertMessage(finalMsg)
            }
            _state.value = _state.value.copy(
                messages = _state.value.messages.dropLastAssistantPartial() + finalMsg,
                isGenerating = false,
            )
        }
    }

    fun stop() {
        genJob?.cancel()
        genJob = null
        _state.value = _state.value.copy(isGenerating = false)
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
                title = session?.title ?: "Xero",
                messages = msgs,
                isGenerating = false,
            )
        }
    }

    private suspend fun pickEngine(): ChatEngine {
        val s = settings.current()
        if (s.engineMode == EngineModeSetting.CLOUD) {
            val request = settings.cloudRequest()
            if (request != null) {
                cloudEngine.load(request)
                return cloudEngine
            }
        }
        return fakeEngine
    }

    /** Assistant content in progress: replace (or append) the live bubble. */
    private fun withAssistant(reply: StringBuilder): List<ChatMessage> {
        val msgs = _state.value.messages.toMutableList()
        val live = ChatMessage(
            id = LIVE_ID,
            sessionId = _state.value.sessionId.orEmpty(),
            role = MessageRole.ASSISTANT,
            content = reply.toString(),
        )
        val existing = msgs.indexOfFirst { it.id == LIVE_ID }
        if (existing >= 0) msgs[existing] = live else msgs.add(live)
        return msgs
    }

    private fun List<ChatMessage>.dropLastAssistantPartial(): List<ChatMessage> =
        filter { it.id != LIVE_ID }

    companion object {
        const val LIVE_ID = "__live__"
    }
}
