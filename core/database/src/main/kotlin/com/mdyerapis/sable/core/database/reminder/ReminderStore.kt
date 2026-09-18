package com.mdyerapis.sable.core.database.reminder

interface ReminderStore {
    suspend fun insert(reminder: LocalReminder)
    suspend fun get(id: String): LocalReminder?
    suspend fun listPending(): List<LocalReminder>
    suspend fun listAll(): List<LocalReminder>
    /** Returns true if this caller won the pending→fired race. */
    suspend fun markFired(id: String, nowMillis: Long): Boolean
    /** Returns true if a pending row was cancelled. */
    suspend fun markCancelled(id: String): Boolean
}

interface ReminderScheduler {
    fun schedule(reminder: LocalReminder)
    fun cancel(id: String)
}

interface ReminderNotifier {
    fun notify(id: String, text: String)
}
