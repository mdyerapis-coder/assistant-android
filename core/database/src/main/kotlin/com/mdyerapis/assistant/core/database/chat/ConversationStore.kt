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

    /**
     * Upsert a conversation cached from the server (phase 08: server is
     * the source of truth). Merges by serverConversationId — keeps the
     * existing local row's id if one is already linked to this server
     * thread. Returns the local conversation id.
     */
    suspend fun cacheServerThread(
        serverConversationId: String,
        title: String,
        preview: String,
        createdAtMs: Long,
        updatedAtMs: Long,
    ): String

    /**
     * Replace a conversation's cached messages wholesale with the
     * server's renderable history (phase 08).
     */
    suspend fun replaceMessages(conversationId: String, messages: List<StoredMessage>)
}
