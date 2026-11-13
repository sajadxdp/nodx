package app.nodx.data.cloud

import app.nodx.domain.EngineMessage
import app.nodx.domain.EngineRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recorded-fixture tests for the OpenAI-compatible SSE parser — the
 * highest-value unit tests in the cloud path. */
class OpenAiSseParserTest {

    @Test
    fun `parses a content delta`() {
        val data = """{"choices":[{"delta":{"content":"Hello"}}]}"""
        assertEquals("Hello", OpenAiSseParser.parseDelta(data))
    }

    @Test
    fun `parses empty-string delta as null`() {
        val data = """{"choices":[{"delta":{"content":""}}]}"""
        assertNull(OpenAiSseParser.parseDelta(data))
    }

    @Test
    fun `role-only delta carries no content`() {
        val data = """{"choices":[{"delta":{"role":"assistant"}}]}"""
        assertNull(OpenAiSseParser.parseDelta(data))
    }

    @Test
    fun `DONE sentinel is null`() {
        assertNull(OpenAiSseParser.parseDelta("[DONE]"))
    }

    @Test
    fun `malformed json is null not crash`() {
        assertNull(OpenAiSseParser.parseDelta("{\"choices\":"))
        assertNull(OpenAiSseParser.parseDelta("not json at all"))
    }

    @Test
    fun `delta with reasoning field still yields content`() {
        val data = """{"choices":[{"delta":{"reasoning":"","content":"Hi"}}],"model":"x"}"""
        assertEquals("Hi", OpenAiSseParser.parseDelta(data))
    }

    @Test
    fun `usage chunk without choices is null`() {
        val data = """{"usage":{"total_tokens":42}}"""
        assertNull(OpenAiSseParser.parseDelta(data))
    }

    @Test
    fun `payload builds valid json with all messages`() {
        val payload = OpenAiSseParser.buildPayload(
            "test-model",
            listOf(
                EngineMessage(EngineRole.system, "be brief"),
                EngineMessage(EngineRole.user, "hi"),
            ),
        )
        assertTrue(payload.contains("\"model\":\"test-model\""))
        assertTrue(payload.contains("\"stream\":true"))
        assertTrue(payload.contains("\"role\":\"system\""))
        assertTrue(payload.contains("\"role\":\"user\""))
        assertTrue(payload.contains("be brief"))
        // Round-trips through the JSON parser
        kotlinx.serialization.json.Json.parseToJsonElement(payload)
    }

    @Test
    fun `error message extraction prefers nested message`() {
        val body = """{"error":{"message":"bad key","type":"auth"}}"""
        assertEquals("bad key", OpenAiSseParser.parseErrorMessage(body))
    }

    @Test
    fun `error message falls back to body prefix`() {
        assertEquals("plain text", OpenAiSseParser.parseErrorMessage("plain text"))
    }
}
