package com.mdyerapis.assistant.core.database.chat

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val toolCallId: String?,
    val toolName: String?,
    val toolArgsJson: String?,
    val toolResult: String?,
    val isError: Int,
    val createdAt: Long,
)
