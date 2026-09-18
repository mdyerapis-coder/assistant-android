package com.mdyerapis.sable.feature.localmodel

import com.mdyerapis.sable.backendclient.ChatTransport
import com.mdyerapis.sable.backendclient.ChatTurnRequest
import com.mdyerapis.sable.backendclient.TransportCapabilities
import com.mdyerapis.sable.core.model.ChatEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device path: MediaPipe tokens mapped onto the same [ChatEvent]
 * vocabulary as the remote SSE loop. Never emits tool_call_* — there
 * is no tool executor in-process (ADR-012 Option C, not B).
 */
@Singleton
class LocalChatTransport @Inject constructor(
    private val inference: LlmInferenceService,
    private val models: LocalModelRepository,
) : ChatTransport {
    override val capabilities: TransportCapabilities = TransportCapabilities.ON_DEVICE

    override fun stream(request: ChatTurnRequest): Flow<ChatEvent> = channelFlow {
        val convId = request.conversationId?.takeIf { it.isNotBlank() } ?: "local"
        if (models.state.value !is LocalModelState.Ready) {
            send(
                ChatEvent.Error(
                    conversationId = convId,
                    message = "Local model is not installed. Please download one from model settings.",
                    retryable = true,
                ),
            )
            return@channelFlow
        }

        val prompt = LocalPromptBuilder.build(
            history = request.history,
            fallbackUserMessage = request.message,
        )
        try {
            var emittedTokens = false
            val result = inference.generate(prompt) { partial ->
                if (partial.isNotEmpty()) {
                    emittedTokens = true
                    send(ChatEvent.Delta(conversationId = convId, content = partial))
                }
            }
            when {
                isInferenceError(result) && !emittedTokens -> send(
                    ChatEvent.Error(
                        conversationId = convId,
                        message = result,
                        retryable = true,
                    ),
                )
                !emittedTokens && result.isNotBlank() -> {
                    send(ChatEvent.Delta(conversationId = convId, content = result))
                    send(
                        ChatEvent.MessageCompleted(
                            conversationId = convId,
                            messageId = "local-${System.currentTimeMillis()}",
                        ),
                    )
                }
                else -> send(
                    ChatEvent.MessageCompleted(
                        conversationId = convId,
                        messageId = "local-${System.currentTimeMillis()}",
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            send(
                ChatEvent.Error(
                    conversationId = convId,
                    message = e.message ?: "Local inference failed",
                    retryable = true,
                ),
            )
        }
    }

    private fun isInferenceError(result: String): Boolean =
        result.startsWith("Error:") || result.startsWith("Error running")
}
