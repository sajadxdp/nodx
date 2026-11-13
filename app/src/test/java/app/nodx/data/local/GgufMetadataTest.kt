package app.nodx.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** GGUF header reader tests against a synthesized v3 file. */
class GgufMetadataTest {

    /** Writes a minimal valid GGUF v3 file with typed KV pairs. */
    private fun writeGguf(kvs: List<Pair<String, Any>>): File {
        val out = ByteArrayOutputStream()

        fun u32(v: Long) {
            val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v.toInt()).array()
            out.write(b)
        }

        fun u64(v: Long) {
            val b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()
            out.write(b)
        }

        fun str(s: String) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            u64(bytes.size.toLong())
            out.write(bytes)
        }

        out.write("GGUF".toByteArray())
        u32(3) // version
        u64(1) // tensor count
        u64(kvs.size.toLong()) // kv count

        for ((key, value) in kvs) {
            str(key)
            when (value) {
                is String -> {
                    u32(8) // STRING
                    str(value)
                }

                is Int -> {
                    u32(4) // UINT32
                    u32(value.toLong())
                }

                is Long -> {
                    u32(11) // INT64
                    u64(value)
                }

                is Double -> {
                    u32(12) // FLOAT64
                    out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                        .putDouble(value).array())
                }

                is Boolean -> {
                    u32(7) // BOOL
                    out.write(if (value) 1 else 0)
                }
            }
        }

        val f = File.createTempFile("test", ".gguf")
        FileOutputStream(f).use { it.write(out.toByteArray()) }
        f.deleteOnExit()
        return f
    }

    @Test
    fun `reads context length and chat template`() {
        val f = writeGguf(
            listOf(
                "general.architecture" to "llama",
                "llama.context_length" to 4096,
                "llama.block_count" to 26L,
                "tokenizer.chat_template" to "<|im_start|>",
            )
        )
        val info = GgufMetadata.read(f.absolutePath)

        assertNotNull(info)
        assertEquals(3, info!!.version)
        assertEquals(4096L, info.contextLength)
        assertEquals("llama", info.arch)
        assertEquals(26L, info.blockCount)
        assertEquals("<|im_start|>", info.chatTemplate)
        assertTrue(info.hasChatTemplate)
    }

    @Test
    fun `missing fields come back null`() {
        val f = writeGguf(listOf("general.architecture" to "qwen2"))
        val info = GgufMetadata.read(f.absolutePath)

        assertNotNull(info)
        assertNull(info!!.contextLength)
        assertNull(info.chatTemplate)
        assertEquals("qwen2", info.arch)
    }

    @Test
    fun `bad magic returns null`() {
        val f = File.createTempFile("bad", ".gguf")
        FileOutputStream(f).use { it.write("NOPE".toByteArray()) }
        f.deleteOnExit()
        assertNull(GgufMetadata.read(f.absolutePath))
    }

    @Test
    fun `missing file returns null`() {
        assertNull(GgufMetadata.read("/no/such/file.gguf"))
    }

    @Test
    fun `mixed-type kv sequence stays aligned`() {
        // Order exercises every width; a misread would shift everything.
        val f = writeGguf(
            listOf(
                "general.architecture" to "gemma",
                "some.flag" to true,
                "some.score" to 0.5,
                "llama.context_length" to 2048,
                "tokenizer.chat_template" to "template",
            )
        )
        val info = GgufMetadata.read(f.absolutePath)

        assertNotNull(info)
        assertEquals("gemma", info!!.arch)
        assertEquals(2048L, info.contextLength)
        assertEquals("template", info.chatTemplate)
    }
}
