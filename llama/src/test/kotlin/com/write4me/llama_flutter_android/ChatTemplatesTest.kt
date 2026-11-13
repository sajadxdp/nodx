package com.write4me.llama_flutter_android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chat-template formatting tests for the ported template registry. */
class ChatTemplatesTest {

    private val msgs = listOf(
        TemplateChatMessage("system", "Be brief."),
        TemplateChatMessage("user", "Hi"),
        TemplateChatMessage("assistant", "Hello!"),
        TemplateChatMessage("user", "Bye"),
    )

    @Test
    fun `chatml template ends with assistant turn`() {
        val out = ChatMLTemplate().format(msgs)
        assertTrue(out.startsWith("<|im_start|>system"))
        assertTrue(out.trimEnd().endsWith("<|im_start|>assistant"))
        assertTrue(out.contains("Be brief."))
        assertTrue(out.contains("<|im_end|>"))
    }

    @Test
    fun `llama3 template uses header blocks`() {
        val out = Llama3Template().format(msgs)
        assertTrue(out.contains("<|start_header_id|>system<|end_header_id|>"))
        assertTrue(out.contains("<|eot_id|>"))
        assertTrue(out.trimEnd().endsWith("<|start_header_id|>assistant<|end_header_id|>"))
    }

    @Test
    fun `manager detects qwen by filename`() {
        val t = ChatTemplateManager.detectTemplate("qwen2.5-1.5b-instruct-q4_k_m.gguf")
        assertEquals("chatml", t.name)
    }

    @Test
    fun `manager detects llama by filename`() {
        val t = ChatTemplateManager.detectTemplate("llama-3.2-1b-instruct-q4_k_m.gguf")
        assertTrue(t.name.contains("llama"))
    }

    @Test
    fun `manager falls back for unknown names`() {
        val t = ChatTemplateManager.detectTemplate("mystery-model.gguf")
        assertTrue(t.format(msgs).isNotEmpty()) // fallback still formats
    }
}
