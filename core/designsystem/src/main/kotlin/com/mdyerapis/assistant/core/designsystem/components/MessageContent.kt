package com.mdyerapis.assistant.core.designsystem.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

@Composable
fun MessageContent(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current
) {
    val annotated = buildAnnotatedString {
        var cursor = 0
        val boldRegex = Regex("""\*\*(.*?)\*\*""")
        val codeRegex = Regex("""`(.*?)`""")

        // Simple markdown parsing for bold and inline code
        val matches = (boldRegex.findAll(text) + codeRegex.findAll(text))
            .sortedBy { it.range.first }

        for (match in matches) {
            if (match.range.first < cursor) continue

            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }

            if (match.value.startsWith("**") && match.value.endsWith("**")) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(match.groupValues[1])
                }
            } else if (match.value.startsWith("`") && match.value.endsWith("`")) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                    )
                ) {
                    append(match.groupValues[1])
                }
            }
            cursor = match.range.last + 1
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }

    Text(
        text = annotated,
        modifier = modifier,
        color = color,
        style = style
    )
}
