package com.mdyerapis.sable.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalReminderWorkTest {
    @Test
    fun uniqueNameIsStablePerId() {
        assertEquals("local-reminder-abc", LocalReminderWork.uniqueName("abc"))
        assertEquals(
            LocalReminderWork.uniqueName("abc"),
            LocalReminderWork.uniqueName("abc"),
        )
        assertTrue(LocalReminderWork.uniqueName("a") != LocalReminderWork.uniqueName("b"))
    }

    @Test
    fun scheduleAndCancelShareUniqueName() {
        val id = "due-1"
        // WorkManager enqueueUniqueWork(REPLACE) and cancelUniqueWork must
        // use the same name, and the exact-alarm receiver enqueues that name
        // with delay 0 so LocalReminderFiring can de-dupe.
        assertEquals("local-reminder-$id", LocalReminderWork.uniqueName(id))
        assertEquals("reminder_id", LocalReminderWork.KEY_ID)
        assertEquals("com.mdyerapis.sable.reminders.ACTION_DUE", LocalReminderWork.ACTION_DUE)
    }

    @Test
    fun localNotificationsReuseFcmChannel() {
        assertEquals("sable_reminders_v2", AndroidReminderNotifier.CHANNEL_ID)
    }
}
