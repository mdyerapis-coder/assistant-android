package com.mdyerapis.sable.core.database.chat

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mdyerapis.sable.core.database.automation.AutomationDao
import com.mdyerapis.sable.core.database.automation.AutomationEntity
import com.mdyerapis.sable.core.database.reminder.ReminderDao
import com.mdyerapis.sable.core.database.reminder.ReminderEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ReminderEntity::class,
        AutomationEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun reminderDao(): ReminderDao
    abstract fun automationDao(): AutomationDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS automations (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        expression TEXT NOT NULL,
                        actionText TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        intervalMillis INTEGER,
                        hour INTEGER NOT NULL,
                        minute INTEGER NOT NULL,
                        daysOfWeek TEXT,
                        createdAt INTEGER NOT NULL,
                        lastFiredAt INTEGER,
                        enabled INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        fun create(context: Context): ChatDatabase =
            Room.databaseBuilder(context, ChatDatabase::class.java, "assistant_chat.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration(false)
                .build()
    }
}
