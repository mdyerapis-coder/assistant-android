package com.mdyerapis.sable.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneOffset
import java.time.ZonedDateTime

class LocalAutomationParserTest {
    private val zone = ZoneOffset.UTC
    private val now = ZonedDateTime.of(2026, 9, 18, 10, 0, 0, 0, zone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun everyDayAtNine() {
        val intent = LocalAutomationParser.parse(
            "every day at 9am remind me to take vitamins",
            now,
            zone,
        ) as AutomationIntent.Create
        assertEquals("Take vitamins", intent.actionText)
        assertEquals(AutomationKind.DAILY, intent.schedule.kind)
        assertEquals(9, intent.schedule.hour)
        assertEquals(0, intent.schedule.minute)
    }

    @Test
    fun everyTwoHours() {
        val intent = LocalAutomationParser.parse(
            "remind me to drink water every 2 hours",
            now,
            zone,
        ) as AutomationIntent.Create
        assertEquals("Drink water", intent.actionText)
        assertEquals(AutomationKind.INTERVAL, intent.schedule.kind)
        assertEquals(2 * 3_600_000L, intent.schedule.intervalMillis)
    }

    @Test
    fun everyHourPing() {
        val intent = LocalAutomationParser.parse("every hour ping me", now, zone) as AutomationIntent.Create
        assertEquals(3_600_000L, intent.schedule.intervalMillis)
        assertEquals("Ping me", intent.actionText)
    }

    @Test
    fun everyFifteenSeconds() {
        val intent = LocalAutomationParser.parse(
            "every 15 seconds ping me",
            now,
            zone,
        ) as AutomationIntent.Create
        assertEquals(15_000L, intent.schedule.intervalMillis)
    }

    @Test
    fun weekdaysAtEight() {
        val intent = LocalAutomationParser.parse(
            "every weekday at 8am remind me to stand up",
            now,
            zone,
        ) as AutomationIntent.Create
        assertEquals(AutomationKind.WEEKLY, intent.schedule.kind)
        assertEquals(8, intent.schedule.hour)
        assertEquals(
            setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            intent.schedule.daysOfWeek,
        )
    }

    @Test
    fun everyMonday() {
        val intent = LocalAutomationParser.parse(
            "every monday at 9am remind me to plan the week",
            now,
            zone,
        ) as AutomationIntent.Create
        assertEquals(setOf(DayOfWeek.MONDAY), intent.schedule.daysOfWeek)
        assertEquals("Plan the week", intent.actionText)
    }

    @Test
    fun everyMorningDefaultsToEight() {
        val intent = LocalAutomationParser.parse(
            "every morning remind me to stretch",
            now,
            zone,
        ) as AutomationIntent.Create
        assertEquals(AutomationKind.DAILY, intent.schedule.kind)
        assertEquals(8, intent.schedule.hour)
    }

    @Test
    fun setupPrefix() {
        val intent = LocalAutomationParser.parse(
            "set up an automation to stretch every day at 7am",
            now,
            zone,
        ) as AutomationIntent.Create
        assertEquals("Stretch", intent.actionText)
        assertEquals(7, intent.schedule.hour)
    }

    @Test
    fun listAndCancel() {
        assertEquals(AutomationIntent.List, LocalAutomationParser.parse("what are my automations", now, zone))
        assertEquals(AutomationIntent.List, LocalAutomationParser.parse("Automations", now, zone))
        val cancel = LocalAutomationParser.parse("cancel automation vitamins", now, zone) as AutomationIntent.Cancel
        assertEquals("vitamins", cancel.query)
    }

    @Test
    fun monthlyIsHonestUnsupported() {
        val intent = LocalAutomationParser.parse(
            "every month remind me to pay rent",
            now,
            zone,
        ) as AutomationIntent.CreateUnsupported
        assertTrue(intent.reason.contains("monthly", ignoreCase = true))
    }

    @Test
    fun calendarActionIsHonestUnsupported() {
        val intent = LocalAutomationParser.parse(
            "every day check my calendar",
            now,
            zone,
        ) as AutomationIntent.CreateUnsupported
        assertTrue(intent.reason.contains("Calendar", ignoreCase = true))
    }

    @Test
    fun reminderPhrasesAreUnrelated() {
        assertEquals(
            AutomationIntent.Unrelated,
            LocalAutomationParser.parse("Remind me to stretch in 30 minutes", now, zone),
        )
        assertEquals(AutomationIntent.Unrelated, LocalAutomationParser.parse("hello", now, zone))
        assertEquals(
            AutomationIntent.Unrelated,
            LocalAutomationParser.parse("what's on my calendar?", now, zone),
        )
    }

    @Test
    fun nextFireDailyRollsForward() {
        val schedule = LocalAutomationSchedule(
            kind = AutomationKind.DAILY,
            hour = 9,
            minute = 0,
            expression = "every day at 9am",
        )
        // now is Friday 10:00 UTC → next is Saturday 09:00
        val next = schedule.nextFireMillis(now, zone)
        val expected = ZonedDateTime.of(2026, 9, 19, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, next)
    }

    @Test
    fun nextFireIntervalFromLastFired() {
        val schedule = LocalAutomationSchedule(
            kind = AutomationKind.INTERVAL,
            intervalMillis = 15_000L,
            expression = "every 15 seconds",
        )
        val next = schedule.nextFireMillis(now, zone, lastFiredMillis = now)
        assertEquals(now + 15_000L, next)
    }

    @Test
    fun nextFireWeekdaySkipsWeekend() {
        val fridayEvening = ZonedDateTime.of(2026, 9, 18, 18, 0, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
        val schedule = LocalAutomationSchedule(
            kind = AutomationKind.WEEKLY,
            hour = 8,
            minute = 0,
            daysOfWeek = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            expression = "every weekday at 8am",
        )
        val next = schedule.nextFireMillis(fridayEvening, zone)
        val monday = ZonedDateTime.of(2026, 9, 21, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(monday, next)
    }
}
