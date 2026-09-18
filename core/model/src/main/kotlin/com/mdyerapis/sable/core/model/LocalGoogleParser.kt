package com.mdyerapis.sable.core.model

/**
 * Deterministic on-device calendar / Gmail intents (ADR-013 O1).
 *
 * Matching turns are forwarded to the cloud OAuth relay's existing
 * `POST /v1/chat` Google tools — this parser never sees a refresh token
 * or `client_secret`. Unrelated chat still goes to MediaPipe.
 */
sealed class GoogleIntent {
    data object Calendar : GoogleIntent()
    data object Gmail : GoogleIntent()
    data object Unrelated : GoogleIntent()
}

object LocalGoogleParser {
    private val calendarRegexes = listOf(
        Regex(
            """^(?:what(?:'s|s| is)|show|list|check|open|read)\s+(?:my\s+)?(?:google\s+)?(?:calendar|schedule)\b.*""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """^(?:what(?:'s|s| is))\s+on\s+(?:my\s+)?(?:calendar|schedule)\b.*""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """^(?:am i free|any (?:meetings?|events?|appointments?))\b.*""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """^(?:add|create|schedule|book)\s+(?:an?\s+)?(?:calendar\s+)?(?:event|meeting|appointment)\b.*""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """^(?:put|add)\s+.+\s+on\s+(?:my\s+)?calendar\b.*""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """^calendar\s+(?:today|tomorrow|this week)\b.*""",
            RegexOption.IGNORE_CASE,
        ),
    )

    private val gmailRegexes = listOf(
        Regex(
            """^(?:check|read|open|show|list)\s+(?:my\s+)?(?:unread\s+)?(?:e-?mails?|gmail|inbox)\b.*""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """^(?:send|draft|write|compose)\s+(?:an?\s+)?(?:e-?mail|gmail)\b.*""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """^(?:any(?:\s+new)?|new)\s+(?:e-?mails?|mail|gmail)\??$""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """^(?:e-?mail|gmail)\s+.+$""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""^(?:inbox|gmail)\??$""", RegexOption.IGNORE_CASE),
    )

    fun parse(raw: String): GoogleIntent {
        val message = raw.trim().replace(Regex("\\s+"), " ")
        if (message.isEmpty()) return GoogleIntent.Unrelated
        if (calendarRegexes.any { it.matches(message) }) return GoogleIntent.Calendar
        if (gmailRegexes.any { it.matches(message) }) return GoogleIntent.Gmail
        return GoogleIntent.Unrelated
    }
}
