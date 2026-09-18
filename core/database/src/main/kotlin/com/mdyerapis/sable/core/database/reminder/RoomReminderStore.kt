package com.mdyerapis.sable.core.database.reminder

import com.mdyerapis.sable.core.database.chat.ChatDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomReminderStore @Inject constructor(
    db: ChatDatabase,
) : ReminderStore {
    private val dao = db.reminderDao()

    override suspend fun insert(reminder: LocalReminder) {
        dao.upsert(
            ReminderEntity(
                id = reminder.id,
                text = reminder.text,
                dueAt = reminder.dueAtMillis,
                createdAt = reminder.createdAtMillis,
                firedAt = reminder.firedAtMillis,
                status = reminder.status,
            ),
        )
    }

    override suspend fun get(id: String): LocalReminder? = dao.get(id)?.toModel()

    override suspend fun listPending(): List<LocalReminder> = dao.listPending().map { it.toModel() }

    override suspend fun listAll(): List<LocalReminder> = dao.listAll().map { it.toModel() }

    override suspend fun markFired(id: String, nowMillis: Long): Boolean =
        dao.markFired(id, nowMillis) > 0

    override suspend fun markCancelled(id: String): Boolean = dao.markCancelled(id) > 0
}
