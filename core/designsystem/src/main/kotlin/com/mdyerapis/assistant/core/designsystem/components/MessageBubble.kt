package com.mdyerapis.assistant.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
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

    // Assistant: plain text on background, no bubble
    // User: subtle bubble, right-aligned, asymmetric radius
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val displayText = if (isUser) {
        // User message
        content
    } else {
        // Assistant message: plain text, no bubble
        content
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = alignment
    ) {
        if (isUser) {
            // User bubble
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = displayText,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            // Assistant: plain text on background, no bubble
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = displayText,
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