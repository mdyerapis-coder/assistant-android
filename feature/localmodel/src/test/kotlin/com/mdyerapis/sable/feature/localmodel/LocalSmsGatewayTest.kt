package com.mdyerapis.sable.feature.localmodel

import com.mdyerapis.sable.core.model.DeviceSmsMessage
import com.mdyerapis.sable.core.model.ChatEvent
import com.mdyerapis.sable.core.model.SmsOperations
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSmsGatewayTest {
    @Test
    fun sendRecordsAndDoesNotUseFcm() = runTest {
        val ops = RecordingSmsOperations(sendGranted = true, readGranted = true)
        val gateway = DefaultLocalSmsGateway(ops)
        val events = gateway.handle("text 0412345678 running late", "local:1")!!
        assertTrue(events.any { it is ChatEvent.ToolCallStarted && it.name == "send_sms" })
        assertTrue(events.any { it is ChatEvent.ToolCallFinished && it.ok })
        assertEquals(listOf("0412345678" to "running late"), ops.sent)
        val spoken = events.filterIsInstance<ChatEvent.Delta>().single().content
        assertTrue(spoken.contains("no FCM", ignoreCase = true) || spoken.contains("no FastAPI"))
    }

    @Test
    fun sendWithoutPermissionIsHonest() = runTest {
        val ops = RecordingSmsOperations(sendGranted = false, readGranted = false)
        val events = DefaultLocalSmsGateway(ops).handle("text 0412345678 hi", "local:1")!!
        assertTrue(events.any { it is ChatEvent.ToolCallFinished && !it.ok })
        assertTrue(events.any { it is ChatEvent.ToolCallFinished && it.summary.contains("permission") })
        assertTrue(ops.sent.isEmpty())
    }

    @Test
    fun readInboxFormatsMessages() = runTest {
        val ops = RecordingSmsOperations(sendGranted = true, readGranted = true)
        ops.inbox += DeviceSmsMessage("0412000000", "hello", 1_000L)
        val events = DefaultLocalSmsGateway(ops).handle("read my texts", "local:1")!!
        assertTrue(events.any { it is ChatEvent.ToolCallStarted && it.name == "read_sms" })
        val spoken = events.filterIsInstance<ChatEvent.Delta>().single().content
        assertTrue(spoken.contains("hello"))
        assertTrue(spoken.contains("0412000000"))
    }

    @Test
    fun unrelatedReturnsNull() = runTest {
        val gateway = DefaultLocalSmsGateway(RecordingSmsOperations(true, true))
        assertNull(gateway.handle("what's on my calendar?", "local:1"))
        assertNull(gateway.handle("Remind me to stretch in 30 minutes", "local:1"))
    }
}

private class RecordingSmsOperations(
    private val sendGranted: Boolean,
    private val readGranted: Boolean,
) : SmsOperations {
    val sent = mutableListOf<Pair<String, String>>()
    val inbox = mutableListOf<DeviceSmsMessage>()
    override fun hasSendPermission(): Boolean = sendGranted
    override fun hasReadPermission(): Boolean = readGranted
    override suspend fun send(phone: String, message: String) {
        sent += phone to message
    }
    override suspend fun readInbox(phoneFilter: String?, limit: Int): List<DeviceSmsMessage> =
        inbox.filter { phoneFilter == null || it.fromNumber.contains(phoneFilter) }
            .take(limit)
}
