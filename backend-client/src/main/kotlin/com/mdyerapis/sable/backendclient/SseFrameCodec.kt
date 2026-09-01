package com.mdyerapis.sable.backendclient

import com.mdyerapis.sable.core.model.ChatEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Response
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses SSE `data:` lines into [ChatEvent]s per docs/CONTRACT.md.
 *
 * Tolerant by design: unknown `type` values become [ChatEvent.Unknown],
 * missing fields default silently, and malformed JSON never throws —
 * a forward-compatible client is more valuable than a strict one.
 */
object SseFrameCodec {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(line: String): ChatEvent {
        if (line.startsWith("[DONE]")) return ChatEvent.MessageCompleted()
        if (!line.startsWith("data:")) return ChatEvent.Unknown()
        val payload = line.removePrefix("data:").trimStart()
        if (payload.isEmpty()) return ChatEvent.Unknown()
        if (payload == "[DONE]") return ChatEvent.MessageCompleted()
        return try {
            val obj = json.decodeFromString<JsonObject>(payload)
            val type = obj["type"]?.jsonPrimitive?.content ?: return ChatEvent.Unknown()
            val cid = obj["conversation_id"]?.jsonPrimitive?.content ?: ""
            when (type) {
                "delta" -> ChatEvent.Delta(
                    conversationId = cid,
                    content = obj["content"]?.jsonPrimitive?.content ?: "",
                )
                "tool_call_started" -> ChatEvent.ToolCallStarted(
                    conversationId = cid,
                    id = obj["id"]?.jsonPrimitive?.content ?: "",
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    argsJson = obj["args_json"]?.jsonPrimitive?.content ?: "",
                )
                "tool_call_progress" -> ChatEvent.ToolCallProgress(
                    conversationId = cid,
                    id = obj["id"]?.jsonPrimitive?.content ?: "",
                    note = obj["note"]?.jsonPrimitive?.content ?: "",
                )
                "tool_call_finished" -> ChatEvent.ToolCallFinished(
                    conversationId = cid,
                    id = obj["id"]?.jsonPrimitive?.content ?: "",
                    ok = obj["ok"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
                    summary = obj["summary"]?.jsonPrimitive?.content ?: "",
                )
                "message_completed" -> ChatEvent.MessageCompleted(
                    conversationId = cid,
                    messageId = obj["message_id"]?.jsonPrimitive?.content ?: "",
                )
                "error" -> ChatEvent.Error(
                    conversationId = cid,
                    message = obj["message"]?.jsonPrimitive?.content ?: "",
                    retryable = obj["retryable"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                )
                else -> ChatEvent.Unknown(conversationId = cid)
            }
        } catch (_: Exception) {
            ChatEvent.Unknown()
        }
    }

    /**
     * Streams parsed server events while keeping the blocking response body on
     * [Dispatchers.IO]. Downstream collectors stay on their own context.
     */
    fun events(response: Response): Flow<ChatEvent> = flow {
        response.use { streamedResponse ->
            val reader = streamedResponse.body?.source() ?: return@use
            while (!reader.exhausted()) {
                val line = reader.readUtf8Line() ?: break
                if (line.isNotBlank()) emit(parse(line))
            }
        }
    }.flowOn(Dispatchers.IO)
}
