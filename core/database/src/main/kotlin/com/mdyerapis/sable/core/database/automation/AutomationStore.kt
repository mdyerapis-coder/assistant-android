package com.mdyerapis.sable.core.database.automation

interface AutomationStore {
    suspend fun insert(automation: LocalAutomation)
    suspend fun get(id: String): LocalAutomation?
    suspend fun listEnabled(): List<LocalAutomation>
    suspend fun listAll(): List<LocalAutomation>
    /** Returns true if this caller won the de-dupe window for a fire. */
    suspend fun markFired(id: String, nowMillis: Long): Boolean
    /** Returns true if an enabled row was disabled. */
    suspend fun markDisabled(id: String): Boolean
}

interface AutomationScheduler {
    fun schedule(automation: LocalAutomation)
    fun cancel(id: String)
}

interface AutomationNotifier {
    fun notify(id: String, title: String, text: String)
}
