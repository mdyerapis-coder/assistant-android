package com.mdyerapis.sable.core.designsystem.components

/**
 * Block-level markdown parsing for assistant replies.
 *
 * Pure functions — no Compose imports — so the grammar is unit-testable on
 * the JVM without a Compose runtime. [MessageContent] renders these blocks.
 */

data class InlineSpan(
    /** Range over the RAW text, delimiters included. */
    val range: IntRange,
    val kind: Kind,
    /** Link URL when [kind] is [Kind.LINK], else null. */
    val target: String? = null,
) {
    enum class Kind { BOLD, CODE, LINK }
}

sealed interface MessageBlock {
    data class Paragraph(val text: String) : MessageBlock
    data class Heading(val level: Int, val text: String) : MessageBlock
    data class UnorderedList(val items: List<String>) : MessageBlock
    data class OrderedList(val items: List<String>) : MessageBlock
    data class CodeBlock(val content: String, val language: String?) : MessageBlock
}

private val fenceRegex = Regex("""^```(\w*)\s*$""")
private val headingRegex = Regex("""^(#{1,6})\s+(.+?)\s*$""")
private val bulletRegex = Regex("""^\s*[-*+]\s+(.+?)\s*$""")
private val orderedRegex = Regex("""^\s*\d+[.)]\s+(.+?)\s*$""")

/** Splits [text] into renderable blocks; never throws, degrades to one paragraph. */
fun parseMessageBlocks(text: String): List<MessageBlock> {
    if (text.isBlank()) return listOf(MessageBlock.Paragraph(text))

    val lines = text.split('\n')
    val blocks = mutableListOf<MessageBlock>()
    var i = 0
    val paragraph = StringBuilder()

    fun flushParagraph() {
        val p = paragraph.toString().trimEnd('\n')
        paragraph.clear()
        if (p.isNotBlank()) blocks.add(MessageBlock.Paragraph(p))
    }

    while (i < lines.size) {
        val line = lines[i]
        val fence = fenceRegex.matchEntire(line.trimEnd())
        val heading = headingRegex.matchEntire(line)
        val bullet = bulletRegex.matchEntire(line)
        val ordered = orderedRegex.matchEntire(line)

        when {
            fence != null -> {
                flushParagraph()
                val language = fence.groupValues[1].ifBlank { null }
                val body = StringBuilder()
                var closed = false
                i++
                while (i < lines.size) {
                    if (fenceRegex.matchEntire(lines[i].trimEnd()) != null) {
                        closed = true
                        break
                    }
                    body.append(lines[i]).append('\n')
                    i++
                }
                if (!closed) i-- // consume to end
                blocks.add(
                    MessageBlock.CodeBlock(body.toString().trimEnd('\n'), language)
                )
            }
            heading != null -> {
                flushParagraph()
                blocks.add(
                    MessageBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
                )
            }
            bullet != null -> {
                flushParagraph()
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val m = bulletRegex.matchEntire(lines[i]) ?: break
                    items.add(m.groupValues[1])
                    i++
                }
                blocks.add(MessageBlock.UnorderedList(items))
                i-- // outer loop advances past the item we didn't consume
            }
            ordered != null -> {
                flushParagraph()
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val m = orderedRegex.matchEntire(lines[i]) ?: break
                    items.add(m.groupValues[1])
                    i++
                }
                blocks.add(MessageBlock.OrderedList(items))
                i--
            }
            line.isBlank() -> flushParagraph()
            else -> paragraph.append(line).append('\n')
        }
        i++
    }
    flushParagraph()
    return if (blocks.isEmpty()) listOf(MessageBlock.Paragraph(text)) else blocks
}

private val boldInlineRegex = Regex("""\*\*(.+?)\*\*""")
private val codeInlineRegex = Regex("""`([^`\n]+)`""")
private val linkInlineRegex = Regex("""\[[^\]]+]\(\S+\)""")

/** Inline spans over RAW text, left-to-right, non-overlapping; markers stay in the range. */
fun parseInlineSpans(text: String): List<InlineSpan> {
    val candidates = buildList {
        boldInlineRegex.findAll(text).forEach {
            add(InlineSpan(it.range, InlineSpan.Kind.BOLD))
        }
        codeInlineRegex.findAll(text).forEach {
            add(InlineSpan(it.range, InlineSpan.Kind.CODE))
        }
        linkInlineRegex.findAll(text).forEach { match ->
            val url = match.value
                .substringAfter('(')
                .removeSuffix(")")
            add(InlineSpan(match.range, InlineSpan.Kind.LINK, url))
        }
    }.sortedBy { it.range.first }

    val spans = mutableListOf<InlineSpan>()
    var cursor = 0
    for (span in candidates) {
        if (span.range.first >= cursor) {
            spans.add(span)
            cursor = span.range.last + 1
        }
    }
    return spans
}

/** Strips marker characters for [span] over [raw], leaving inner content. */
fun innerText(raw: String, span: InlineSpan): String = when (span.kind) {
    InlineSpan.Kind.BOLD -> raw.substring(span.range).removeSurrounding("**")
    InlineSpan.Kind.CODE -> raw.substring(span.range).removeSurrounding("`")
    InlineSpan.Kind.LINK -> {
        val s = raw.substring(span.range)
        s.substringAfter('[').substringBefore(']')
    }
}
