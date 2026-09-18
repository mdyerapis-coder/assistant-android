package com.mdyerapis.sable.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalGoogleParserTest {
    @Test
    fun calendarPhrases() {
        assertEquals(GoogleIntent.Calendar, LocalGoogleParser.parse("what's on my calendar?"))
        assertEquals(GoogleIntent.Calendar, LocalGoogleParser.parse("What is on my calendar today?"))
        assertEquals(GoogleIntent.Calendar, LocalGoogleParser.parse("show my calendar"))
        assertEquals(GoogleIntent.Calendar, LocalGoogleParser.parse("am I free tomorrow"))
        assertEquals(GoogleIntent.Calendar, LocalGoogleParser.parse("schedule a meeting with Declan at 3pm"))
        assertEquals(GoogleIntent.Calendar, LocalGoogleParser.parse("add lunch on my calendar"))
        assertEquals(GoogleIntent.Calendar, LocalGoogleParser.parse("calendar today"))
    }

    @Test
    fun gmailPhrases() {
        assertEquals(GoogleIntent.Gmail, LocalGoogleParser.parse("check my email"))
        assertEquals(GoogleIntent.Gmail, LocalGoogleParser.parse("List my unread emails"))
        assertEquals(GoogleIntent.Gmail, LocalGoogleParser.parse("send an email to mason"))
        assertEquals(GoogleIntent.Gmail, LocalGoogleParser.parse("any new mail?"))
        assertEquals(GoogleIntent.Gmail, LocalGoogleParser.parse("gmail"))
        assertEquals(GoogleIntent.Gmail, LocalGoogleParser.parse("inbox"))
    }

    @Test
    fun unrelatedAndRemindersStayOffThisPath() {
        assertEquals(GoogleIntent.Unrelated, LocalGoogleParser.parse("hello"))
        assertEquals(GoogleIntent.Unrelated, LocalGoogleParser.parse("what is a calendar"))
        assertEquals(GoogleIntent.Unrelated, LocalGoogleParser.parse("Remind me to stretch in 30 minutes"))
        assertEquals(GoogleIntent.Unrelated, LocalGoogleParser.parse("what are my reminders"))
        assertEquals(GoogleIntent.Unrelated, LocalGoogleParser.parse("Say hello and introduce yourself!"))
        assertEquals(GoogleIntent.Unrelated, LocalGoogleParser.parse("Give me a morning brief"))
    }
}
