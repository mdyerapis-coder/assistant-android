package com.mdyerapis.sable.core.model

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Deterministic on-device automation intents. Recurring jobs are not
 * handed to MediaPipe — small local models invent cron. Unrelated chat
 * still goes to the LLM. Cloud mode never uses this parser.
 */
sealed class AutomationIntent {
    data class Create(
        val name: String,
        val actionText: String,
        val schedule: LocalAutomationSchedule,
    ) : AutomationIntent()

    data class CreateUnsupported(val reason: String) : AutomationIntent()

    data object List : AutomationIntent()

    data class Cancel(val query: String) : AutomationIntent()

    data object Unrelated : AutomationIntent()
}

enum class AutomationKind {
    INTERVAL,
    DAILY,
    WEEKLY,
}

data class LocalAutomationSchedule(
    val kind: AutomationKind,
    val intervalMillis: Long? = null,
    val hour: Int = 9,
    val minute: Int = 0,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val expression: String,
) {
    fun daysOfWeekCsv(): String? =
        daysOfWeek.takeIf { it.isNotEmpty() }
            ?.sortedBy { it.value }
            ?.joinToString(",") { it.value.toString() }

    /**
     * Next fire at or after [nowMillis]. Recurring — never returns a
     * time in the past. [lastFiredMillis] skips the just-fired slot for
     * INTERVAL schedules.
     */
    fun nextFireMillis(
        nowMillis: Long,
        zone: ZoneId,
        lastFiredMillis: Long? = null,
    ): Long {
        val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        return when (kind) {
            AutomationKind.INTERVAL -> {
                val step = intervalMillis?.coerceAtLeast(1L) ?: 60_000L
                val start = lastFiredMillis ?: nowMillis
                var t = start + step
                while (t <= nowMillis) t += step
                t
            }
            AutomationKind.DAILY -> nextAtDays(now, emptySet(), hour, minute)
            AutomationKind.WEEKLY -> nextAtDays(now, daysOfWeek, hour, minute)
        }
    }

    companion object {
        fun fromStored(
            kind: String,
            intervalMillis: Long?,
            hour: Int,
            minute: Int,
            daysOfWeek: String?,
            expression: String,
        ): LocalAutomationSchedule {
            val days = daysOfWeek
                ?.split(',')
                ?.mapNotNull { token ->
                    token.trim().toIntOrNull()?.let { n ->
                        if (n in 1..7) DayOfWeek.of(n) else null
                    }
                }
                ?.toSet()
                ?: emptySet()
            return LocalAutomationSchedule(
                kind = AutomationKind.valueOf(kind),
                intervalMillis = intervalMillis,
                hour = hour,
                minute = minute,
                daysOfWeek = days,
                expression = expression,
            )
        }
    }
}

