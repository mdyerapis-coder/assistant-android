package com.mdyerapis.sable.feature.localmodel

import com.mdyerapis.sable.core.model.ChatMessage

/**
 * Builds a single prompt string for MediaPipe. The on-device engine has
 * no tool loop, so the system preamble is explicit about what it cannot
 * do — calendar/email/reminders/SMS stay on the hosted backend.
 */
object LocalPromptBuilder {
    const val SYSTEM_PREAMBLE: String =
        "You are Sable, a personal assistant running entirely on this phone. " +
            "You can chat, draft text, and reason about what the user types. " +
            "You cannot call tools, access the internet, read Gmail or Calendar, " +
            "create reminders, or send SMS while on-device. " +
            "If the user asks you to do those things, say so plainly and tell them " +
            "to switch to Cloud Assistant. Do not invent calendar events, emails, " +
            "or reminder confirmations."

    private const val HISTORY_CAP = 20

    fun build(
        history: List<ChatMessage>,
        fallbackUserMessage: String,
    ): String {
        val transcript = history
            .filter { it.role == "user" || it.role == "assistant" }
            .filter { it.content.isNotBlank() }
            .takeLast(HISTORY_CAP)
        val lines = buildList {
            add(SYSTEM_PREAMBLE)
            if (transcript.isEmpty()) {
                add("User: $fallbackUserMessage")
            } else {
                transcript.forEach { msg ->
                    val role = if (msg.role == "user") "User" else "Assistant"
                    add("$role: ${msg.content}")
                }
            }
            add("Assistant:")
        }
        return lines.joinToString("\n")
    }
}
