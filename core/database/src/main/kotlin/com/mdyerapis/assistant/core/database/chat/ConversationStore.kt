package com.mdyerapis.assistant.core.database.chat

import kotlinx.coroutines.flow.Flow

interface ConversationStore {
    val conversations: Flow<List<ConversationSummary>>

    suspend fun createConversation(
        modelId: String?,
        mode: String,
        title: String = "New conversation",
        serverConversationId: String? = null,
    ): String

    suspend fun setServerConversationId(id: String, serverConversationId: String?)

    suspend fun deleteConversation(id: String)

    suspend fun clearAll()

    fun messagesFor(conversationId: String): Flow<List<StoredMessage>>

    suspend fun appendMessage(
        conversationId: String,
        messageId: String,
        role: String,
        content: String,
        toolCallId: String? = null,
        toolName: String? = null,
        toolArgsJson: String? = null,
        toolResult: String? = null,
        isError: Boolean = false,
    )
}
