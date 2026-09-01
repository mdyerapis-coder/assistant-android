package com.mdyerapis.sable.core.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract: assistant replies arrive as plain text with light markdown.
 * Rendering must never show raw fence/list/link syntax, and code content
 * must survive verbatim (no inline span interpretation inside fences).
 */
class MessageBlocksTest {

    @Test
    fun `plain line stays a paragraph`() {
        val blocks = parseMessageBlocks("Just a reply.")
        assertEquals(listOf(MessageBlock.Paragraph("Just a reply.")), blocks)
    }

    @Test
    fun `fenced code block captures language and verbatim content`() {
        val text = "Use this:\n```kotlin\nval x = **not bold**\n```\nDone"
        val blocks = parseMessageBlocks(text)
        assertEquals(
            listOf(
                MessageBlock.Paragraph("Use this:"),
                MessageBlock.CodeBlock("val x = **not bold**", "kotlin"),
                MessageBlock.Paragraph("Done"),
            ),
            blocks,
        )
    }

    @Test
    fun `unclosed fence swallows the remainder as code`() {
        val blocks = parseMessageBlocks("oops\n```\nstill code\n- not a list")
        assertEquals(
            listOf(
                MessageBlock.Paragraph("oops"),
                MessageBlock.CodeBlock("still code\n- not a list", null),
            ),
            blocks,
        )
    }

    @Test
    fun `dash lines group into an unordered list`() {
        val blocks = parseMessageBlocks("- first\n- second\n\ntrailing")
        assertEquals(
            listOf(
                MessageBlock.UnorderedList(listOf("first", "second")),
                MessageBlock.Paragraph("trailing"),
            ),
            blocks,
        )
    }

    @Test
    fun `numbered lines group into an ordered list`() {
        val blocks = parseMessageBlocks("1. one\n2. two")
        assertEquals(listOf(MessageBlock.OrderedList(listOf("one", "two"))), blocks)
    }

    @Test
    fun `heading captures its level`() {
        val blocks = parseMessageBlocks("## Plan\nDetails here")
        assertEquals(
            listOf(
                MessageBlock.Heading(2, "Plan"),
                MessageBlock.Paragraph("Details here"),
            ),
            blocks,
        )
    }

    @Test
    fun `inline bold and code become styled spans`() {
        val spans = parseInlineSpans("a **b** plus `c`")
        assertEquals(
            listOf(
                InlineSpan(2..6, InlineSpan.Kind.BOLD, null),
                InlineSpan(13..15, InlineSpan.Kind.CODE, null),
            ),
            spans,
        )
    }

    @Test
    fun `inline markdown link yields a LINK span with url`() {
        val spans = parseInlineSpans("see [docs](https://example.com/x) now")
        assertEquals(
            listOf(InlineSpan(4..32, InlineSpan.Kind.LINK, "https://example.com/x")),
            spans,
        )
    }
}
