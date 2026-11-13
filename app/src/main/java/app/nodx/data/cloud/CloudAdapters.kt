package app.nodx.data.cloud

import app.nodx.domain.EngineMessage
import app.nodx.domain.EngineRole
import app.nodx.domain.GenerateRequest
import app.nodx.domain.LoadRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

/**
 * One streaming protocol. Adapters translate the shared ChatEngine request
 * into provider wire format and pull content deltas out of provider SSE
 * lines. Kept as pure functions so parsers are unit-testable from fixtures.
 */
interface CloudAdapter {
    fun buildRequest(cfg: LoadRequest.Cloud, req: GenerateRequest, http: OkHttpClient): Request
    /** Content delta from one `data:` line, or null when it carries none. */
    fun parseDelta(line: String): String?
    /** True when this line terminates the stream. */
    fun isDone(line: String): Boolean
    fun parseError(body: String): String
}

object Wire {
    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    fun parse(body: String): JsonObject = json.parseToJsonElement(body).jsonObject

    fun messagesArray(messages: List<EngineMessage>): kotlinx.serialization.json.JsonArray =
        buildJsonArray {
            messages.forEach { m ->
                add(buildJsonObject {
                    put("role", m.role.name)
                    put("content", m.text)
                })
            }
        }

    fun parseErrorMessage(obj: JsonObject): String? = try {
        obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }
}

// ── OpenAI-compatible (OpenAI, OpenRouter, DeepSeek, Kimi, custom) ──────

object OpenAiCompatAdapter : CloudAdapter {

    override fun buildRequest(cfg: LoadRequest.Cloud, req: GenerateRequest, http: OkHttpClient): Request {
        val payload = buildJsonObject {
            put("model", cfg.model)
            put("stream", true)
            put("messages", splitSystem(req.messages))
            put("temperature", req.temperature)
            put("max_tokens", req.maxTokens)
        }.toString()
        return Request.Builder()
            .url(cfg.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .header("Accept", "text/event-stream")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
    }

    /** OpenAI takes system as a top-level message. */
    private fun splitSystem(messages: List<EngineMessage>): kotlinx.serialization.json.JsonElement =
        Wire.messagesArray(messages)

    override fun parseDelta(line: String): String? = try {
        val data = line.removePrefix("data:").trim()
        if (data == "[DONE]") null
        else Wire.parse(data)["choices"]
            ?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("delta")?.jsonObject
            ?.get("content")?.jsonPrimitive
            ?.takeIf { it.isString }?.content
            ?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    override fun isDone(line: String): Boolean = line.contains("[DONE]")

    override fun parseError(body: String): String = try {
        Wire.parseErrorMessage(Wire.parse(body)) ?: body.take(300)
    } catch (_: Exception) {
        body.take(300)
    }
}

// ── Anthropic Messages API ──────────────────────────────────────────────

object AnthropicAdapter : CloudAdapter {

    private const val API_VERSION = "2023-06-01"

    override fun buildRequest(cfg: LoadRequest.Cloud, req: GenerateRequest, http: OkHttpClient): Request {
        // System is a top-level field, not a message. Roles: user/assistant only.
        val system = req.messages.filter { it.role == EngineRole.system }
            .joinToString("\n\n") { it.text }
        val messages = buildJsonArray {
            req.messages.filter { it.role != EngineRole.system }.forEach { m ->
                add(buildJsonObject {
                    put("role", m.role.name) // user | assistant
                    put("content", m.text)
                })
            }
        }
        val payload = buildJsonObject {
            put("model", cfg.model)
            put("stream", true)
            put("max_tokens", req.maxTokens)
            put("temperature", req.temperature)
            if (system.isNotEmpty()) put("system", system)
            put("messages", messages)
        }.toString()
        return Request.Builder()
            .url(cfg.baseUrl.trimEnd('/') + "/messages")
            .header("x-api-key", cfg.apiKey)
            .header("anthropic-version", API_VERSION)
            .header("Accept", "text/event-stream")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
    }

    /** data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"…"}} */
    override fun parseDelta(line: String): String? = try {
        val data = line.removePrefix("data:").trim()
        if (!data.startsWith("{")) null
        else {
            val obj = Wire.parse(data)
            if (obj["type"]?.jsonPrimitive?.content == "content_block_delta")
                obj["delta"]?.jsonObject?.get("text")?.jsonPrimitive
                    ?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }
            else null
        }
    } catch (_: Exception) {
        null
    }

    override fun isDone(line: String): Boolean = line.contains("\"message_stop\"")

    override fun parseError(body: String): String = try {
        Wire.parseErrorMessage(Wire.parse(body))
            ?: (try { Wire.parse(body)["type"]?.jsonPrimitive?.content } catch (_: Exception) { null })
            ?: body.take(300)
    } catch (_: Exception) {
        body.take(300)
    }
}

// ── Google Gemini ───────────────────────────────────────────────────────

object GoogleAdapter : CloudAdapter {

    override fun buildRequest(cfg: LoadRequest.Cloud, req: GenerateRequest, http: OkHttpClient): Request {
        val system = req.messages.filter { it.role == EngineRole.system }
            .joinToString("\n\n") { it.text }
        val contents = buildJsonArray {
            req.messages.filter { it.role != EngineRole.system }.forEach { m ->
                add(buildJsonObject {
                    put("role", if (m.role == EngineRole.assistant) "model" else "user")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", m.text) }) })
                })
            }
        }
        val payload = buildJsonObject {
            if (system.isNotEmpty()) put(
                "systemInstruction",
                buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) }) }
            )
            put("contents", contents)
            put("generationConfig", buildJsonObject {
                put("temperature", req.temperature)
                put("maxOutputTokens", req.maxTokens)
            })
        }.toString()
        val url = cfg.baseUrl.trimEnd('/') +
            "/models/${cfg.model}:streamGenerateContent?alt=sse&key=${cfg.apiKey}"
        return Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
    }

    /** data: {"candidates":[{"content":{"parts":[{"text":"…"}],"role":"model"}}]} */
    override fun parseDelta(line: String): String? = try {
        val data = line.removePrefix("data:").trim()
        if (!data.startsWith("{")) null
        else Wire.parse(data)["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive
            ?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    override fun isDone(line: String): Boolean = false // stream ends on connection close

    override fun parseError(body: String): String = try {
        val obj = Wire.parse(body)
        val googleErr = try {
            obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
        } catch (_: Exception) { null }
        googleErr ?: body.take(300)
    } catch (_: Exception) {
        body.take(300)
    }
}
