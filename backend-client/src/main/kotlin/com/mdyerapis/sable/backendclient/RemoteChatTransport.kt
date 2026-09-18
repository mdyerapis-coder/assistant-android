package com.mdyerapis.sable.backendclient

import com.mdyerapis.sable.core.model.ChatEvent
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Cloud path: POST /v1/chat and unfold the SSE body into [ChatEvent]s.
 * Tool execution, Google, reminders, and SMS all happen server-side
 * inside that turn — this class never talks to OpenAI or Google itself.
 */
class RemoteChatTransport(
    private val client: ChatApiClient,
) : ChatTransport {
    override val capabilities: TransportCapabilities = TransportCapabilities.CLOUD

    override fun stream(request: ChatTurnRequest): Flow<ChatEvent> = flow {
        val convId = request.conversationId.orEmpty()
        val response = try {
            client.streamChat(
                message = request.message,
                conversationId = request.conversationId,
                model = request.model,
                timezone = request.timezone,
            )
        } catch (e: IOException) {
            emit(
                ChatEvent.Error(
                    conversationId = convId,
                    message = "Couldn't reach the server — check it is running and reachable.",
                    retryable = true,
                ),
            )
            return@flow
        }

        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            emit(
                ChatEvent.Error(
                    conversationId = convId,
                    message = "Server error: $code",
                    retryable = code in 401..503,
                ),
            )
            return@flow
        }

        SseFrameCodec.events(response).collect { emit(it) }
    }.flowOn(Dispatchers.IO)
}
