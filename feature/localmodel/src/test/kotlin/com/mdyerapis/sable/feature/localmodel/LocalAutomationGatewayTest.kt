package com.mdyerapis.sable.feature.localmodel

import com.mdyerapis.sable.core.database.automation.LocalAutomation
import com.mdyerapis.sable.core.database.automation.AutomationScheduler
import com.mdyerapis.sable.core.database.automation.AutomationStore
import com.mdyerapis.sable.core.model.ChatEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAutomationGatewayTest {
    @Test
    fun createSchedulesAndCancelRemoves() = runTest {
        val store = RecordingAutomationStore()
        val scheduler = RecordingAutomationScheduler()
        val gateway = DefaultLocalAutomationGateway(store, scheduler)

        val created = gateway.handle("every day at 9am remind me to take vitamins", "local:1")!!
        assertTrue(created.any { it is ChatEvent.ToolCallStarted && it.name == "create_automation" })
        assertTrue(created.any { it is ChatEvent.ToolCallFinished && it.ok })
        assertEquals(1, scheduler.scheduled.size)
        assertEquals("Take vitamins", scheduler.scheduled.first().actionText)
        assertEquals(1, store.listEnabled().size)

        val listed = gateway.handle("what are my automations", "local:1")!!
        val listDelta = listed.filterIsInstance<ChatEvent.Delta>().single()
        assertTrue(listDelta.content.contains("Take vitamins"))

        val cancelled = gateway.handle("cancel automation vitamins", "local:1")!!
        assertTrue(cancelled.any { it is ChatEvent.ToolCallFinished && it.ok })
        assertEquals(listOf(scheduler.scheduled.first().id), scheduler.cancelled)
        assertTrue(store.listEnabled().isEmpty())
    }

    @Test
    fun unrelatedReturnsNull() = runTest {
        val gateway = DefaultLocalAutomationGateway(RecordingAutomationStore(), RecordingAutomationScheduler())
        assertNull(gateway.handle("Remind me to stretch in 30 minutes", "local:1"))
        assertNull(gateway.handle("what's on my calendar?", "local:1"))
    }

    @Test
    fun unsupportedDoesNotSchedule() = runTest {
        val scheduler = RecordingAutomationScheduler()
        val gateway = DefaultLocalAutomationGateway(RecordingAutomationStore(), scheduler)
        val monthly = gateway.handle("every month remind me to pay rent", "local:1")!!
        assertTrue(monthly.any { it is ChatEvent.ToolCallFinished && !it.ok })
        assertTrue(scheduler.scheduled.isEmpty())

        val calendar = gateway.handle("every day check my calendar", "local:1")!!
        assertTrue(calendar.any { it is ChatEvent.ToolCallFinished && !it.ok })
        assertTrue(
            calendar.filterIsInstance<ChatEvent.Delta>().single().content.contains("Calendar", ignoreCase = true),
        )
        assertTrue(scheduler.scheduled.isEmpty())
    }
}

private class RecordingAutomationStore : AutomationStore {
    private val rows = LinkedHashMap<String, LocalAutomation>()
    override suspend fun insert(automation: LocalAutomation) {
        rows[automation.id] = automation
    }
    override suspend fun get(id: String): LocalAutomation? = rows[id]
    override suspend fun listEnabled(): List<LocalAutomation> = rows.values.filter { it.enabled }
    override suspend fun listAll(): List<LocalAutomation> = rows.values.toList()
    override suspend fun markFired(id: String, nowMillis: Long): Boolean = false
    override suspend fun markDisabled(id: String): Boolean {
        val current = rows[id] ?: return false
        if (!current.enabled) return false
        rows[id] = current.copy(enabled = false)
        return true
    }
}

private class RecordingAutomationScheduler : AutomationScheduler {
    val scheduled = mutableListOf<LocalAutomation>()
    val cancelled = mutableListOf<String>()
    override fun schedule(automation: LocalAutomation) {
        scheduled += automation
    }
    override fun cancel(id: String) {
        cancelled += id
    }
}
