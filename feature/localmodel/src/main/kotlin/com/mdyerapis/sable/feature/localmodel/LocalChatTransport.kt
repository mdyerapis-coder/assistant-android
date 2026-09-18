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
 * On-device path: local reminder/automation intents (P1), in-process SMS
 * (P2), hybrid Google via the O1 OAuth relay, then MediaPipe tokens —
 * all mapped onto [ChatEvent]s. Reminder/automation/SMS turns do not
 * need downloaded weights. Calendar / Gmail matching turns POST
 * `/v1/chat` to the relay; they never see a refresh token or
 * `client_secret`.
 */
@Singleton
class LocalChatTransport @Inject constructor(
    private val inference: LlmInferenceService,
    private val models: LocalModelRepository,
    private val reminders: LocalReminderGateway,
    private val google: LocalGoogleGateway,
    private val sms: LocalSmsGateway,
    private val automations: LocalAutomationGateway,
) : ChatTransport {
    constructor(
        inference: LlmInferenceService,
        models: LocalModelRepository,
    ) : this(
        inference,
        models,
        NoOpLocalReminderGateway,
        NoOpLocalGoogleGateway,
        NoOpLocalSmsGateway,
        NoOpLocalAutomationGateway,
    )

    constructor(
        inference: LlmInferenceService,
        models: LocalModelRepository,
        reminders: LocalReminderGateway,
    ) : this(
        inference,
        models,
        reminders,
        NoOpLocalGoogleGateway,
        NoOpLocalSmsGateway,
        NoOpLocalAutomationGateway,
    )

    constructor(
        inference: LlmInferenceService,
        models: LocalModelRepository,
        reminders: LocalReminderGateway,
        google: LocalGoogleGateway,
    ) : this(
        inference,
        models,
        reminders,
        google,
        NoOpLocalSmsGateway,
        NoOpLocalAutomationGateway,
    )

    constructor(
        inference: LlmInferenceService,
        models: LocalModelRepository,
        reminders: LocalReminderGateway,
        google: LocalGoogleGateway,
        sms: LocalSmsGateway,
    ) : this(
        inference,
        models,
        reminders,
        google,
        sms,
        NoOpLocalAutomationGateway,
    )

    override val capabilities: TransportCapabilities = TransportCapabilities.ON_DEVICE

    override fun stream(request: ChatTurnRequest): Flow<ChatEvent> = channelFlow {
        val convId = request.conversationId?.takeIf { it.isNotBlank() } ?: "local"
        val intercepted = try {
            automations.handle(request.message, convId)
                ?: reminders.handle(request.message, convId)
                ?: sms.handle(request.message, convId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            listOf(
                ChatEvent.Error(
                    conversationId = convId,
                    message = e.message ?: "On-device tool failed",
                    retryable = true,
                ),
            )
        }
        if (intercepted != null) {
            intercepted.forEach { send(it) }
            return@channelFlow
        }

        val googleFlow = try {
            google.handle(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            kotlinx.coroutines.flow.flow {
                emit(
                    ChatEvent.Error(
                        conversationId = convId,
                        message = e.message ?: "O1 Google relay failed",
                        retryable = true,
                    ),
                )
            }
        }
        if (googleFlow != null) {
            googleFlow.collect { send(it) }
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
