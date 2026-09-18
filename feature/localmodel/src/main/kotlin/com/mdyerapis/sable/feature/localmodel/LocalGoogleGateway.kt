package com.mdyerapis.sable.feature.localmodel

import com.mdyerapis.sable.backendclient.ChatTransport
import com.mdyerapis.sable.backendclient.ChatTurnRequest
import com.mdyerapis.sable.core.model.ChatEvent
import com.mdyerapis.sable.core.model.GoogleIntent
import com.mdyerapis.sable.core.model.LocalGoogleParser
import com.mdyerapis.sable.core.security.BearerTokenRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * O1 hybrid Google: matching calendar/Gmail turns POST `/v1/chat` to the
 * OAuth relay so existing backend tools run with `get_google_credentials()`.
 * Refresh tokens and `client_secret` stay on the relay.
 */
interface LocalGoogleGateway {
    fun configureRelay(transport: ChatTransport?, host: String) {}
    fun handle(request: ChatTurnRequest): Flow<ChatEvent>?
}

object NoOpLocalGoogleGateway : LocalGoogleGateway {
    override fun handle(request: ChatTurnRequest): Flow<ChatEvent>? = null
}

@Singleton
class DefaultLocalGoogleGateway @Inject constructor(
    private val tokenRepository: BearerTokenRepository,
) : LocalGoogleGateway {
    @Volatile
    private var relayTransport: ChatTransport? = null

    @Volatile
    private var relayHost: String = BearerTokenRepository.DEFAULT_OAUTH_RELAY_URL

    @Volatile
    private var relayConversationId: String? = null

    override fun configureRelay(transport: ChatTransport?, host: String) {
        val cleaned = host.trim().trimEnd('/').ifBlank {
            BearerTokenRepository.DEFAULT_OAUTH_RELAY_URL
        }
        if (cleaned != relayHost) {
            relayConversationId = null
        }
        relayHost = cleaned
        relayTransport = transport
    }

    override fun handle(request: ChatTurnRequest): Flow<ChatEvent>? {
        val intent = LocalGoogleParser.parse(request.message)
        if (intent is GoogleIntent.Unrelated) return null
        val localConvId = request.conversationId?.takeIf { it.isNotBlank() } ?: "local"
        val toolName = when (intent) {
            GoogleIntent.Calendar -> "calendar"
            GoogleIntent.Gmail -> "gmail"
            GoogleIntent.Unrelated -> return null
        }
        val token = tokenRepository.getToken()
        val transport = relayTransport
        if (token.isNullOrBlank() || transport == null) {
            return flow {
                honestUnavailable(localConvId, toolName, request.message).forEach { emit(it) }
            }
        }
        val relayRequest = request.copy(
            // Never send on-device `local:` ids or MediaPipe model ids to the relay.
            conversationId = relayConversationId,
            model = null,
        )
        return transport.stream(relayRequest).map { event ->
            if (event.conversationId.isNotBlank() &&
                !event.conversationId.startsWith(LOCAL_CONVERSATION_PREFIX)
            ) {
                relayConversationId = event.conversationId
            }
            rewrite(event, localConvId)
        }
    }

    private fun honestUnavailable(
        conversationId: String,
        toolName: String,
        userMessage: String,
    ): List<ChatEvent> {
        val toolId = "local-o1-$toolName-${System.currentTimeMillis()}"
        val spoken = "I can't talk to Google Calendar or Gmail from this phone without the " +
            "O1 relay at $relayHost. Paste a bearer token (Settings → Connect cloud assistant) " +
            "even if chat stays on-device, then Connect Google. The Custom Tab opens " +
            "$relayHost/oauth/google/start; the phone never stores a Google client_secret " +
            "or refresh token. Chat still works here. I did not invent events for “$userMessage”."
        return listOf(
            ChatEvent.ToolCallStarted(
                conversationId = conversationId,
                id = toolId,
                name = toolName,
                argsJson = """{"relay":"$relayHost"}""",
            ),
            ChatEvent.ToolCallFinished(
                conversationId = conversationId,
                id = toolId,
                ok = false,
                summary = "O1 relay needed",
            ),
            ChatEvent.Delta(conversationId = conversationId, content = spoken),
            ChatEvent.MessageCompleted(
                conversationId = conversationId,
                messageId = "local-o1-${System.currentTimeMillis()}",
            ),
        )
    }

    private fun rewrite(event: ChatEvent, localConvId: String): ChatEvent = when (event) {
        is ChatEvent.Delta -> event.copy(conversationId = localConvId)
        is ChatEvent.ToolCallStarted -> event.copy(conversationId = localConvId)
        is ChatEvent.ToolCallProgress -> event.copy(conversationId = localConvId)
        is ChatEvent.ToolCallFinished -> event.copy(conversationId = localConvId)
        is ChatEvent.MessageCompleted -> event.copy(conversationId = localConvId)
        is ChatEvent.Error -> event.copy(
            conversationId = localConvId,
            message = rewriteError(event.message),
        )
        is ChatEvent.Unknown -> event.copy(conversationId = localConvId)
    }

    private fun rewriteError(message: String): String {
        val lower = message.lowercase()
        return when {
            "couldn't reach" in lower || "failed to connect" in lower ||
                "connection refused" in lower ->
                "Couldn't reach the Google OAuth relay at $relayHost — Calendar and Gmail " +
                    "need that host. Chat still works on-device. No fake events."
            "401" in message || "403" in message ->
                "The O1 relay at $relayHost rejected the bearer token. Re-paste it in Settings " +
                    "(chat can stay on-device). The phone never stores a Google client_secret."
            else -> message
        }
    }

    private companion object {
        const val LOCAL_CONVERSATION_PREFIX = "local:"
    }
}
