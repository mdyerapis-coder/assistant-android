package com.mdyerapis.sable.feature.localmodel

import com.mdyerapis.sable.core.model.ChatEvent
import com.mdyerapis.sable.core.model.DeviceSmsMessage
import com.mdyerapis.sable.core.model.LocalSmsParser
import com.mdyerapis.sable.core.model.SmsIntent
import com.mdyerapis.sable.core.model.SmsOperations
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns parsed SMS intents into the same ChatEvent stream the SSE tool
 * loop uses. Send/read hit [SmsOperations] (Android APIs on device).
 * No FCM hop and no FastAPI `/v1/sms/results` on this path.
 */
interface LocalSmsGateway {
    suspend fun handle(message: String, conversationId: String): List<ChatEvent>?
}

object NoOpLocalSmsGateway : LocalSmsGateway {
    override suspend fun handle(message: String, conversationId: String): List<ChatEvent>? = null
}

@Singleton
class DefaultLocalSmsGateway @Inject constructor(
    private val sms: SmsOperations,
) : LocalSmsGateway {
    override suspend fun handle(message: String, conversationId: String): List<ChatEvent>? {
        return when (val intent = LocalSmsParser.parse(message)) {
            SmsIntent.Unrelated -> null
            is SmsIntent.SendNeedsPhone -> events(
                conversationId,
                toolName = "send_sms",
                argsJson = """{"message":${jsonString(intent.message)}}""",
                ok = false,
                summary = "Need a phone number",
                spoken = "What number should I text" +
                    if (intent.message.isBlank()) "?" else " “${intent.message}” to? Include the digits.",
            )
            is SmsIntent.SendNeedsMessage -> events(
                conversationId,
                toolName = "send_sms",
                argsJson = """{"phone":${jsonString(intent.phone)}}""",
                ok = false,
                summary = "Need a message",
                spoken = "What should I text ${intent.phone}? Try “text ${intent.phone} running late”.",
            )
            is SmsIntent.Send -> sendEvents(conversationId, intent)
            is SmsIntent.Read -> readEvents(conversationId, intent)
        }
    }

    private suspend fun sendEvents(
        conversationId: String,
        intent: SmsIntent.Send,
    ): List<ChatEvent> {
        if (!sms.hasSendPermission()) {
            return events(
                conversationId,
                toolName = "send_sms",
                argsJson = """{"phone":${jsonString(intent.phone)},"message":${jsonString(intent.message)}}""",
                ok = false,
                summary = "SMS permission needed",
                spoken = "I need SEND_SMS permission to text ${intent.phone} from this phone. " +
                    "Allow it in the dialog — this is in-process Android SMS, not the cloud FCM relay.",
            )
        }
        return try {
            sms.send(intent.phone, intent.message)
            events(
                conversationId,
                toolName = "send_sms",
                argsJson = """{"phone":${jsonString(intent.phone)},"message":${jsonString(intent.message)}}""",
                ok = true,
                summary = "Sent to ${intent.phone}",
                spoken = "Sent to ${intent.phone}: “${intent.message}”. " +
                    "This went out through this phone’s SMS radio — no FastAPI hop, no FCM.",
            )
        } catch (e: Exception) {
            events(
                conversationId,
                toolName = "send_sms",
                argsJson = """{"phone":${jsonString(intent.phone)}}""",
                ok = false,
                summary = "Send failed",
                spoken = "I couldn't send that SMS: ${e.message ?: "unknown error"}.",
            )
        }
    }

    private suspend fun readEvents(
        conversationId: String,
        intent: SmsIntent.Read,
    ): List<ChatEvent> {
        if (!sms.hasReadPermission()) {
            return events(
                conversationId,
                toolName = "read_sms",
                argsJson = """{"limit":${intent.limit}}""",
                ok = false,
                summary = "SMS permission needed",
                spoken = "I need READ_SMS permission to look at this phone’s inbox. " +
                    "Allow it in the dialog — nothing is uploaded; this stays on-device.",
            )
        }
        return try {
            val messages = sms.readInbox(intent.phoneFilter, intent.limit)
            events(
                conversationId,
                toolName = "read_sms",
                argsJson = buildReadArgs(intent),
                ok = true,
                summary = "${messages.size} messages",
                spoken = formatInbox(messages, intent),
            )
        } catch (e: Exception) {
            events(
                conversationId,
                toolName = "read_sms",
                argsJson = buildReadArgs(intent),
                ok = false,
                summary = "Read failed",
                spoken = "I couldn't read SMS: ${e.message ?: "unknown error"}.",
            )
        }
    }

    private fun formatInbox(messages: List<DeviceSmsMessage>, intent: SmsIntent.Read): String {
        if (messages.isEmpty()) {
            val from = intent.phoneFilter?.let { " from $it" } ?: ""
            return "No SMS$from in the inbox."
        }
        val lines = messages.joinToString("\n") { msg ->
            "• ${msg.fromNumber} (${formatWhen(msg.receivedAtMillis)}): ${msg.body}"
        }
        return "Latest on-device SMS:\n$lines"
    }

    private fun buildReadArgs(intent: SmsIntent.Read): String = buildString {
        append("{")
        append("\"limit\":").append(intent.limit)
        intent.phoneFilter?.let {
            append(",\"phone\":").append(jsonString(it))
        }
        append("}")
    }

    private fun events(
        conversationId: String,
        toolName: String,
        argsJson: String,
        ok: Boolean,
        summary: String,
        spoken: String,
    ): List<ChatEvent> {
        val toolId = "local-$toolName-${System.currentTimeMillis()}"
        return listOf(
            ChatEvent.ToolCallStarted(
                conversationId = conversationId,
                id = toolId,
                name = toolName,
                argsJson = argsJson,
            ),
            ChatEvent.ToolCallFinished(
                conversationId = conversationId,
                id = toolId,
                ok = ok,
                summary = summary,
            ),
            ChatEvent.Delta(conversationId = conversationId, content = spoken),
            ChatEvent.MessageCompleted(
                conversationId = conversationId,
                messageId = "local-sms-${System.currentTimeMillis()}",
            ),
        )
    }

    private fun formatWhen(epochMs: Long): String =
        ISO.format(Date(epochMs))

    private fun jsonString(value: String): String =
        buildString {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    else -> append(ch)
                }
            }
            append('"')
        }

    companion object {
        private val ISO = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
    }
}
