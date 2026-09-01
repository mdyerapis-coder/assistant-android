package com.mdyerapis.sable.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Renders assistant text as markdown blocks: fenced code, lists, headings, links, inline bold/code. */
@Composable
fun MessageContent(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
) {
    val blocks = remember(text) { parseMessageBlocks(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MessageBlock.CodeBlock -> CodeBlockCard(block)
                is MessageBlock.Heading -> Text(
                    text = inlineStyled(block.text),
                    style = headingStyle(block.level),
                    color = color,
                )
                is MessageBlock.UnorderedList -> Column {
                    block.items.forEachIndexed { index, item ->
                        ListRow(marker = "•", markerStyle = MarkerStyle.Bullet, index = index) {
                            Text(inlineStyled(item), color = color, style = style)
                        }
                    }
                }
                is MessageBlock.OrderedList -> Column {
                    block.items.forEachIndexed { index, item ->
                        ListRow(marker = "${index + 1}.", markerStyle = MarkerStyle.Number, index = index) {
                            Text(inlineStyled(item), color = color, style = style)
                        }
                    }
                }
                is MessageBlock.Paragraph -> Text(
                    text = inlineStyled(block.text),
                    color = color,
                    style = style,
                )
            }
        }
    }
}

private enum class MarkerStyle { Bullet, Number }

@Composable
private fun ListRow(
    marker: String,
    markerStyle: MarkerStyle,
    index: Int,
    content: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        val style = if (markerStyle == MarkerStyle.Number) {
            MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        } else {
            MaterialTheme.typography.bodyMedium
        }
        Text(
            text = marker,
            style = style,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp),
        )
        content()
    }
}

@Composable
private fun CodeBlockCard(block: MessageBlock.CodeBlock) {
    val clipboard = LocalClipboardManager.current
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                block.language?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                } ?: androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(block.content)) },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            androidx.compose.foundation.layout.Spacer(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
            Text(
                text = block.content,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(12.dp)
                    .horizontalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle = when (level) {
    1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
    2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
}

/** Applies bold/code/link spans; safe to call inside a composed text node. */
@Composable
private fun inlineStyled(raw: String): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(raw, codeBackground, linkColor) {
        buildAnnotatedString {
            val spans = parseInlineSpans(raw)
            var cursor = 0
            for (span in spans) {
                if (span.range.first > cursor) append(raw.substring(cursor, span.range.first))
                val inner = innerText(raw, span)
                when (span.kind) {
                    InlineSpan.Kind.BOLD ->
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inner) }

                    InlineSpan.Kind.CODE ->
                        withStyle(
                            SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
                        ) { append(inner) }

                    InlineSpan.Kind.LINK ->
                        withLink(LinkAnnotation.Url(span.target ?: "")) {
                            withStyle(
                                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                            ) { append(inner) }
                        }
                }
                cursor = span.range.last + 1
            }
            if (cursor < raw.length) append(raw.substring(cursor))
        }
    }
}
