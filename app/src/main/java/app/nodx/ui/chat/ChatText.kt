package app.nodx.ui.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** A parsed chat message: rich inline text and/or fenced code blocks. */
sealed interface Segment {
    data class Rich(val text: AnnotatedString) : Segment
    data class Code(val text: String) : Segment
}

private val FENCE = Regex("(?s)```[a-zA-Z0-9_+-]*\\n?(.*?)```")
private val INLINE = Regex("(\\*\\*(.+?)\\*\\*)|(`([^`\\n]+)`)")

/** Splits a message into rich-text and code-block segments. Pure function. */
fun parseSegments(text: String): List<Segment> {
    if (text.isBlank()) return listOf(Segment.Rich(AnnotatedString(text)))
    val out = mutableListOf<Segment>()
    var index = 0
    for (match in FENCE.findAll(text)) {
        if (match.range.first > index) {
            out += Segment.Rich(buildRich(text.substring(index, match.range.first)))
        }
        out += Segment.Code(match.groupValues[1].removeSuffix("\n"))
        index = match.range.last + 1
    }
    if (index < text.length) out += Segment.Rich(buildRich(text.substring(index)))
    return out
}

/** Applies **bold** and `inline code` spans. Pure function. */
fun buildRich(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    for (match in INLINE.findAll(text)) {
        append(text.substring(index, match.range.first))
        val bold = match.groupValues[2]
        val code = match.groupValues[4]
        // groupValues returns "" (not null) for non-participating groups.
        if (bold.isNotEmpty()) {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append(bold)
            pop()
        } else if (code.isNotEmpty()) {
            pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
            append(code)
            pop()
        }
        index = match.range.last + 1
    }
    if (index < text.length) append(text.substring(index))
}
