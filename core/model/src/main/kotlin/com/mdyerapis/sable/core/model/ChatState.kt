package com.mdyerapis.sable.core.model

data class ChatState(
    val conversationId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val currentMessageId: String? = null,
    val currentContent: String = "",
    val isLoading: Boolean = false,
    val activeToolCalls: List<ToolCall> = emptyList(),
    val error: String? = null,
)
