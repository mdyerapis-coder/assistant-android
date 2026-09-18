package com.mdyerapis.sable.feature.localmodel

import com.mdyerapis.sable.core.model.ChatMessage

/**
 * Builds a single prompt string for MediaPipe. Calendar/Gmail matching
 * turns are intercepted for the O1 relay before this prompt is built;
 * on-device reminders and SMS likewise (P1/P2). Do not invent extra ones.
 */
object LocalPromptBuilder {
    const val SYSTEM_PREAMBLE: String =
        "You are Assistant, a personal assistant running entirely on this phone. " +
            "You can chat, draft text, and reason about what the user types. " +
            "On-device reminders, recurring automations, and SMS are handled by the app itself when the " +
            "user says “remind me …”, “every day at 9am …”, or “text <number> …” — you will not see those turns. " +
            "Calendar and Gmail matching turns are sent to the O1 OAuth relay; " +
            "you will not see those either. " +
            "If a leftover calendar or email question reaches you, say so plainly " +
            "and tell them to Connect Google / paste a bearer for the relay — " +
            "do not invent calendar events, emails, or extra reminder/SMS confirmations."

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
