package app.xero.xero.data.cloud

import app.xero.xero.domain.EngineMessage
import app.xero.xero.domain.GenerateRequest
import app.xero.xero.domain.LoadRequest
import app.xero.xero.domain.TokenEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Pure SSE wire-format parsing for OpenAI-compatible chat streams.
 * Separated from I/O so it can be unit-tested against recorded fixtures. */
object OpenAiSseParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** Parses one `data:` payload; null when the line carries no content. */
    fun parseDelta(data: String): String? = try {
        if (data == "[DONE]") null
        else json.parseToJsonElement(data).jsonObject["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("delta")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.takeIf { it.isString }
            ?.content
            ?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    fun buildPayload(model: String, messages: List<EngineMessage>): String =
        buildJsonObject {
            put("model", model)
            put("stream", true)
            put("messages", buildJsonArray {
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role.name)
                        put("content", m.text)
                    })
                }
            })
        }.toString()

    fun parseErrorMessage(body: String): String = try {
        json.parseToJsonElement(body).jsonObject["error"]
            ?.jsonObject?.get("message")?.jsonPrimitive?.content
            ?: body.take(300)
    } catch (_: Exception) {
        body.take(300)
    }
}

/** OpenAI-compatible streaming chat engine: OpenAI, OpenRouter, DeepSeek,
 * Kimi, and any custom base URL speaking /chat/completions with SSE. */
class CloudEngine(private val http: OkHttpClient) :
    app.xero.xero.domain.ChatEngine {

    @Volatile
    private var config: LoadRequest.Cloud? = null

    @Volatile
    private var call: okhttp3.Call? = null

    override suspend fun load(request: LoadRequest) {
        check(request is LoadRequest.Cloud) {
            "CloudEngine only accepts CloudLoadRequest"
        }
        config = request
    }

    override val isReady: Boolean get() = config != null

    override fun generate(request: GenerateRequest) =
        kotlinx.coroutines.flow.channelFlow {
            val cfg = config ?: run {
                send(TokenEvent.Failed(IllegalStateException("Engine not loaded")))
                return@channelFlow
            }
            val url = cfg.baseUrl.trimEnd('/') + "/chat/completions"
            val body = OpenAiSseParser.buildPayload(cfg.model, request.messages)
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${cfg.apiKey}")
                .header("Accept", "text/event-stream")
                .post(body)
                .build()

            val active = http.newCall(req)
            call = active
            try {
                active.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val errBody = resp.body?.string().orEmpty()
                        send(
                            TokenEvent.Failed(
                                RuntimeException(
                                    "HTTP ${resp.code}: ${OpenAiSseParser.parseErrorMessage(errBody)}"
                                )
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
                        OpenAiSseParser.parseDelta(line.removePrefix("data:").trim())
                            ?.let { send(TokenEvent.Delta(it)) }
                        if (line.contains("[DONE]")) break
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

    override val stats: app.xero.xero.domain.EngineStats? get() = null
}
