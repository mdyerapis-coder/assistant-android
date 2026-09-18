package com.mdyerapis.sable.core.database.reminder

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Marks a due reminder fired at most once, then shows a local notification.
 * WorkManager and the exact-alarm receiver both call this so a double
 * wake-up cannot notify twice.
 */
@Singleton
class LocalReminderFiring @Inject constructor(
    private val store: ReminderStore,
    private val notifier: ReminderNotifier,
) {
    suspend fun fire(id: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val reminder = store.get(id) ?: return false
        if (!reminder.isPending) return false
        val claimed = store.markFired(id, nowMillis)
        if (!claimed) return false
        notifier.notify(id, reminder.text)
        return true
    }
}
