package com.axora.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.axora.app.util.MarkdownParser
import com.axora.app.util.MarkdownParser.Block
import com.axora.app.util.MarkdownParser.Span

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is Block.Paragraph -> MarkdownParagraph(block.segments, color)
                is Block.Heading -> Text(
                    text = buildSpanned(block.segments, color),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                is Block.CodeBlock -> CodeBlockCard(block)
                is Block.ListItem -> MarkdownListItem(block, color)
                is Block.Quote -> MarkdownQuote(block, color)
                Block.Rule -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun MarkdownParagraph(segments: List<Span>, color: Color) {
    Text(
        text = buildSpanned(segments, color, MaterialTheme.colorScheme.primary),
        style = MaterialTheme.typography.bodyLarge,
        color = color
    )
}

@Composable
private fun MarkdownListItem(block: Block.ListItem, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = if (block.ordered) "${block.index}." else "•",
            style = MaterialTheme.typography.bodyLarge,
            color = color
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = buildSpanned(block.segments, color, MaterialTheme.colorScheme.primary),
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MarkdownQuote(block: Block.Quote, color: Color) {
    val scheme = MaterialTheme.colorScheme
    Row {
        Box(
            modifier = Modifier
                .width(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(scheme.primary.copy(alpha = 0.5f))
                .heightIn(min = 18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = buildSpanned(block.segments, color, scheme.primary),
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CodeBlockCard(block: Block.CodeBlock) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerHighest,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (block.language.isNotBlank()) {
                Text(
                    text = block.language,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = block.code,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = scheme.onSurface,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
    }
}

private fun buildSpanned(segments: List<Span>, baseColor: Color, linkColor: Color = baseColor): AnnotatedString =
    buildAnnotatedString {
        for (span in segments) {
            pushStyle(
                SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    textDecoration = if (span.strike) TextDecoration.LineThrough else null,
                    fontFamily = if (span.code) FontFamily.Monospace else null,
                    color = when {
                        span.code || span.link != null -> linkColor
                        else -> baseColor
                    }
                )
            )
            append(span.text)
            pop()
        }
    }
