package com.mdyerapis.sable.core.database.reminder

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val text: String,
    val dueAt: Long,
    val createdAt: Long,
    val firedAt: Long? = null,
    val status: String,
)

data class LocalReminder(
    val id: String,
    val text: String,
    val dueAtMillis: Long,
    val createdAtMillis: Long,
    val firedAtMillis: Long? = null,
    val status: String,
) {
    val isPending: Boolean get() = status == STATUS_PENDING

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_FIRED = "fired"
        const val STATUS_CANCELLED = "cancelled"
    }
}

fun ReminderEntity.toModel(): LocalReminder = LocalReminder(
    id = id,
    text = text,
    dueAtMillis = dueAt,
    createdAtMillis = createdAt,
    firedAtMillis = firedAt,
    status = status,
)
