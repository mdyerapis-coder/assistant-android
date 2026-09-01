package com.mdyerapis.sable.core.database.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

data class ConversationSummary(
    val id: String,
    val title: String,
    val preview: String,
    val modelId: String?,
    val mode: String,
    val serverConversationId: String?,
    val updatedAt: Long,
)

data class StoredMessage(
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val toolCallId: String?,
    val toolName: String?,
    val toolArgsJson: String?,
    val toolResult: String?,
    val isError: Boolean,
    val createdAt: Long,
)

class ConversationRepository(
    private val db: ChatDatabase,
) : ConversationStore {
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()

    override val conversations: Flow<List<ConversationSummary>> =
        conversationDao.observeAll().map { list ->
            list.map { entity ->
                ConversationSummary(
                    id = entity.id,
                    title = entity.title,
                    preview = entity.preview,
                    modelId = entity.modelId,
                    mode = entity.mode,
                    serverConversationId = entity.serverConversationId,
                    updatedAt = entity.updatedAt,
                )
            }
        }

    override suspend fun createConversation(
        modelId: String?,
        mode: String,
        title: String,
        serverConversationId: String?,
    ): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        conversationDao.upsert(
            ConversationEntity(
                id = id,
                title = title,
                preview = "",
                modelId = modelId,
                mode = mode,
                serverConversationId = serverConversationId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        id
    }

    override suspend fun setServerConversationId(id: String, serverConversationId: String?) =
        withContext(Dispatchers.IO) {
            conversationDao.setServerId(id, serverConversationId)
        }

    suspend fun renameConversation(id: String, title: String) = withContext(Dispatchers.IO) {
        conversationDao.rename(id, title)
    }

    override suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        conversationDao.delete(id)
        messageDao.deleteForConversation(id)
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        conversationDao.clear()
        messageDao.clear()
    }

    override fun messagesFor(conversationId: String): Flow<List<StoredMessage>> =
        messageDao.observeForConversation(conversationId).map { list ->
            list.map { entity ->
                StoredMessage(
                    id = entity.id,
                    conversationId = entity.conversationId,
                    role = entity.role,
                    content = entity.content,
                    toolCallId = entity.toolCallId,
                    toolName = entity.toolName,
                    toolArgsJson = entity.toolArgsJson,
                    toolResult = entity.toolResult,
                    isError = entity.isError == 1,
                    createdAt = entity.createdAt,
                )
            }
        }

    override suspend fun appendMessage(
        conversationId: String,
        messageId: String,
        role: String,
        content: String,
        toolCallId: String?,
        toolName: String?,
        toolArgsJson: String?,
        toolResult: String?,
        isError: Boolean,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        messageDao.upsert(
            MessageEntity(
                id = messageId,
                conversationId = conversationId,
                role = role,
                content = content,
                toolCallId = toolCallId,
                toolName = toolName,
                toolArgsJson = toolArgsJson,
                toolResult = toolResult,
                isError = if (isError) 1 else 0,
                createdAt = now,
            ),
        )
        conversationDao.touch(
            id = conversationId,
            preview = content.take(80),
            updatedAt = now,
        )
    }

    suspend fun conversationExists(id: String): Boolean = withContext(Dispatchers.IO) {
        conversationDao.get(id) != null
    }

    override suspend fun cacheServerThread(
        serverConversationId: String,
        title: String,
        preview: String,
        createdAtMs: Long,
        updatedAtMs: Long,
    ): String = withContext(Dispatchers.IO) {
        // Merge by serverConversationId so a thread that already has a
        // locally-created row (created before its first send assigned the
        // server id) keeps that row's id — active UI references stay valid.
        val existing = conversationDao.getByServerId(serverConversationId)
        val localId = existing?.id ?: serverConversationId
        conversationDao.upsert(
            ConversationEntity(
                id = localId,
                title = title,
                preview = preview,
                modelId = existing?.modelId,
                mode = existing?.mode ?: "cloud",
                serverConversationId = serverConversationId,
                createdAt = existing?.createdAt ?: createdAtMs,
                updatedAt = updatedAtMs,
            )
        )
        localId
    }

    override suspend fun replaceMessages(
        conversationId: String,
        messages: List<StoredMessage>,
    ) = withContext(Dispatchers.IO) {
        messageDao.deleteForConversation(conversationId)
        messages.forEach { stored ->
            messageDao.upsert(
                MessageEntity(
                    id = stored.id,
                    conversationId = stored.conversationId,
                    role = stored.role,
                    content = stored.content,
                    toolCallId = stored.toolCallId,
                    toolName = stored.toolName,
                    toolArgsJson = stored.toolArgsJson,
                    toolResult = stored.toolResult,
                    isError = if (stored.isError) 1 else 0,
                    createdAt = stored.createdAt,
                )
            )
        }
    }
}
