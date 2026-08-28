package com.mdyerapis.assistant.feature.chat

import com.mdyerapis.assistant.core.model.ChatState

data class ChatUiState(
    val chatState: ChatState = ChatState(),
    val isGoogleConnected: Boolean = false,
)
