package com.mdyerapis.assistant.core.database.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE serverConversationId = :serverId LIMIT 1")
    suspend fun getByServerId(serverId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    @Query("UPDATE conversations SET preview = :preview, updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, preview: String, updatedAt: Long)

    @Query("UPDATE conversations SET serverConversationId = :serverConversationId WHERE id = :id")
    suspend fun setServerId(id: String, serverConversationId: String?)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM conversations")
    suspend fun clear()
}
