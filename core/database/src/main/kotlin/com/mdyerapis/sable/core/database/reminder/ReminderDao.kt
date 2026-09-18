package com.mdyerapis.sable.core.database.reminder

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE status = 'pending' ORDER BY dueAt ASC")
    suspend fun listPending(): List<ReminderEntity>

    @Query("SELECT * FROM reminders ORDER BY dueAt ASC")
    suspend fun listAll(): List<ReminderEntity>

    @Query(
        """
        UPDATE reminders SET status = 'fired', firedAt = :now
        WHERE id = :id AND status = 'pending'
        """,
    )
    suspend fun markFired(id: String, now: Long): Int

    @Query(
        """
        UPDATE reminders SET status = 'cancelled'
        WHERE id = :id AND status = 'pending'
        """,
    )
    suspend fun markCancelled(id: String): Int
}
