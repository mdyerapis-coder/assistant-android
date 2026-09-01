package com.mdyerapis.sable.core.database.chat

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val preview: String,
    val modelId: String?,
    val mode: String,
    val serverConversationId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
