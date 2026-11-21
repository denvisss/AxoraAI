package com.axora.app.util

import com.axora.app.util.MarkdownParser.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `plain text becomes a single paragraph`() {
        val blocks = MarkdownParser.parse("Привет, мир")
        assertEquals(1, blocks.size)
        val paragraph = blocks[0] as Block.Paragraph
        assertEquals("Привет, мир", paragraph.segments.joinToString("") { it.text })
    }

    @Test
    fun `bold italic code strike and link are recognized`() {
        val spans = MarkdownParser.parseInline("**жирный** обычный *курсив* `код` ~~зачёркнутый~~ [ссылка](https://x.y)")
        val bold = spans.first()
        assertTrue(bold.bold)
        assertEquals("жирный", bold.text)
        assertTrue(spans.any { it.italic && it.text == "курсив" })
        assertTrue(spans.any { it.code && it.text == "код" })
        assertTrue(spans.any { it.strike && it.text == "зачёркнутый" })
        val link = spans.last()
        assertEquals("ссылка", link.text)
        assertEquals("https://x.y", link.link)
    }

    @Test
    fun `headings levels are parsed`() {
        val blocks = MarkdownParser.parse("# Один\n## Два\n### Три")
        assertEquals(3, blocks.size)
        assertEquals(1, (blocks[0] as Block.Heading).level)
        assertEquals(2, (blocks[1] as Block.Heading).level)
        assertEquals(3, (blocks[2] as Block.Heading).level)
    }

    @Test
    fun `code fence captures language and body`() {
        val blocks = MarkdownParser.parse("```kotlin\nval a = 1\nval b = 2\n```")
        val code = blocks[0] as Block.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("val a = 1\nval b = 2", code.code)
    }

    @Test
    fun `bullets and ordered lists are parsed`() {
        val blocks = MarkdownParser.parse("- один\n- два\n3. три")
        val items = blocks.filterIsInstance<Block.ListItem>()
        assertEquals(3, items.size)
        assertEquals(false, items[0].ordered)
        assertEquals(true, items[2].ordered)
        assertEquals(3, items[2].index)
    }

    @Test
    fun `quote and rule are parsed`() {
        val blocks = MarkdownParser.parse("> цитата\n---")
        assertTrue(blocks[0] is Block.Quote)
        assertTrue(blocks[1] is Block.Rule)
    }

    @Test
    fun `blank lines split paragraphs`() {
        val blocks = MarkdownParser.parse("первый\n\nвторой")
        assertEquals(2, blocks.size)
    }

    @Test
    fun `inline bold inside italic-free text keeps plain segments`() {
        val spans = MarkdownParser.parseInline("a **b** c")
        assertEquals(listOf("a ", "b", " c"), spans.map { it.text })
        assertEquals(false, spans[0].bold)
        assertEquals(true, spans[1].bold)
    }
}
