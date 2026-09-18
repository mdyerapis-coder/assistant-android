package com.mdyerapis.sable.core.database.automation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAutomationFiringTest {
    @Test
    fun firstFireNotifiesAndKeepsEnabled() = runTest {
        val store = InMemoryAutomationStore()
        val notifier = RecordingAutomationNotifier()
        store.insert(sample(id = "a1", enabled = true))
        val firing = LocalAutomationFiring(store, notifier)

        val updated = firing.fire("a1", nowMillis = 2_000L)
        assertEquals(listOf(Triple("a1", "Drink water", "Drink water")), notifier.shown)
        assertTrue(updated!!.enabled)
        assertEquals(2_000L, updated.lastFiredAtMillis)

        assertNull(firing.fire("a1", nowMillis = 2_500L))
        assertEquals(1, notifier.shown.size)
    }

    @Test
    fun disabledAutomationDoesNotNotify() = runTest {
        val store = InMemoryAutomationStore()
        val notifier = RecordingAutomationNotifier()
        store.insert(sample(id = "a2", enabled = true))
        store.markDisabled("a2")
        assertNull(LocalAutomationFiring(store, notifier).fire("a2"))
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun unknownIdIsNoop() = runTest {
        val notifier = RecordingAutomationNotifier()
        assertNull(LocalAutomationFiring(InMemoryAutomationStore(), notifier).fire("missing"))
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun secondFireAfterWindowNotifiesAgain() = runTest {
        val store = InMemoryAutomationStore()
        val notifier = RecordingAutomationNotifier()
        store.insert(sample(id = "a3", enabled = true))
        val firing = LocalAutomationFiring(store, notifier)
        assertTrue(firing.fire("a3", nowMillis = 1_000L) != null)
        assertTrue(firing.fire("a3", nowMillis = 5_000L) != null)
        assertEquals(2, notifier.shown.size)
        assertTrue(store.get("a3")!!.enabled)
    }
}

internal class InMemoryAutomationStore : AutomationStore {
    private val rows = LinkedHashMap<String, LocalAutomation>()

    override suspend fun insert(automation: LocalAutomation) {
        rows[automation.id] = automation
    }

    override suspend fun get(id: String): LocalAutomation? = rows[id]

    override suspend fun listEnabled(): List<LocalAutomation> =
        rows.values.filter { it.enabled }

    override suspend fun listAll(): List<LocalAutomation> = rows.values.toList()

    override suspend fun markFired(id: String, nowMillis: Long): Boolean {
        val current = rows[id] ?: return false
        if (!current.enabled) return false
        val last = current.lastFiredAtMillis
        if (last != null && last >= nowMillis - 2_000L) return false
        rows[id] = current.copy(lastFiredAtMillis = nowMillis)
        return true
    }

    override suspend fun markDisabled(id: String): Boolean {
        val current = rows[id] ?: return false
        if (!current.enabled) return false
        rows[id] = current.copy(enabled = false)
        return true
    }
}

internal class RecordingAutomationNotifier : AutomationNotifier {
    val shown = mutableListOf<Triple<String, String, String>>()
    override fun notify(id: String, title: String, text: String) {
        shown += Triple(id, title, text)
    }
}

private fun sample(id: String, enabled: Boolean) = LocalAutomation(
    id = id,
    name = "Drink water",
    expression = "every 2 hours",
    actionText = "Drink water",
    kind = "INTERVAL",
    intervalMillis = 7_200_000L,
    hour = 0,
    minute = 0,
    daysOfWeek = null,
    createdAtMillis = 0L,
    enabled = enabled,
)
