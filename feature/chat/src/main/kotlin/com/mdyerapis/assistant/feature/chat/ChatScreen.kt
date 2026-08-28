package com.mdyerapis.assistant.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdyerapis.assistant.core.designsystem.components.LoadingIndicator
import com.mdyerapis.assistant.core.designsystem.components.MessageBubble
import com.mdyerapis.assistant.core.designsystem.components.ToolCallChip

@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Refresh Google connection status when the screen is recomposed
    // (covers the case where the user returns from the OAuth Custom Tab).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.refreshGoogleStatus()
    }

    LaunchedEffect(uiState.chatState.messages.size, uiState.chatState.currentContent) {
        if (uiState.chatState.messages.isNotEmpty() || uiState.chatState.currentContent.isNotEmpty()) {
            listState.animateScrollToItem(
                uiState.chatState.messages.size + uiState.chatState.activeToolCalls.size
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Google account header row
        GoogleAccountRow(
            connected = uiState.isGoogleConnected,
            onConnect = viewModel::connectGoogle,
            onDisconnect = viewModel::disconnectGoogle,
        )

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(uiState.chatState.messages) { msg ->
                MessageBubble(content = msg.content)
            }

            if (uiState.chatState.currentContent.isNotEmpty()) {
                item {
                    MessageBubble(content = uiState.chatState.currentContent)
                }
            }

            items(uiState.chatState.activeToolCalls) { tc ->
                ToolCallChip(toolCall = tc)
            }

            if (uiState.chatState.isLoading && uiState.chatState.currentContent.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        LoadingIndicator()
                    }
                }
            }
        }

        uiState.chatState.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank() && !uiState.chatState.isLoading,
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun GoogleAccountRow(
    connected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (connected) Color(0xFF22C55E) else MaterialTheme.colorScheme.outline
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (connected) "Google connected" else "Google not connected",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = if (connected) onDisconnect else onConnect,
            ) {
                Text(if (connected) "Disconnect" else "Connect")
            }
        }
    }
}
