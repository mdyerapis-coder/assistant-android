package com.mdyerapis.sable.core.database.automation

import com.mdyerapis.sable.core.database.chat.ChatDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomAutomationStore @Inject constructor(
    db: ChatDatabase,
) : AutomationStore {
    private val dao = db.automationDao()

    override suspend fun insert(automation: LocalAutomation) {
        dao.upsert(
            AutomationEntity(
                id = automation.id,
                name = automation.name,
                expression = automation.expression,
                actionText = automation.actionText,
                kind = automation.kind,
                intervalMillis = automation.intervalMillis,
                hour = automation.hour,
                minute = automation.minute,
                daysOfWeek = automation.daysOfWeek,
                createdAt = automation.createdAtMillis,
                lastFiredAt = automation.lastFiredAtMillis,
                enabled = automation.enabled,
            ),
        )
    }

    override suspend fun get(id: String): LocalAutomation? = dao.get(id)?.toModel()

    override suspend fun listEnabled(): List<LocalAutomation> =
        dao.listEnabled().map { it.toModel() }

    override suspend fun listAll(): List<LocalAutomation> = dao.listAll().map { it.toModel() }

    override suspend fun markFired(id: String, nowMillis: Long): Boolean =
        dao.markFired(id, nowMillis) > 0

    override suspend fun markDisabled(id: String): Boolean = dao.markDisabled(id) > 0
}
