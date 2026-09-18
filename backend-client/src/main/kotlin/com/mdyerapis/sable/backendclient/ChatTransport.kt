package com.mdyerapis.sable.backendclient

import com.mdyerapis.sable.core.model.ChatEvent
import com.mdyerapis.sable.core.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * One turn of chat, independent of whether tokens come from the remote
 * FastAPI SSE loop or on-device MediaPipe.
 *
 * [history] is for on-device only — the backend already has the thread
 * keyed by [conversationId]. Remote transports ignore it.
 */
data class ChatTurnRequest(
    val message: String,
    val conversationId: String? = null,
    val model: String? = null,
    val timezone: String? = java.util.TimeZone.getDefault().id,
    val history: List<ChatMessage> = emptyList(),
)

/**
 * What this transport can actually do. On-device MediaPipe is a reduced
 * assistant: chat tokens only. Tool/Google/reminder/SMS work stays on
 * the hosted backend (ADR-013 O1/P1/P2) until those slices land here.
 */
data class TransportCapabilities(
    val llm: Boolean,
    val tools: Boolean,
    val google: Boolean,
    val reminders: Boolean,
    val sms: Boolean,
    val offline: Boolean,
) {
    companion object {
        val CLOUD = TransportCapabilities(
            llm = true,
            tools = true,
            google = true,
            reminders = true,
            sms = true,
            offline = false,
        )
        val ON_DEVICE = TransportCapabilities(
            llm = true,
            tools = false,
            google = false,
            reminders = false,
            sms = false,
            offline = true,
        )
    }
}

/**
 * Shared seam for [ChatViewModel]: both the remote SSE client and the
 * local MediaPipe path emit [ChatEvent]s so [ChatReducer] stays the
 * single fold.
 */
interface ChatTransport {
    val capabilities: TransportCapabilities
    fun stream(request: ChatTurnRequest): Flow<ChatEvent>
}
