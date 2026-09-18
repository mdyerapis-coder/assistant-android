package com.mdyerapis.sable.core.database.chat

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mdyerapis.sable.core.database.reminder.ReminderDao
import com.mdyerapis.sable.core.database.reminder.ReminderEntity

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, ReminderEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reminders (
                        id TEXT NOT NULL PRIMARY KEY,
                        text TEXT NOT NULL,
                        dueAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        firedAt INTEGER,
                        status TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        fun create(context: Context): ChatDatabase =
            Room.databaseBuilder(context, ChatDatabase::class.java, "assistant_chat.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(false)
                .build()
    }
}
