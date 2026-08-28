package com.mdyerapis.assistant.core.database.chat

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        fun create(context: Context): ChatDatabase =
            Room.databaseBuilder(context, ChatDatabase::class.java, "assistant_chat.db")
                .fallbackToDestructiveMigration(false)
                .build()
    }
}
