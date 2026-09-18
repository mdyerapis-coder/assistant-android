package com.mdyerapis.sable.feature.localmodel

import com.mdyerapis.sable.core.model.ChatMessage

/**
 * Builds a single prompt string for MediaPipe. Calendar, Gmail, and SMS
 * stay on the hosted backend. On-device reminders are handled before
 * this prompt is built (P1) — do not invent extra ones.
 */
object LocalPromptBuilder {
    const val SYSTEM_PREAMBLE: String =
        "You are Sable, a personal assistant running entirely on this phone. " +
            "You can chat, draft text, and reason about what the user types. " +
            "On-device reminders are created by the app itself when the user says " +
            "“remind me …” with a time — you will not see those turns. " +
            "You cannot access the internet, read Gmail or Calendar, or send SMS. " +
            "If the user asks for calendar, email, or SMS, say so plainly and tell " +
            "them to switch to Cloud Assistant. Do not invent calendar events, emails, " +
            "or extra reminder confirmations."

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
