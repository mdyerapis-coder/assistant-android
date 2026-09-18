package com.mdyerapis.sable.core.database.reminder

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalReminderFiringTest {
    @Test
    fun firstFireNotifiesAndMarksFired() = runTest {
        val store = InMemoryReminderStore()
        val notifier = RecordingNotifier()
        val reminder = LocalReminder(
            id = "r1",
            text = "Stretch",
            dueAtMillis = 1_000L,
            createdAtMillis = 0L,
            status = LocalReminder.STATUS_PENDING,
        )
        store.insert(reminder)
        val firing = LocalReminderFiring(store, notifier)

        assertTrue(firing.fire("r1", nowMillis = 2_000L))
        assertEquals(listOf("r1" to "Stretch"), notifier.shown)
        assertEquals(LocalReminder.STATUS_FIRED, store.get("r1")!!.status)
        assertEquals(2_000L, store.get("r1")!!.firedAtMillis)

        assertFalse(firing.fire("r1", nowMillis = 3_000L))
        assertEquals(1, notifier.shown.size)
    }

    @Test
    fun cancelledReminderDoesNotNotify() = runTest {
        val store = InMemoryReminderStore()
        val notifier = RecordingNotifier()
        store.insert(
            LocalReminder(
                id = "r2",
                text = "Call",
                dueAtMillis = 1L,
                createdAtMillis = 0L,
                status = LocalReminder.STATUS_PENDING,
            ),
        )
        store.markCancelled("r2")
        assertFalse(LocalReminderFiring(store, notifier).fire("r2"))
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun unknownIdIsNoop() = runTest {
        val notifier = RecordingNotifier()
        assertFalse(LocalReminderFiring(InMemoryReminderStore(), notifier).fire("missing"))
        assertTrue(notifier.shown.isEmpty())
    }
}

internal class InMemoryReminderStore : ReminderStore {
    private val rows = LinkedHashMap<String, LocalReminder>()

    override suspend fun insert(reminder: LocalReminder) {
        rows[reminder.id] = reminder
    }

    override suspend fun get(id: String): LocalReminder? = rows[id]

    override suspend fun listPending(): List<LocalReminder> =
        rows.values.filter { it.isPending }.sortedBy { it.dueAtMillis }

    override suspend fun listAll(): List<LocalReminder> =
        rows.values.sortedBy { it.dueAtMillis }

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

internal class RecordingNotifier : ReminderNotifier {
    val shown = mutableListOf<Pair<String, String>>()
    override fun notify(id: String, text: String) {
        shown += id to text
    }
}
