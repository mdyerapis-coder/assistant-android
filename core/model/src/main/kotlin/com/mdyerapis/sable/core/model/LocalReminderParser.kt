package com.mdyerapis.sable.core.model

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Deterministic on-device reminder intents. MediaPipe is not asked to
 * emit tool JSON — small local models hallucinate due times. Unrelated
 * chat still goes to the LLM.
 */
sealed class ReminderIntent {
    data class Create(
        val text: String,
        val dueAtMillis: Long,
        val dueDescription: String,
    ) : ReminderIntent()

    data class CreateNeedsWhen(val text: String) : ReminderIntent()

    data object List : ReminderIntent()

    data class Cancel(val query: String) : ReminderIntent()

    data object Unrelated : ReminderIntent()
}

object LocalReminderParser {
    private val listRegex = Regex(
        """^(?:what(?:'s| is| are)?|show|list|see|any)\s+(?:my\s+)?reminders\??$|^reminders\??$""",
        RegexOption.IGNORE_CASE,
    )
    private val cancelRegex = Regex(
        """^(?:cancel|delete|remove)\s+(?:the\s+)?reminder\s+(?:#\s*)?(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val inDurationRegex = Regex(
        """^remind\s+me(?:\s+to|\s+that)?\s+(.+?)\s+in\s+(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?)\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val inDurationBareRegex = Regex(
        """^remind\s+me\s+in\s+(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?)\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val setInDurationRegex = Regex(
        """^set\s+(?:a\s+)?reminder(?:\s+to|\s+for)?\s+(.+?)\s+in\s+(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?)\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val tomorrowAtRegex = Regex(
        """^remind\s+me(?:\s+to|\s+that)?\s+(.+?)\s+tomorrow(?:\s+at\s+(.+))?$""",
        RegexOption.IGNORE_CASE,
    )
    private val atTimeRegex = Regex(
        """^remind\s+me(?:\s+to|\s+that)?\s+(.+?)\s+(?:at|@)\s+(\d{1,2}(?::\d{2})?\s*(?:am|pm)?)\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val needsWhenRegex = Regex(
        """^(?:remind\s+me(?:\s+to|\s+that)\s+(.+)|set\s+(?:a\s+)?reminder(?:\s+to|\s+for)?\s+(.+))$""",
        RegexOption.IGNORE_CASE,
    )
    private val clockTimeRegex = Regex(
        """^\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(
        raw: String,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ReminderIntent {
        val message = raw.trim().replace(Regex("\\s+"), " ")
        if (message.isEmpty()) return ReminderIntent.Unrelated
        if (listRegex.matches(message)) return ReminderIntent.List
        cancelRegex.matchEntire(message)?.let { match ->
            return ReminderIntent.Cancel(match.groupValues[1].trim())
        }

        inDurationBareRegex.matchEntire(message)?.let { match ->
            val due = nowMillis + durationMillis(match.groupValues[1].toLong(), match.groupValues[2])
            return ReminderIntent.Create("Reminder", due, describeDue(due, zone))
        }
        inDurationRegex.matchEntire(message)?.let { match ->
            val due = nowMillis + durationMillis(match.groupValues[2].toLong(), match.groupValues[3])
            return ReminderIntent.Create(cleanTask(match.groupValues[1]), due, describeDue(due, zone))
        }
        setInDurationRegex.matchEntire(message)?.let { match ->
            val due = nowMillis + durationMillis(match.groupValues[2].toLong(), match.groupValues[3])
            return ReminderIntent.Create(cleanTask(match.groupValues[1]), due, describeDue(due, zone))
        }
        tomorrowAtRegex.matchEntire(message)?.let { match ->
            val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
            val time = parseClockTime(match.groupValues[2], defaultHour = 9) ?: LocalTime.of(9, 0)
            var due = now.plusDays(1).withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
            return ReminderIntent.Create(
                cleanTask(match.groupValues[1]),
                due.toInstant().toEpochMilli(),
                describeDue(due.toInstant().toEpochMilli(), zone),
            )
        }
        atTimeRegex.matchEntire(message)?.let { match ->
            val time = parseClockTime(match.groupValues[2]) ?: return ReminderIntent.Unrelated
            val due = nextAt(nowMillis, zone, time)
            return ReminderIntent.Create(
                cleanTask(match.groupValues[1]),
                due,
                describeDue(due, zone),
            )
        }
        needsWhenRegex.matchEntire(message)?.let { match ->
            val task = match.groupValues[1].ifBlank { match.groupValues[2] }
            if (task.isBlank()) return ReminderIntent.Unrelated
            return ReminderIntent.CreateNeedsWhen(cleanTask(task))
        }
        return ReminderIntent.Unrelated
    }

    fun describeDue(dueAtMillis: Long, zone: ZoneId): String {
        val whenAt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(dueAtMillis), zone)
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .format(whenAt)
    }

    private fun durationMillis(amount: Long, unit: String): Long {
        val n = amount.coerceAtLeast(0)
        return when {
            unit.startsWith("sec", ignoreCase = true) -> n * 1_000L
            unit.startsWith("min", ignoreCase = true) -> n * 60_000L
            unit.startsWith("hour", ignoreCase = true) || unit.startsWith("hr", ignoreCase = true) ->
                n * 3_600_000L
            else -> n * 86_400_000L
        }
    }

    private fun parseClockTime(raw: String, defaultHour: Int? = null): LocalTime? {
        val text = raw.trim()
        if (text.isEmpty()) {
            return defaultHour?.let { LocalTime.of(it, 0) }
        }
        val match = clockTimeRegex.matchEntire(text) ?: return null
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifBlank { "0" }.toInt()
        val meridem = match.groupValues[3].lowercase(Locale.US)
        if (minute !in 0..59 || hour !in 0..23) return null
        if (meridem == "am" || meridem == "pm") {
            if (hour !in 1..12) return null
            hour = when {
                meridem == "am" && hour == 12 -> 0
                meridem == "pm" && hour != 12 -> hour + 12
                else -> hour
            }
        }
        return LocalTime.of(hour, minute)
    }

    private fun nextAt(nowMillis: Long, zone: ZoneId, time: LocalTime): Long {
        val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        var candidate = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1)
        }
        return candidate.toInstant().toEpochMilli()
    }

    private fun cleanTask(raw: String): String {
        val trimmed = raw.trim().trimEnd('.', '!', '?')
        if (trimmed.isEmpty()) return "Reminder"
        return trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}
