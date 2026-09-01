package com.mdyerapis.sable.core.model

import kotlinx.serialization.Serializable

@Serializable
sealed class ChatEvent {
    abstract val conversationId: String

    @Serializable
    data class Delta(
        override val conversationId: String = "",
        val content: String = "",
    ) : ChatEvent()

    @Serializable
    data class ToolCallStarted(
        override val conversationId: String = "",
        val id: String = "",
        val name: String = "",
        val argsJson: String = "",
    ) : ChatEvent()

    @Serializable
    data class ToolCallProgress(
        override val conversationId: String = "",
        val id: String = "",
        val note: String = "",
    ) : ChatEvent()

    @Serializable
    data class ToolCallFinished(
        override val conversationId: String = "",
        val id: String = "",
        val ok: Boolean = true,
        val summary: String = "",
    ) : ChatEvent()

    @Serializable
    data class MessageCompleted(
        override val conversationId: String = "",
        val messageId: String = "",
    ) : ChatEvent()

    @Serializable
    data class Error(
        override val conversationId: String = "",
        val message: String = "",
        val retryable: Boolean = false,
    ) : ChatEvent()

    @Serializable
    data class Unknown(
        override val conversationId: String = "",
    ) : ChatEvent()
}
