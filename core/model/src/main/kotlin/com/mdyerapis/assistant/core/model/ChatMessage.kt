package com.mdyerapis.assistant.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String = "",
    val role: String = "",
    val content: String = "",
    val timestamp: Long = 0L,
)