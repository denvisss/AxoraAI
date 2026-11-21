package com.axora.app.util

object MarkdownParser {

    sealed interface Block {
        data class Paragraph(val segments: List<Span>) : Block
        data class Heading(val level: Int, val segments: List<Span>) : Block
        data class CodeBlock(val language: String, val code: String) : Block
        data class ListItem(val ordered: Boolean, val index: Int, val segments: List<Span>) : Block
        data class Quote(val segments: List<Span>) : Block
        data object Rule : Block
    }

    data class Span(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val strike: Boolean = false,
        val link: String? = null
    )

    private val INLINE = Regex(
        """(\*\*|__)(.+?)\1|\*(?!\*)(.+?)\*|`([^`]+)`|~~(.+?)~~|\[([^\]]+)\]\(([^)\s]+)\)""",
        RegexOption.DOT_MATCHES_ALL
    )

    private val BULLET = Regex("""^\s{0,3}[-*+]\s+(.*)$""")
    private val ORDERED = Regex("""^\s{0,3}(\d{1,3})[.)]\s+(.*)$""")
    private val HEADING = Regex("""^(#{1,4})\s+(.*)$""")
    private val RULE = Regex("""^\s{0,3}([-*_])\1{2,}\s*$""")

    fun parse(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val lines = markdown.lines()
        var i = 0

        fun flushParagraph(accumulator: MutableList<String>) {
            if (accumulator.isNotEmpty()) {
                blocks += Block.Paragraph(parseInline(accumulator.joinToString("\n")))
                accumulator.clear()
            }
        }

        val paragraph = mutableListOf<String>()
        while (i < lines.size) {
            val line = lines[i]

            if (line.trimStart().startsWith("```")) {
                flushParagraph(paragraph)
                val language = line.trimStart().removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[i])
                    i++
                }
                i++
                blocks += Block.CodeBlock(language, code.toString())
                continue
            }

            when {
                line.isBlank() -> { flushParagraph(paragraph); i++ }
                RULE.containsMatchIn(line) -> { flushParagraph(paragraph); blocks += Block.Rule; i++ }
                else -> {
                    val heading = HEADING.find(line)
                    val bullet = BULLET.matchEntire(line)
                    val ordered = ORDERED.matchEntire(line)
                    when {
                        heading != null -> {
                            flushParagraph(paragraph)
                            blocks += Block.Heading(
                                heading.groupValues[1].length,
                                parseInline(heading.groupValues[2])
                            )
                            i++
                        }
                        bullet != null -> {
                            flushParagraph(paragraph)
                            blocks += Block.ListItem(false, 0, parseInline(bullet.groupValues[1]))
                            i++
                        }
                        ordered != null -> {
                            flushParagraph(paragraph)
                            blocks += Block.ListItem(
                                true,
                                ordered.groupValues[1].toIntOrNull() ?: 1,
                                parseInline(ordered.groupValues[2])
                            )
                            i++
                        }
                        line.trimStart().startsWith(">") -> {
                            flushParagraph(paragraph)
                            val quote = StringBuilder()
                            while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                                if (quote.isNotEmpty()) quote.append('\n')
                                quote.append(lines[i].trimStart().removePrefix(">").trimStart())
                                i++
                            }
                            blocks += Block.Quote(parseInline(quote.toString()))
                        }
                        else -> {
                            paragraph += line
                            i++
                        }
                    }
                }
            }
        }
        flushParagraph(paragraph)
        return blocks
    }

    fun parseInline(text: String): List<Span> {
        if (text.isEmpty()) return listOf(Span(""))
        val spans = mutableListOf<Span>()
        var last = 0
        for (match in INLINE.findAll(text)) {
            if (match.range.first > last) {
                spans += Span(text.substring(last, match.range.first))
            }
            val g = match.groupValues
            when {
                g[2].isNotEmpty() -> spans += Span(g[2], bold = true)
                g[3].isNotEmpty() -> spans += Span(g[3], italic = true)
                g[4].isNotEmpty() -> spans += Span(g[4], code = true)
                g[5].isNotEmpty() -> spans += Span(g[5], strike = true)
                g[6].isNotEmpty() -> spans += Span(g[6], link = g[7])
                else -> spans += Span(match.value)
            }
            last = match.range.last + 1
        }
        if (last < text.length) spans += Span(text.substring(last))
        return spans.ifEmpty { listOf(Span("")) }
    }
}
