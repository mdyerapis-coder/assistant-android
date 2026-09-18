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
 * assistant: chat tokens plus P1 local reminders/automations and P2
 * in-process SMS. Calendar/Gmail matching turns hitch a ride on the O1
 * OAuth relay (`google = true` hybrid).
 */
data class TransportCapabilities(
    val llm: Boolean,
    val tools: Boolean,
    val google: Boolean,
    val reminders: Boolean,
    val automations: Boolean,
    val sms: Boolean,
    val offline: Boolean,
) {
    companion object {
        val CLOUD = TransportCapabilities(
            llm = true,
            tools = true,
            google = true,
            reminders = true,
            automations = true,
            sms = true,
            offline = false,
        )
        val ON_DEVICE = TransportCapabilities(
            llm = true,
            tools = false,
            google = true,
            reminders = true,
            automations = true,
            sms = true,
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
