package com.mdyerapis.sable.core.model

/**
 * Deterministic on-device SMS intents. MediaPipe is not asked to emit
 * tool JSON — phone numbers and bodies are too easy to invent. Unrelated
 * chat still goes to the LLM.
 */
sealed class SmsIntent {
    data class Send(val phone: String, val message: String) : SmsIntent()
    data class SendNeedsPhone(val message: String) : SmsIntent()
    data class SendNeedsMessage(val phone: String) : SmsIntent()
    data class Read(val phoneFilter: String?, val limit: Int) : SmsIntent()
    data object Unrelated : SmsIntent()
}

object LocalSmsParser {
    private val readRegex = Regex(
        """^(?:read|show|list|check|what(?:'s|s| are)|any)\s+(?:my\s+)?(?:last\s+(\d+)\s+)?(?:new\s+)?(?:texts?|sms|messages?)(?:\s+from\s+(.+))?\??$""",
        RegexOption.IGNORE_CASE,
    )
    private val readBareRegex = Regex(
        """^(?:texts?|sms|messages?)\??$""",
        RegexOption.IGNORE_CASE,
    )
    private val sendSayingRegex = Regex(
        """^(?:send\s+(?:a\s+|an\s+)?(?:text|sms|message)|text|sms|message)\s+(?:to\s+)?(.+?)\s+(?:saying|that|:)\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val textNumberRestRegex = Regex(
        """^(?:text|sms|message)\s+(\+?\d[\d\s\-().]{6,}\d)\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val sendToOnlyRegex = Regex(
        """^(?:send\s+(?:a\s+|an\s+)?(?:text|sms|message)|text|sms)\s+to\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val phoneRegex = Regex("""\+?\d[\d\s\-().]{6,}\d""")

    fun parse(raw: String): SmsIntent {
        val message = raw.trim().replace(Regex("\\s+"), " ")
        if (message.isEmpty()) return SmsIntent.Unrelated

        readRegex.matchEntire(message)?.let { match ->
            val limit = match.groupValues[1].toIntOrNull()?.coerceIn(1, 100) ?: 10
            val filterRaw = match.groupValues[2].trim().ifBlank { null }
            val filter = filterRaw?.let { extractPhone(it) ?: it }
            return SmsIntent.Read(phoneFilter = filter, limit = limit)
        }
        if (readBareRegex.matches(message)) {
            return SmsIntent.Read(phoneFilter = null, limit = 10)
        }

        sendSayingRegex.matchEntire(message)?.let { match ->
            val target = match.groupValues[1].trim()
            val body = match.groupValues[2].trim()
            if (body.isEmpty()) return SmsIntent.Unrelated
            val phone = extractPhone(target)
            return if (phone != null) {
                SmsIntent.Send(phone, body)
            } else {
                SmsIntent.SendNeedsPhone(body)
            }
        }
        textNumberRestRegex.matchEntire(message)?.let { match ->
            val phone = extractPhone(match.groupValues[1]) ?: return SmsIntent.Unrelated
            val body = match.groupValues[2].trim()
            if (body.isEmpty()) return SmsIntent.SendNeedsMessage(phone)
            return SmsIntent.Send(phone, body)
        }
        sendToOnlyRegex.matchEntire(message)?.let { match ->
            val target = match.groupValues[1].trim()
            val phone = extractPhone(target)
            return if (phone != null) {
                SmsIntent.SendNeedsMessage(phone)
            } else {
                SmsIntent.SendNeedsPhone("")
            }
        }
        return SmsIntent.Unrelated
    }

    fun extractPhone(raw: String): String? {
        val match = phoneRegex.find(raw.trim()) ?: return null
        val kept = match.value.filter { it.isDigit() || it == '+' }
        val digits = kept.count { it.isDigit() }
        if (digits < 8) return null
        return kept
    }
}
