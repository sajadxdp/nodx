package app.nodx.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Recorded-fixture tests for the OpenAI-compatible SSE parser — the
 * highest-value unit tests in the cloud path. */
class OpenAiSseParserTest {

    @Test
    fun `parses a content delta`() {
        val data = """{"choices":[{"delta":{"content":"Hello"}}]}"""
        assertEquals("Hello", OpenAiCompatAdapter.parseDelta(data))
    }

    @Test
    fun `parses empty-string delta as null`() {
        val data = """{"choices":[{"delta":{"content":""}}]}"""
        assertNull(OpenAiCompatAdapter.parseDelta(data))
    }

    @Test
    fun `role-only delta carries no content`() {
        val data = """{"choices":[{"delta":{"role":"assistant"}}]}"""
        assertNull(OpenAiCompatAdapter.parseDelta(data))
    }

    @Test
    fun `DONE sentinel is null`() {
        assertNull(OpenAiCompatAdapter.parseDelta("[DONE]"))
    }

    @Test
    fun `malformed json is null not crash`() {
        assertNull(OpenAiCompatAdapter.parseDelta("{\"choices\":"))
        assertNull(OpenAiCompatAdapter.parseDelta("not json at all"))
    }

    @Test
    fun `delta with reasoning field still yields content`() {
        val data = """{"choices":[{"delta":{"reasoning":"","content":"Hi"}}],"model":"x"}"""
        assertEquals("Hi", OpenAiCompatAdapter.parseDelta(data))
    }

    @Test
    fun `usage chunk without choices is null`() {
        val data = """{"usage":{"total_tokens":42}}"""
        assertNull(OpenAiCompatAdapter.parseDelta(data))
    }

    @Test
    fun `error message extraction prefers nested message`() {
        val body = """{"error":{"message":"bad key","type":"auth"}}"""
        assertEquals("bad key", OpenAiCompatAdapter.parseError(body))
    }

    @Test
    fun `error message falls back to body prefix`() {
        assertEquals("plain text", OpenAiCompatAdapter.parseError("plain text"))
    }
}
