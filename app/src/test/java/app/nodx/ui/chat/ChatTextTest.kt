package app.nodx.ui.chat

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chat text parsing: fenced blocks, bold, inline code. */
class ChatTextTest {

    @Test
    fun `plain text is one rich segment`() {
        val segs = parseSegments("just words")
        assertEquals(1, segs.size)
        assertTrue(segs[0] is Segment.Rich)
        assertEquals("just words", (segs[0] as Segment.Rich).text.text)
    }

    @Test
    fun `fenced block becomes a code segment`() {
        val segs = parseSegments("before\n```py\nprint(1)\n```\nafter")
        assertEquals(3, segs.size)
        assertTrue(segs[0] is Segment.Rich)
        assertTrue(segs[1] is Segment.Code)
        assertEquals("print(1)", (segs[1] as Segment.Code).text)
        assertTrue(segs[2] is Segment.Rich)
        assertTrue((segs[2] as Segment.Rich).text.text.contains("after"))
    }

    @Test
    fun `bold span is applied`() {
        val s = buildRich("a **big** day")
        val plain = AnnotatedString(s.text).toString()
        assertTrue(plain.contains("big"))
        // One bold span covering exactly "big"
        val span = s.spanStyles.single { it.item.fontWeight != null }
        assertEquals(plain.indexOf("big"), span.start)
        assertEquals(plain.indexOf("big") + 3, span.end)
    }

    @Test
    fun `inline code span is applied`() {
        val s = buildRich("run `npm test` now")
        val span = s.spanStyles.single { it.item.fontFamily != null }
        val plain = s.text
        assertTrue(plain.contains("npm test"))
        assertEquals(plain.indexOf("npm test"), span.start)
    }

    @Test
    fun `unmatched markers stay literal`() {
        val s = buildRich("one **bold and a lone `tick")
        assertEquals(0, s.spanStyles.size)
    }
}
