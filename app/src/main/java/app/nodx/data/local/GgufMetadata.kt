package app.nodx.data.local

import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Metadata parsed from a GGUF file header. */
data class GgufInfo(
    val version: Int,
    val contextLength: Long?,
    val chatTemplate: String?,
    val arch: String?,
    val blockCount: Long?,
) {
    val hasChatTemplate: Boolean get() = !chatTemplate.isNullOrEmpty()
}

/**
 * Minimal GGUF v2/v3 header reader: magic, version, then key-value metadata
 * (keeping context length, chat template, architecture, block count).
 * Value types and widths follow the GGUF spec (gguf.h). Ported from the
 * reference client's gguf_metadata.dart.
 */
object GgufMetadata {

    fun read(path: String): GgufInfo? = try {
        RandomAccessFile(File(path), "r").use { raf -> Reader(raf).read() }
    } catch (_: Exception) {
        null
    }

    private class Reader(private val raf: RandomAccessFile) {
        private val kv = mutableMapOf<String, Any>()

        fun read(): GgufInfo? {
            if (u32() != 0x46554747L) return null // "GGUF" little-endian
            val version = u32().toInt()
            if (version !in 2..3) return null
            u64() // tensor count — unused
            val kvCount = u64().toInt()

            repeat(kvCount) { _ ->
                val key = string()
                val value = value() ?: return@repeat
                kv[key] = value
            }

            val blockCount = kv.entries
                .firstOrNull { it.key.endsWith(".block_count") }?.value as? Long

            return GgufInfo(
                version = version,
                contextLength = (kv["llama.context_length"]
                    ?: kv[".general.context_length"]) as? Long,
                chatTemplate = kv["tokenizer.chat_template"] as? String,
                arch = kv["general.architecture"] as? String,
                blockCount = blockCount,
            )
        }

        // ── primitive reads (all little-endian) ──

        private fun bytes(n: Int): ByteArray {
            val b = ByteArray(n)
            var off = 0
            while (off < n) {
                val r = raf.read(b, off, n - off)
                if (r < 0) throw EOFException()
                off += r
            }
            return b
        }

        private fun u16(): Int =
            ByteBuffer.wrap(bytes(2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

        private fun u32(): Long {
            val b = bytes(4)
            var v = 0L
            for (i in 3 downTo 0) v = (v shl 8) or (b[i].toLong() and 0xFF)
            return v
        }

        private fun u64(): Long {
            val b = bytes(8)
            var v = 0L
            for (i in 7 downTo 0) v = (v shl 8) or (b[i].toLong() and 0xFF)
            return v
        }

        private fun f32(): Float =
            ByteBuffer.wrap(bytes(4)).order(ByteOrder.LITTLE_ENDIAN).float

        private fun f64(): Double =
            ByteBuffer.wrap(bytes(8)).order(ByteOrder.LITTLE_ENDIAN).double

        private fun string(): String {
            val len = u64().toInt()
            return String(bytes(len), Charsets.UTF_8)
        }

        /** GGUF value types per gguf.h. Returns Long for integers, Double for
         * floats, Boolean, String, or List<*> for arrays; null to skip. */
        private fun value(): Any? = when (val type = u32().toInt()) {
            0 -> bytes(1)[0].toLong() and 0xFF          // UINT8
            1 -> bytes(1)[0].toLong()                    // INT8
            2 -> u16().toLong()                          // UINT16
            3 -> bytes(2).let {                          // INT16
                ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).short.toLong()
            }
            4 -> u32()                                   // UINT32
            5 -> u32().let {                             // INT32 (sign from bits)
                if (it >= 0x80000000L) it - 0x100000000L else it
            }
            6 -> f32().toDouble()                        // FLOAT32
            7 -> bytes(1)[0].toInt() != 0                // BOOL
            8 -> string()                                // STRING
            9 -> {                                       // ARRAY
                val elemType = u32().toInt()
                val count = u64().toInt()
                when (elemType) {
                    8 -> List(count) { string() }
                    else -> { // skip non-string arrays by size
                        repeat(count) { value() }
                        null
                    }
                }
            }
            10 -> u64()                                  // UINT64
            11 -> u64()                                  // INT64
            12 -> f64()                                  // FLOAT64
            else -> null                                 // unknown type
        }
    }
}
