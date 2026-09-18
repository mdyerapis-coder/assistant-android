package com.mdyerapis.sable.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class LocalReminderParserTest {
    private val zone = ZoneOffset.UTC
    private val now = ZonedDateTime.of(2026, 9, 18, 10, 0, 0, 0, zone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun inThirtyMinutes() {
        val intent = LocalReminderParser.parse("Remind me to stretch in 30 minutes", now, zone)
        val create = intent as ReminderIntent.Create
        assertEquals("Stretch", create.text)
        assertEquals(now + 30 * 60_000L, create.dueAtMillis)
    }

    @Test
    fun inFifteenSeconds() {
        val intent = LocalReminderParser.parse("remind me to ping in 15 seconds", now, zone)
        val create = intent as ReminderIntent.Create
        assertEquals(now + 15_000L, create.dueAtMillis)
        assertEquals("Ping", create.text)
    }

    @Test
    fun bareDuration() {
        val intent = LocalReminderParser.parse("remind me in 2 minutes", now, zone)
        val create = intent as ReminderIntent.Create
        assertEquals("Reminder", create.text)
        assertEquals(now + 120_000L, create.dueAtMillis)
    }

    @Test
    fun tomorrowAtNineAm() {
        val intent = LocalReminderParser.parse(
            "remind me to call the dentist tomorrow at 9am",
            now,
            zone,
        )
        val create = intent as ReminderIntent.Create
        assertEquals("Call the dentist", create.text)
        val due = ZonedDateTime.of(2026, 9, 19, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(due, create.dueAtMillis)
    }

    @Test
    fun atTimeRollsToTomorrowWhenPast() {
        val intent = LocalReminderParser.parse("remind me to eat at 9:00", now, zone)
        val create = intent as ReminderIntent.Create
        // 10:00 UTC now, 9:00 already passed → tomorrow 09:00
        val due = ZonedDateTime.of(2026, 9, 19, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(due, create.dueAtMillis)
    }

    @Test
    fun listAndCancel() {
        assertEquals(ReminderIntent.List, LocalReminderParser.parse("what are my reminders", now, zone))
        assertEquals(ReminderIntent.List, LocalReminderParser.parse("Reminders", now, zone))
        val cancel = LocalReminderParser.parse("cancel reminder stretch", now, zone) as ReminderIntent.Cancel
        assertEquals("stretch", cancel.query)
    }

    @Test
    fun needsWhenAndUnrelated() {
        val needs = LocalReminderParser.parse("remind me to stretch", now, zone)
        assertTrue(needs is ReminderIntent.CreateNeedsWhen)
        assertEquals(ReminderIntent.Unrelated, LocalReminderParser.parse("remind me why the sky is blue", now, zone))
        assertEquals(ReminderIntent.Unrelated, LocalReminderParser.parse("hello", now, zone))
    }
}
