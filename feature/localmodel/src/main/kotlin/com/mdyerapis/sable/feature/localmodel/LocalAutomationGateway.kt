package com.mdyerapis.sable.feature.localmodel

import com.mdyerapis.sable.core.database.automation.AutomationScheduler
import com.mdyerapis.sable.core.database.automation.AutomationStore
import com.mdyerapis.sable.core.database.automation.LocalAutomation
import com.mdyerapis.sable.core.model.AutomationIntent
import com.mdyerapis.sable.core.model.ChatEvent
import com.mdyerapis.sable.core.model.LocalAutomationParser
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns parsed automation intents into the same ChatEvent stream the SSE
 * tool loop uses. Create/list/cancel hit local SQLite + [AutomationScheduler]
 * (WorkManager on device). Cloud mode still uses backend croniter + FCM.
 */
interface LocalAutomationGateway {
    suspend fun handle(message: String, conversationId: String): List<ChatEvent>?
}

object NoOpLocalAutomationGateway : LocalAutomationGateway {
    override suspend fun handle(message: String, conversationId: String): List<ChatEvent>? = null
}

@Singleton
class DefaultLocalAutomationGateway @Inject constructor(
    private val store: AutomationStore,
    private val scheduler: AutomationScheduler,
) : LocalAutomationGateway {
    override suspend fun handle(message: String, conversationId: String): List<ChatEvent>? {
        return when (val intent = LocalAutomationParser.parse(message)) {
            AutomationIntent.Unrelated -> null
            is AutomationIntent.CreateUnsupported -> events(
                conversationId,
                toolName = "create_automation",
                argsJson = """{"message":${jsonString(message)}}""",
                ok = false,
                summary = "Unsupported on-device",
                spoken = intent.reason,
            )
            AutomationIntent.List -> listEvents(conversationId)
            is AutomationIntent.Cancel -> cancelEvents(conversationId, intent.query)
            is AutomationIntent.Create -> createEvents(conversationId, intent)
        }
    }

    private suspend fun createEvents(
        conversationId: String,
        intent: AutomationIntent.Create,
    ): List<ChatEvent> {
        val now = System.currentTimeMillis()
        val automation = LocalAutomation(
            id = UUID.randomUUID().toString(),
            name = intent.name,
            expression = intent.schedule.expression,
            actionText = intent.actionText,
            kind = intent.schedule.kind.name,
            intervalMillis = intent.schedule.intervalMillis,
            hour = intent.schedule.hour,
            minute = intent.schedule.minute,
            daysOfWeek = intent.schedule.daysOfWeekCsv(),
            createdAtMillis = now,
            enabled = true,
        )
        store.insert(automation)
        scheduler.schedule(automation)
        return events(
            conversationId,
            toolName = "create_automation",
            argsJson = """{"name":${jsonString(intent.name)},"expression":${jsonString(intent.schedule.expression)},"action":${jsonString(intent.actionText)}}""",
            ok = true,
            summary = "Repeats ${intent.schedule.expression}",
            spoken = "I'll notify you to ${intent.actionText} ${intent.schedule.expression}. " +
                "This is on-device (WorkManager + a local notification) — no cloud croniter or FCM. " +
                "Calendar/Gmail/SMS automations still need Cloud Assistant.",
        )
    }

    private suspend fun listEvents(conversationId: String): List<ChatEvent> {
        val enabled = store.listEnabled()
        val spoken = if (enabled.isEmpty()) {
            "You have no on-device automations. Try “every day at 9am remind me to drink water”. " +
                "Cloud automations (croniter + FCM) stay on Cloud Assistant."
        } else {
            enabled.joinToString(separator = "\n", prefix = "On-device automations:\n") { row ->
                "• ${row.name} — ${row.expression} (${row.id.take(8)})"
            }
        }
        return events(
            conversationId,
            toolName = "list_automations",
            argsJson = "{}",
            ok = true,
            summary = "${enabled.size} enabled",
            spoken = spoken,
        )
    }

    private suspend fun cancelEvents(conversationId: String, query: String): List<ChatEvent> {
        val enabled = store.listEnabled()
        val matches = enabled.filter { row ->
            row.id.startsWith(query, ignoreCase = true) ||
                row.id.take(8).equals(query, ignoreCase = true) ||
                row.name.contains(query, ignoreCase = true) ||
                row.actionText.contains(query, ignoreCase = true)
        }
        if (matches.isEmpty()) {
            return events(
                conversationId,
                toolName = "delete_automation",
                argsJson = """{"query":${jsonString(query)}}""",
                ok = false,
                summary = "Not found",
                spoken = "I couldn't find an on-device automation matching “$query”. " +
                    "Say “what are my automations” to list them. Cloud automations are not listed here.",
            )
        }
        if (matches.size > 1) {
            val options = matches.joinToString { "${it.name} (${it.id.take(8)})" }
            return events(
                conversationId,
                toolName = "delete_automation",
                argsJson = """{"query":${jsonString(query)}}""",
                ok = false,
                summary = "Ambiguous",
                spoken = "Several automations match. Be more specific: $options",
            )
        }
        val target = matches.first()
        store.markDisabled(target.id)
        scheduler.cancel(target.id)
        return events(
            conversationId,
            toolName = "delete_automation",
            argsJson = """{"id":${jsonString(target.id)}}""",
            ok = true,
            summary = "Cancelled",
            spoken = "Cancelled “${target.name}” (${target.expression}).",
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
                messageId = "local-automation-${System.currentTimeMillis()}",
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
