package com.mdyerapis.sable.feature.localmodel

import com.mdyerapis.sable.core.database.reminder.LocalReminder
import com.mdyerapis.sable.core.database.reminder.ReminderScheduler
import com.mdyerapis.sable.core.database.reminder.ReminderStore
import com.mdyerapis.sable.core.model.ChatEvent
import com.mdyerapis.sable.core.model.LocalReminderParser
import com.mdyerapis.sable.core.model.ReminderIntent
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns parsed reminder intents into the same ChatEvent stream the SSE
 * tool loop uses. Create/list/cancel hit local SQLite + [ReminderScheduler]
 * (WorkManager on device). Calendar/Gmail stay off this path.
 */
interface LocalReminderGateway {
    suspend fun handle(message: String, conversationId: String): List<ChatEvent>?
}

object NoOpLocalReminderGateway : LocalReminderGateway {
    override suspend fun handle(message: String, conversationId: String): List<ChatEvent>? = null
}

@Singleton
class DefaultLocalReminderGateway @Inject constructor(
    private val store: ReminderStore,
    private val scheduler: ReminderScheduler,
) : LocalReminderGateway {
    override suspend fun handle(message: String, conversationId: String): List<ChatEvent>? {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        return when (val intent = LocalReminderParser.parse(message, now, zone)) {
            ReminderIntent.Unrelated -> null
            is ReminderIntent.CreateNeedsWhen -> events(
                conversationId,
                toolName = "create_reminder",
                argsJson = """{"text":${jsonString(intent.text)}}""",
                ok = false,
                summary = "Need a due time",
                spoken = "When should I remind you to ${intent.text}? Try “in 30 minutes” or “tomorrow at 9am”.",
            )
            ReminderIntent.List -> listEvents(conversationId)
            is ReminderIntent.Cancel -> cancelEvents(conversationId, intent.query)
            is ReminderIntent.Create -> createEvents(conversationId, intent, now)
        }
    }

    private suspend fun createEvents(
        conversationId: String,
        intent: ReminderIntent.Create,
        now: Long,
    ): List<ChatEvent> {
        val reminder = LocalReminder(
            id = UUID.randomUUID().toString(),
            text = intent.text,
            dueAtMillis = intent.dueAtMillis,
            createdAtMillis = now,
            status = LocalReminder.STATUS_PENDING,
        )
        store.insert(reminder)
        scheduler.schedule(reminder)
        return events(
            conversationId,
            toolName = "create_reminder",
            argsJson = """{"text":${jsonString(intent.text)},"due_at":${intent.dueAtMillis}}""",
            ok = true,
            summary = "Scheduled for ${intent.dueDescription}",
            spoken = "I'll remind you to ${intent.text} at ${intent.dueDescription}. " +
                "This is on-device (WorkManager + a local notification) — no cloud scheduler or FCM.",
        )
    }

    private suspend fun listEvents(conversationId: String): List<ChatEvent> {
        val pending = store.listPending()
        val spoken = if (pending.isEmpty()) {
            "You have no pending on-device reminders."
        } else {
            pending.joinToString(separator = "\n", prefix = "Pending on-device reminders:\n") { reminder ->
                val whenAt = LocalReminderParser.describeDue(reminder.dueAtMillis, ZoneId.systemDefault())
                "• ${reminder.text} — $whenAt (${reminder.id.take(8)})"
            }
        }
        return events(
            conversationId,
            toolName = "list_reminders",
            argsJson = "{}",
            ok = true,
            summary = "${pending.size} pending",
            spoken = spoken,
        )
    }

    private suspend fun cancelEvents(conversationId: String, query: String): List<ChatEvent> {
        val pending = store.listPending()
        val matches = pending.filter { reminder ->
            reminder.id.startsWith(query, ignoreCase = true) ||
                reminder.id.take(8).equals(query, ignoreCase = true) ||
                reminder.text.contains(query, ignoreCase = true)
        }
        if (matches.isEmpty()) {
            return events(
                conversationId,
                toolName = "cancel_reminder",
                argsJson = """{"query":${jsonString(query)}}""",
                ok = false,
                summary = "Not found",
                spoken = "I couldn't find an on-device reminder matching “$query”. Say “what are my reminders” to list them.",
            )
        }
        if (matches.size > 1) {
            val options = matches.joinToString { "${it.text} (${it.id.take(8)})" }
            return events(
                conversationId,
                toolName = "cancel_reminder",
                argsJson = """{"query":${jsonString(query)}}""",
                ok = false,
                summary = "Ambiguous",
                spoken = "Several reminders match. Be more specific: $options",
            )
        }
        val target = matches.first()
        store.markCancelled(target.id)
        scheduler.cancel(target.id)
        return events(
            conversationId,
            toolName = "cancel_reminder",
            argsJson = """{"id":${jsonString(target.id)}}""",
            ok = true,
            summary = "Cancelled",
            spoken = "Cancelled “${target.text}”.",
        )
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
                messageId = "local-reminder-${System.currentTimeMillis()}",
            ),
        )
    }

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
}
