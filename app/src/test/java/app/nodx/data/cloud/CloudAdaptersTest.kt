package app.nodx.data.cloud

import app.nodx.domain.EngineMessage
import app.nodx.domain.EngineRole
import app.nodx.domain.GenerateRequest
import app.nodx.domain.LoadRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recorded-fixture tests for the three cloud SSE protocols. */
class CloudAdaptersTest {

    private val cfg = LoadRequest.Cloud(
        provider = "test", baseUrl = "https://x.test/v1",
        apiKey = "key", model = "model-1",
    )

    /** RequestBody.toString() is not the payload; read it properly. */
    private fun okhttp3.RequestBody.utf8(): String {
        val buf = okio.Buffer()
        writeTo(buf)
        return buf.readUtf8()
    }

    private fun request(req: GenerateRequest, adapter: CloudAdapter) =
        adapter.buildRequest(cfg, req, okhttp3.OkHttpClient())

    // ── OpenAI-compat ──

    @Test
    fun `openai request carries auth and stream`() {
        val req = request(
            GenerateRequest(listOf(EngineMessage(EngineRole.user, "hi"))),
            OpenAiCompatAdapter,
        )
        assertEquals("Bearer key", req.headers["Authorization"])
        assertEquals("https://x.test/v1/chat/completions", req.url.toString())
        assertTrue(req.body!!.utf8().contains("\"stream\":true"))
    }

    @Test
    fun `openai delta parses`() {
        assertEquals("Hello", OpenAiCompatAdapter.parseDelta("""{"choices":[{"delta":{"content":"Hello"}}]}"""))
        assertNull(OpenAiCompatAdapter.parseDelta("data: [DONE]"))
        assertNull(OpenAiCompatAdapter.parseDelta("garbage{"))
    }

    // ── Anthropic ──

    @Test
    fun `anthropic request uses headers and system field`() {
        val req = request(
            GenerateRequest(
                listOf(
                    EngineMessage(EngineRole.system, "be brief"),
                    EngineMessage(EngineRole.user, "hi"),
                )
            ),
            AnthropicAdapter,
        )
        assertEquals("key", req.headers["x-api-key"])
        assertEquals("2023-06-01", req.headers["anthropic-version"])
        assertEquals("https://x.test/v1/messages", req.url.toString())
        val body = req.body!!.utf8()
        assertTrue(body.contains("\"system\":\"be brief\""))
        assertTrue(body.contains("\"max_tokens\":"))
        assertTrue(!body.contains("\"role\":\"system\""))
    }

    @Test
    fun `anthropic content_block_delta parses`() {
        val line = """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi"}}"""
        assertEquals("Hi", AnthropicAdapter.parseDelta(line))
        assertNull(AnthropicAdapter.parseDelta("""data: {"type":"message_start","message":{}}"""))
        assertNull(AnthropicAdapter.parseDelta("data: [DONE]"))
        assertTrue(AnthropicAdapter.isDone("""data: {"type":"message_stop"}"""))
    }

    @Test
    fun `anthropic error message extraction`() {
        assertEquals(
            "bad key",
            AnthropicAdapter.parseError("""{"type":"error","error":{"type":"authentication_error","message":"bad key"}}"""),
        )
    }

    // ── Google ──

    @Test
    fun `google request uses key query and model path`() {
        val req = request(
            GenerateRequest(listOf(EngineMessage(EngineRole.user, "hi"))),
            GoogleAdapter,
        )
        assertEquals(
            "https://x.test/v1/models/model-1:streamGenerateContent",
            req.url.toString().substringBefore("?"),
        )
        assertTrue(req.url.queryParameterNames.contains("key"))
        assertTrue(req.url.queryParameter("alt") == "sse")
        val body = req.body!!.utf8()
        assertTrue(body.contains("\"role\":\"user\""))
        assertTrue(!body.contains("\"role\":\"assistant\""))
    }

    @Test
    fun `google parts text parses`() {
        val line = """data: {"candidates":[{"content":{"parts":[{"text":"Hola"}],"role":"model"},"index":0}]}"""
        assertEquals("Hola", GoogleAdapter.parseDelta(line))
        assertNull(GoogleAdapter.parseDelta("""data: {"candidates":[]}"""))
        assertNull(GoogleAdapter.parseDelta("data: not-json"))
    }

    @Test
    fun `google error message extraction`() {
        assertEquals(
            "API key invalid",
            GoogleAdapter.parseError("""{"error":{"code":400,"message":"API key invalid","status":"INVALID_ARGUMENT"}}"""),
        )
    }

    @Test
    fun `adapter routing by provider`() {
        assertEquals(AnthropicAdapter, adapterFor("ANTHROPIC"))
        assertEquals(GoogleAdapter, adapterFor("GOOGLE"))
        assertEquals(OpenAiCompatAdapter, adapterFor("OPENAI"))
        assertEquals(OpenAiCompatAdapter, adapterFor("CUSTOM"))
        assertEquals(OpenAiCompatAdapter, adapterFor("whatever"))
    }
}
