package com.mdyerapis.sable.feature.localmodel

import com.mdyerapis.sable.core.database.reminder.LocalReminder
import com.mdyerapis.sable.core.database.reminder.ReminderScheduler
import com.mdyerapis.sable.core.database.reminder.ReminderStore
import com.mdyerapis.sable.core.model.ChatEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalReminderGatewayTest {
    @Test
    fun createSchedulesAndCancelRemoves() = runTest {
        val store = RecordingStore()
        val scheduler = RecordingScheduler()
        val gateway = DefaultLocalReminderGateway(store, scheduler)

        val created = gateway.handle("Remind me to stretch in 30 minutes", "local:1")!!
        assertTrue(created.any { it is ChatEvent.ToolCallStarted && it.name == "create_reminder" })
        assertTrue(created.any { it is ChatEvent.ToolCallFinished && it.ok })
        assertEquals(1, scheduler.scheduled.size)
        assertEquals("Stretch", scheduler.scheduled.first().text)
        assertEquals(1, store.listPending().size)

        val listed = gateway.handle("what are my reminders", "local:1")!!
        val listDelta = listed.filterIsInstance<ChatEvent.Delta>().single()
        assertTrue(listDelta.content.contains("Stretch"))

        val cancelled = gateway.handle("cancel reminder stretch", "local:1")!!
        assertTrue(cancelled.any { it is ChatEvent.ToolCallFinished && it.ok })
        assertEquals(listOf(scheduler.scheduled.first().id), scheduler.cancelled)
        assertTrue(store.listPending().isEmpty())
    }

    @Test
    fun unrelatedReturnsNull() = runTest {
        val gateway = DefaultLocalReminderGateway(RecordingStore(), RecordingScheduler())
        assertNull(gateway.handle("what's on my calendar?", "local:1"))
    }

    @Test
    fun needsWhenDoesNotSchedule() = runTest {
        val scheduler = RecordingScheduler()
        val gateway = DefaultLocalReminderGateway(RecordingStore(), scheduler)
        val events = gateway.handle("remind me to stretch", "local:1")!!
        assertTrue(events.any { it is ChatEvent.ToolCallFinished && !it.ok })
        assertTrue(scheduler.scheduled.isEmpty())
    }
}

private class RecordingStore : ReminderStore {
    private val rows = LinkedHashMap<String, LocalReminder>()
    override suspend fun insert(reminder: LocalReminder) {
        rows[reminder.id] = reminder
    }
    override suspend fun get(id: String): LocalReminder? = rows[id]
    override suspend fun listPending(): List<LocalReminder> =
        rows.values.filter { it.isPending }.sortedBy { it.dueAtMillis }
    override suspend fun listAll(): List<LocalReminder> = rows.values.toList()
    override suspend fun markFired(id: String, nowMillis: Long): Boolean {
        val current = rows[id] ?: return false
        if (!current.isPending) return false
        rows[id] = current.copy(status = LocalReminder.STATUS_FIRED, firedAtMillis = nowMillis)
        return true
    }
    override suspend fun markCancelled(id: String): Boolean {
        val current = rows[id] ?: return false
        if (!current.isPending) return false
        rows[id] = current.copy(status = LocalReminder.STATUS_CANCELLED)
        return true
    }
}

private class RecordingScheduler : ReminderScheduler {
    val scheduled = mutableListOf<LocalReminder>()
    val cancelled = mutableListOf<String>()
    override fun schedule(reminder: LocalReminder) {
        scheduled += reminder
    }
    override fun cancel(id: String) {
        cancelled += id
    }
}
