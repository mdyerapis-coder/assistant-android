package com.mdyerapis.sable.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mdyerapis.sable.core.model.ChatMessage

@Composable
fun MessageBubble(
    content: String,
    role: String = "assistant",
    modifier: Modifier = Modifier,
    timestamp: Long = 0L,
) {
    val isUser = role.equals("user", ignoreCase = true)
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    val contentColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = alignment
    ) {
        if (isUser) {
            // User bubble: 20dp rounded, surfaceContainerHigh
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = content,
                    color = contentColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            // Assistant: plain text on background, no bubble
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
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
