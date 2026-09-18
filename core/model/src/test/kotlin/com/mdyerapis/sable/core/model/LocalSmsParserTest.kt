package com.mdyerapis.sable.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSmsParserTest {
    @Test
    fun sendWithSayingAndE164() {
        val intent = LocalSmsParser.parse("send a text to +61 412 345 678 saying running late")
        val send = intent as SmsIntent.Send
        assertEquals("+61412345678", send.phone)
        assertEquals("running late", send.message)
    }

    @Test
    fun textNumberThenBody() {
        val intent = LocalSmsParser.parse("text 0412345678 I'll be there in 10")
        val send = intent as SmsIntent.Send
        assertEquals("0412345678", send.phone)
        assertEquals("I'll be there in 10", send.message)
    }

    @Test
    fun sendToNameNeedsPhone() {
        val intent = LocalSmsParser.parse("text mum that I'll be late")
        val needs = intent as SmsIntent.SendNeedsPhone
        assertEquals("I'll be late", needs.message)
    }

    @Test
    fun sendToNumberNeedsMessage() {
        val intent = LocalSmsParser.parse("send sms to +61412345678")
        val needs = intent as SmsIntent.SendNeedsMessage
        assertEquals("+61412345678", needs.phone)
    }

    @Test
    fun readInboxAndFromFilter() {
        assertEquals(
            SmsIntent.Read(phoneFilter = null, limit = 10),
            LocalSmsParser.parse("read my texts"),
        )
        val from = LocalSmsParser.parse("any messages from 0412345678") as SmsIntent.Read
        assertEquals("0412345678", from.phoneFilter)
        val last = LocalSmsParser.parse("show my last 5 sms") as SmsIntent.Read
        assertEquals(5, last.limit)
    }

    @Test
    fun unrelatedStaysChat() {
        assertEquals(SmsIntent.Unrelated, LocalSmsParser.parse("hello"))
        assertEquals(SmsIntent.Unrelated, LocalSmsParser.parse("what's on my calendar?"))
        assertEquals(SmsIntent.Unrelated, LocalSmsParser.parse("Remind me to stretch in 30 minutes"))
        assertEquals(SmsIntent.Unrelated, LocalSmsParser.parse("text a haiku about the ocean"))
    }
}
