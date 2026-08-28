package com.mdyerapis.assistant.backendclient

import com.mdyerapis.assistant.core.model.ChatEvent
import com.mdyerapis.assistant.core.model.ChatMessage
import com.mdyerapis.assistant.core.model.ChatState
import com.mdyerapis.assistant.core.model.ToolCall
import com.mdyerapis.assistant.core.model.ToolCallStatus

/**
 * Pure fold: (ChatState, ChatEvent) -> ChatState.
 *
 * No coroutines, no IO — fully unit-testable.
 * Must handle a delta arriving before any "message started" marker by
 * synthesizing the message (per docs/CONTRACT.md ordering guarantees).
 */
object ChatReducer {
    fun reduce(state: ChatState, event: ChatEvent): ChatState = when (event) {
        is ChatEvent.Delta -> {
            val convId = event.conversationId.ifEmpty { state.conversationId }
            if (state.currentMessageId == null) {
                // Delta before any marker — synthesize the message (contract allows this)
                state.copy(
                    conversationId = convId,
                    currentMessageId = "synthesized",
                    currentContent = state.currentContent + event.content,
                )
            } else {
                state.copy(
                    conversationId = convId,
                    currentContent = state.currentContent + event.content,
                )
            }
        }

        is ChatEvent.ToolCallStarted -> {
            val convId = event.conversationId.ifEmpty { state.conversationId }
            state.copy(
                conversationId = convId,
                activeToolCalls = state.activeToolCalls + ToolCall(
                    id = event.id,
                    name = event.name,
                    argumentsJson = event.argsJson,
                    status = ToolCallStatus.Started,
                ),
            )
        }

        is ChatEvent.ToolCallProgress -> {
            val convId = event.conversationId.ifEmpty { state.conversationId }
            state.copy(
                conversationId = convId,
                activeToolCalls = state.activeToolCalls.map {
                    if (it.id == event.id) it.copy(status = ToolCallStatus.Progress)
                    else it
                },
            )
        }

        is ChatEvent.ToolCallFinished -> {
            val convId = event.conversationId.ifEmpty { state.conversationId }
            state.copy(
                conversationId = convId,
                activeToolCalls = state.activeToolCalls.map {
                    if (it.id == event.id) it.copy(
                        status = if (event.ok) ToolCallStatus.Finished else ToolCallStatus.Error,
                    )
                    else it
                },
            )
        }

        is ChatEvent.MessageCompleted -> {
            val convId = event.conversationId.ifEmpty { state.conversationId }
            val msg = ChatMessage(
                id = state.currentMessageId ?: event.messageId.ifEmpty { "unknown" },
                role = "assistant",
                content = state.currentContent,
            )
            state.copy(
                conversationId = convId,
                messages = state.messages + msg,
                currentMessageId = null,
                currentContent = "",
                isLoading = false,
            )
        }

        is ChatEvent.Error -> {
            val convId = event.conversationId.ifEmpty { state.conversationId }
            state.copy(
                conversationId = convId,
                error = event.message,
                isLoading = false,
            )
        }

        is ChatEvent.Unknown -> state
    }
}
