package com.mdyerapis.sable.core.database.automation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AutomationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AutomationEntity)

    @Query("SELECT * FROM automations WHERE id = :id LIMIT 1")
    suspend fun get(id: String): AutomationEntity?

    @Query("SELECT * FROM automations WHERE enabled = 1 ORDER BY createdAt ASC")
    suspend fun listEnabled(): List<AutomationEntity>

    @Query("SELECT * FROM automations ORDER BY createdAt ASC")
    suspend fun listAll(): List<AutomationEntity>

    @Query(
        """
        UPDATE automations SET lastFiredAt = :now
        WHERE id = :id AND enabled = 1
          AND (lastFiredAt IS NULL OR lastFiredAt < :now - 2000)
        """,
    )
    suspend fun markFired(id: String, now: Long): Int

    @Query(
        """
        UPDATE automations SET enabled = 0
        WHERE id = :id AND enabled = 1
        """,
    )
    suspend fun markDisabled(id: String): Int
}
