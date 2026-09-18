package com.mdyerapis.sable.core.database.automation

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts a local notification for an enabled automation and records
 * lastFiredAt. WorkManager and the exact-alarm receiver both call this
 * so a double wake-up cannot notify twice (2s compare-and-swap window).
 * Unlike one-shot reminders, the row stays enabled so the scheduler can
 * enqueue the next occurrence.
 */
@Singleton
class LocalAutomationFiring @Inject constructor(
    private val store: AutomationStore,
    private val notifier: AutomationNotifier,
) {
    suspend fun fire(id: String, nowMillis: Long = System.currentTimeMillis()): LocalAutomation? {
        val automation = store.get(id) ?: return null
        if (!automation.enabled) return null
        val claimed = store.markFired(id, nowMillis)
        if (!claimed) return null
        notifier.notify(id, automation.name, automation.actionText)
        return store.get(id)
    }
}
