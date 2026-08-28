package com.mdyerapis.assistant.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mdyerapis.assistant.core.model.ChatMessage

@Composable
fun MessageBubble(
    content: String,
    role: String = "assistant",
    modifier: Modifier = Modifier,
    timestamp: Long = 0L,
) {
    val isUser = role.equals("user", ignoreCase = true)
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    val bubbleShape = if (isUser) {
        MaterialTheme.shapes.large.copy(
            topEnd = androidx.compose.foundation.shape.CornerSize(4.dp)
        )
    } else {
        MaterialTheme.shapes.large.copy(
            topStart = androidx.compose.foundation.shape.CornerSize(4.dp)
        )
    }

    val containerColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = alignment
    ) {
        Surface(
            shape = bubbleShape,
            color = containerColor,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                MessageContent(
                    text = content,
                    color = contentColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    MessageBubble(
        content = message.content,
        role = message.role,
        timestamp = message.timestamp,
        modifier = modifier
    )
}
