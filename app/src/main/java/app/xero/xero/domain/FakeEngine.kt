package app.xero.xero.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicBoolean

/** Phase-1 placeholder engine: streams a canned reply token by token so the
 * full send → stream → render → persist path is exercised before the real
 * engines are configured. Replaced by [app.xero.xero.data.cloud.CloudEngine]
 * in Phase 2 and llama.cpp in Phase 3. */
class FakeEngine : ChatEngine {
    private val ready = AtomicBoolean(false)
    private val canceled = AtomicBoolean(false)

    override suspend fun load(request: LoadRequest) {
        delay(200)
        ready.set(true)
    }

    override val isReady: Boolean get() = ready.get()

    override fun generate(request: GenerateRequest): Flow<TokenEvent> = flow {
        canceled.set(false)
        val reply = "[fake] This reply streams token-by-token to prove the " +
            "pipeline — configure a cloud provider in Settings for real answers."
        reply.split(" ").forEach { word ->
            if (canceled.get()) return@flow
            delay(90)
            emit(TokenEvent.Delta("$word "))
        }
        if (!canceled.get()) emit(TokenEvent.Done(promptTokens = 12))
    }

    override fun cancel() {
        canceled.set(true)
    }

    override val stats: EngineStats? =
        EngineStats(tokensPerSecond = 42.0, contextUsed = 128, contextTotal = 2048)
}
