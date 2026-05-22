package com.floatingclipboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Hand-rolled minimal markdown renderer for chat bubbles.
 * Supports — inline: **bold**, *italic*, _italic_, `code`.
 *           — block: # heading, - bullet, 1. numbered, blank-line-separated paragraphs.
 *
 * Doesn't pull in a markdown library because the only producer is Haiku in a vocab-tutor system
 * prompt, which uses a narrow subset; nested markup (e.g. **bold *with italic***) is not handled
 * — outer span wins and inner markers render literally. Multi-line code blocks (```…```) are
 * intentionally out of scope.
 */
@Composable
fun MarkdownText(
    text: String,
    color: Color,
    baseStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Paragraph -> Text(
                    text = parseInline(block.text),
                    style = baseStyle,
                    color = color,
                )
                is MdBlock.Heading -> {
                    val headingStyle = when (block.level) {
                        1 -> MaterialTheme.typography.titleMedium
                        2 -> MaterialTheme.typography.titleSmall
                        else -> baseStyle
                    }.copy(fontWeight = FontWeight.Bold)
                    Text(
                        text = parseInline(block.text),
                        style = headingStyle,
                        color = color,
                    )
                }
                is MdBlock.Bullet -> Row {
                    Text("•  ", style = baseStyle, color = color)
                    Text(
                        text = parseInline(block.text),
                        style = baseStyle,
                        color = color,
                    )
                }
                is MdBlock.Numbered -> Row {
                    Text("${block.index}. ", style = baseStyle, color = color, modifier = Modifier.padding(end = 4.dp))
                    Text(
                        text = parseInline(block.text),
                        style = baseStyle,
                        color = color,
                    )
                }
            }
        }
    }
}

private sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Numbered(val index: Int, val text: String) : MdBlock
}

private val HEADING_RE = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET_RE  = Regex("^\\s*[-*+]\\s+(.*)$")
private val NUMBERED_RE = Regex("^\\s*(\\d+)\\.\\s+(.*)$")

private fun parseBlocks(input: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paraBuf = StringBuilder()
    fun flushPara() {
        val s = paraBuf.toString().trim()
        if (s.isNotEmpty()) blocks.add(MdBlock.Paragraph(s))
        paraBuf.clear()
    }
    for (rawLine in input.lines()) {
        val line = rawLine.trimEnd()
        val heading = HEADING_RE.matchEntire(line)
        val bullet = BULLET_RE.matchEntire(line)
        val numbered = NUMBERED_RE.matchEntire(line)
        when {
            line.isBlank() -> flushPara()
            heading != null -> {
                flushPara()
                blocks.add(MdBlock.Heading(
                    level = heading.groupValues[1].length,
                    text = heading.groupValues[2],
                ))
            }
            bullet != null -> {
                flushPara()
                blocks.add(MdBlock.Bullet(bullet.groupValues[1]))
            }
            numbered != null -> {
                flushPara()
                blocks.add(MdBlock.Numbered(
                    index = numbered.groupValues[1].toIntOrNull() ?: 1,
                    text = numbered.groupValues[2],
                ))
            }
            else -> {
                if (paraBuf.isNotEmpty()) paraBuf.append(' ')
                paraBuf.append(line.trim())
            }
        }
    }
    flushPara()
    return blocks
}

/**
 * Inline parser. Walks the string once; at each cursor position checks for the longest matching
 * marker (`**` before `*` for bold-vs-italic disambiguation). Unclosed markers fall through as
 * literal characters so partial-streaming text renders cleanly (e.g. `He **freed` shows the
 * asterisks until the closing pair arrives).
 */
private fun parseInline(s: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        when {
            // **bold** — check first so single-* italic doesn't eat the leading `*`.
            i + 1 < s.length && s[i] == '*' && s[i + 1] == '*' -> {
                val end = s.indexOf("**", startIndex = i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(s.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(s[i]); i++
                }
            }
            // *italic* / _italic_ — only when the previous char isn't alphanumeric, to avoid
            // matching the apostrophe-like `*` mid-word.
            (s[i] == '*' || s[i] == '_') && (i == 0 || !s[i - 1].isLetterOrDigit()) -> {
                val ch = s[i]
                val end = s.indexOf(ch, startIndex = i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(s.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(s[i]); i++
                }
            }
            // `inline code`
            s[i] == '`' -> {
                val end = s.indexOf('`', startIndex = i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(s.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(s[i]); i++
                }
            }
            else -> {
                append(s[i]); i++
            }
        }
    }
}
