package com.mdyerapis.sable.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mdyerapis.sable.core.designsystem.R
import com.mdyerapis.sable.core.model.ChatMessage

@Composable
fun MessageBubble(
    content: String,
    role: String = "assistant",
    modifier: Modifier = Modifier,
    timestamp: Long = 0L,
) {
    val isUser = role.equals("user", ignoreCase = true)
    val contentColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        if (isUser) {
            // User: ember-tinted panel bubble, right-aligned
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(0.85f)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = content,
                    color = contentColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            // Assistant: droid avatar + plain text on background
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Ember-glow droid avatar
                val glowBrush = Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        Color.Transparent
                    )
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .drawBehind {
                            drawCircle(
                                brush = glowBrush,
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.minDimension / 1.2f
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_droid_avatar),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = content,
                    color = contentColor,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 2.dp)
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