object LocalAutomationParser {
    private val listRegex = Regex(
        """^(?:what(?:'s| is| are)?|show|list|see|any)\s+(?:my\s+)?automations\??$|^automations\??$""",
        RegexOption.IGNORE_CASE,
    )
    private val cancelRegex = Regex(
        """^(?:cancel|delete|remove|stop)\s+(?:the\s+)?automation\s+(?:#\s*)?(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val setupPrefix = Regex(
        """^(?:set\s+up|create|add|make)\s+(?:an?\s+)?automation(?:\s+to|\s+that)?\s+""",
        RegexOption.IGNORE_CASE,
    )
    private val unsupportedPeriod = Regex(
        """\b(?:every\s+month|monthly|every\s+year|yearly|annually|first of the month)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val toolAction = Regex(
        """\b(?:calendar|gmail|e-?mail|sms|text message|send (?:a )?text|send (?:an )?sms)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val looksScheduled = Regex(
        """\b(?:every|daily|hourly|weekdays?|weekly|automation)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val intervalRegex = Regex(
        """every\s+(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?)""",
        RegexOption.IGNORE_CASE,
    )
    private val hourlyRegex = Regex(
        """(?:every\s+hour|hourly)""",
        RegexOption.IGNORE_CASE,
    )
    private val weekdayNameRegex = Regex(
        """every\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)s?(?:\s+at\s+(\d{1,2}(?::\d{2})?\s*(?:am|pm)?))?""",
        RegexOption.IGNORE_CASE,
    )
    private val weekdaysRegex = Regex(
        """every\s+weekdays?(?:\s+at\s+(\d{1,2}(?::\d{2})?\s*(?:am|pm)?))?""",
        RegexOption.IGNORE_CASE,
    )
    private val dailyNamedRegex = Regex(
        """every\s+(morning|evening|night)(?:\s+at\s+(\d{1,2}(?::\d{2})?\s*(?:am|pm)?))?""",
        RegexOption.IGNORE_CASE,
    )
    private val dailyRegex = Regex(
        """(?:every\s+day|daily)(?:\s+at\s+(\d{1,2}(?::\d{2})?\s*(?:am|pm)?))?""",
        RegexOption.IGNORE_CASE,
    )
    private val leadInRegex = Regex(
        """^(?:remind\s+me\s+to|remind\s+me\s+that|notify\s+me\s+to|tell\s+me\s+to|alert\s+me\s+to|ping\s+me\s+to)\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val clockTimeRegex = Regex(
        """^\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(
        raw: String,
        @Suppress("UNUSED_PARAMETER") nowMillis: Long = System.currentTimeMillis(),
        @Suppress("UNUSED_PARAMETER") zone: ZoneId = ZoneId.systemDefault(),
    ): AutomationIntent {
        val message = raw.trim().replace(Regex("\\s+"), " ")
        if (message.isEmpty()) return AutomationIntent.Unrelated
        if (listRegex.matches(message)) return AutomationIntent.List
        cancelRegex.matchEntire(message)?.let { match ->
            return AutomationIntent.Cancel(match.groupValues[1].trim())
        }
        if (!looksScheduled.containsMatchIn(message)) return AutomationIntent.Unrelated

        val strippedSetup = setupPrefix.replaceFirst(message, "")
        if (unsupportedPeriod.containsMatchIn(strippedSetup)) {
            return AutomationIntent.CreateUnsupported(
                "On-device automations don't support monthly or yearly schedules. " +
                    "Use daily, weekdays, weekly, or every N minutes/hours — " +
                    "or switch to Cloud Assistant for full croniter.",
            )
        }

        val extracted = extractSchedule(strippedSetup) ?: return AutomationIntent.Unrelated
        val actionRaw = stripLeadIn(extracted.remainder)
        if (actionRaw.isBlank()) {
            return AutomationIntent.CreateUnsupported(
                "What should I notify you about? Try “every day at 9am remind me to drink water”.",
            )
        }
        if (toolAction.containsMatchIn(actionRaw)) {
            return AutomationIntent.CreateUnsupported(
                "On-device automations can only post a local notification. " +
                    "Calendar, Gmail, and SMS actions need Cloud Assistant " +
                    "(server croniter + FCM). I won't pretend to run those here.",
            )
        }
        val actionText = cleanTask(actionRaw)
        return AutomationIntent.Create(
            name = actionText.take(40),
            actionText = actionText,
            schedule = extracted.schedule,
        )
    }

    private data class Extracted(
        val schedule: LocalAutomationSchedule,
        val remainder: String,
    )

    private fun extractSchedule(message: String): Extracted? {
        weekdayNameRegex.find(message)?.let { match ->
            val day = parseDay(match.groupValues[1]) ?: return@let
            val time = parseClockTime(match.groupValues[2], defaultHour = 9) ?: LocalTime.of(9, 0)
            return Extracted(
                LocalAutomationSchedule(
                    kind = AutomationKind.WEEKLY,
                    hour = time.hour,
                    minute = time.minute,
                    daysOfWeek = setOf(day),
                    expression = "every ${day.name.lowercase(Locale.US)} at ${describeClock(time)}",
                ),
                remainderOf(message, match.range),
            )
        }
        weekdaysRegex.find(message)?.let { match ->
            val time = parseClockTime(match.groupValues[1], defaultHour = 9) ?: LocalTime.of(9, 0)
            return Extracted(
                LocalAutomationSchedule(
                    kind = AutomationKind.WEEKLY,
                    hour = time.hour,
                    minute = time.minute,
                    daysOfWeek = WEEKDAYS,
                    expression = "every weekday at ${describeClock(time)}",
                ),
                remainderOf(message, match.range),
            )
        }
        dailyNamedRegex.find(message)?.let { match ->
            val named = match.groupValues[1].lowercase(Locale.US)
            val defaultHour = when (named) {
                "morning" -> 8
                "evening" -> 18
                else -> 21
            }
            val time = parseClockTime(match.groupValues[2], defaultHour = defaultHour)
                ?: LocalTime.of(defaultHour, 0)
            return Extracted(
                LocalAutomationSchedule(
                    kind = AutomationKind.DAILY,
                    hour = time.hour,
                    minute = time.minute,
                    expression = "every $named at ${describeClock(time)}",
                ),
                remainderOf(message, match.range),
            )
        }
        dailyRegex.find(message)?.let { match ->
            val time = parseClockTime(match.groupValues[1], defaultHour = 9) ?: LocalTime.of(9, 0)
            return Extracted(
                LocalAutomationSchedule(
                    kind = AutomationKind.DAILY,
                    hour = time.hour,
                    minute = time.minute,
                    expression = "every day at ${describeClock(time)}",
                ),
                remainderOf(message, match.range),
            )
        }
        hourlyRegex.find(message)?.let { match ->
            return Extracted(
                LocalAutomationSchedule(
                    kind = AutomationKind.INTERVAL,
                    intervalMillis = 3_600_000L,
                    expression = "every hour",
                ),
                remainderOf(message, match.range),
            )
        }
        intervalRegex.find(message)?.let { match ->
            val amount = match.groupValues[1].toLong()
            val unit = match.groupValues[2]
            val millis = durationMillis(amount, unit)
            return Extracted(
                LocalAutomationSchedule(
                    kind = AutomationKind.INTERVAL,
                    intervalMillis = millis,
                    expression = "every $amount ${unit.lowercase(Locale.US)}",
                ),
                remainderOf(message, match.range),
            )
        }
        return null
    }

    private fun remainderOf(message: String, range: IntRange): String {
        val before = message.substring(0, range.first).trim()
        val after = message.substring(range.last + 1).trim()
        return listOf(before, after).filter { it.isNotBlank() }.joinToString(" ")
            .trim(' ', ',', '.', '-', ':')
    }

    private fun stripLeadIn(raw: String): String {
        val trimmed = raw.trim().trimStart(',', '.', '-')
        leadInRegex.matchEntire(trimmed)?.let { return it.groupValues[1].trim() }
        return trimmed
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

    private fun parseDay(raw: String): DayOfWeek? =
        when (raw.lowercase(Locale.US).trimEnd('s')) {
            "monday" -> DayOfWeek.MONDAY
            "tuesday" -> DayOfWeek.TUESDAY
            "wednesday" -> DayOfWeek.WEDNESDAY
            "thursday" -> DayOfWeek.THURSDAY
            "friday" -> DayOfWeek.FRIDAY
            "saturday" -> DayOfWeek.SATURDAY
            "sunday" -> DayOfWeek.SUNDAY
            else -> null
        }

    private fun describeClock(time: LocalTime): String {
        val hour12 = when {
            time.hour == 0 -> 12
            time.hour > 12 -> time.hour - 12
            else -> time.hour
        }
        val meridem = if (time.hour < 12) "am" else "pm"
        return if (time.minute == 0) "$hour12$meridem" else "%d:%02d%s".format(hour12, time.minute, meridem)
    }

    private fun cleanTask(raw: String): String {
        val trimmed = raw.trim().trimEnd('.', '!', '?')
        if (trimmed.isEmpty()) return "Automation"
        return trimmed.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }

    private val WEEKDAYS: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    )
}

private fun nextAtDays(
    now: ZonedDateTime,
    days: Set<DayOfWeek>,
    hour: Int,
    minute: Int,
): Long {
    val time = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    var candidate = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
    repeat(8) {
        val dayOk = days.isEmpty() || candidate.dayOfWeek in days
        if (dayOk && candidate.isAfter(now)) {
            return candidate.toInstant().toEpochMilli()
        }
        candidate = candidate.plusDays(1)
            .withHour(time.hour)
            .withMinute(time.minute)
            .withSecond(0)
            .withNano(0)
    }
    return candidate.toInstant().toEpochMilli()
}
