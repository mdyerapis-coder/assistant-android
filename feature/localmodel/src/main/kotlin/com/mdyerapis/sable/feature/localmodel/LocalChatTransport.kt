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
 * On-device path: local reminder intents (P1) plus MediaPipe tokens,
 * both mapped onto [ChatEvent]s. Reminder create/list/cancel does not
 * need downloaded weights. There is still no calendar/gmail/SMS loop.
 */
@Singleton
class LocalChatTransport @Inject constructor(
    private val inference: LlmInferenceService,
    private val models: LocalModelRepository,
    private val reminders: LocalReminderGateway,
) : ChatTransport {
    constructor(
        inference: LlmInferenceService,
        models: LocalModelRepository,
    ) : this(inference, models, NoOpLocalReminderGateway)

    override val capabilities: TransportCapabilities = TransportCapabilities.ON_DEVICE

    override fun stream(request: ChatTurnRequest): Flow<ChatEvent> = channelFlow {
        val convId = request.conversationId?.takeIf { it.isNotBlank() } ?: "local"
        val reminderEvents = try {
            reminders.handle(request.message, convId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            listOf(
                ChatEvent.Error(
                    conversationId = convId,
                    message = e.message ?: "On-device reminder failed",
                    retryable = true,
                ),
            )
        }
        if (reminderEvents != null) {
            reminderEvents.forEach { send(it) }
            return@channelFlow
        }

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
