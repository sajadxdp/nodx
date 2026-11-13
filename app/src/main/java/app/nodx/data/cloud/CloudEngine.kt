package app.nodx.data.cloud

import app.nodx.domain.ChatEngine
import app.nodx.domain.EngineStats
import app.nodx.domain.GenerateRequest
import app.nodx.domain.LoadRequest
import app.nodx.domain.TokenEvent
import kotlinx.coroutines.flow.channelFlow
import okhttp3.OkHttpClient

/** Providers that speak one of the three streaming protocols. */
fun adapterFor(provider: String): CloudAdapter = when (provider) {
    "ANTHROPIC" -> AnthropicAdapter
    "GOOGLE" -> GoogleAdapter
    else -> OpenAiCompatAdapter
}

/**
 * Streaming cloud engine. One instance handles every provider; the adapter
 * is chosen per generation from the loaded [LoadRequest.Cloud].
 */
class CloudEngine(private val http: OkHttpClient) : ChatEngine {

    @Volatile
    private var config: LoadRequest.Cloud? = null

    @Volatile
    private var call: okhttp3.Call? = null

    override suspend fun load(request: LoadRequest) {
        check(request is LoadRequest.Cloud) { "CloudEngine only accepts CloudLoadRequest" }
        config = request
    }

    override val isReady: Boolean get() = config != null

    override fun generate(request: GenerateRequest) = channelFlow {
        val cfg = config ?: run {
            send(TokenEvent.Failed(IllegalStateException("Engine not loaded")))
            return@channelFlow
        }
        val adapter = adapterFor(cfg.provider)
        val active = http.newCall(adapter.buildRequest(cfg, request, http))
        call = active
        try {
            active.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errBody = resp.body?.string().orEmpty()
                    send(
                        TokenEvent.Failed(
                            RuntimeException("HTTP ${resp.code}: ${adapter.parseError(errBody)}")
                        )
                    )
                    return@use
                }
                val source = resp.body?.source() ?: run {
                    send(TokenEvent.Failed(RuntimeException("Empty response body")))
                    return@use
                }
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    adapter.parseDelta(line)?.let { send(TokenEvent.Delta(it)) }
                    if (adapter.isDone(line)) break
                }
                send(TokenEvent.Done())
            }
        } catch (e: java.io.IOException) {
            if (!active.isCanceled()) send(TokenEvent.Failed(e))
        } catch (e: Exception) {
            if (!active.isCanceled()) send(TokenEvent.Failed(e))
        } finally {
            call = null
        }
    }

    override fun cancel() {
        call?.cancel()
        call = null
    }

    override val stats: EngineStats? get() = null
}
