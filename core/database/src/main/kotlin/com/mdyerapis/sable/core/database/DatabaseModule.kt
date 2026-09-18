package com.mdyerapis.sable.core.database

import android.content.Context
import com.mdyerapis.sable.core.database.chat.ChatDatabase
import com.mdyerapis.sable.core.database.chat.ConversationRepository
import com.mdyerapis.sable.core.database.chat.ConversationStore
import com.mdyerapis.sable.core.database.automation.AutomationStore
import com.mdyerapis.sable.core.database.automation.RoomAutomationStore
import com.mdyerapis.sable.core.database.reminder.ReminderStore
import com.mdyerapis.sable.core.database.reminder.RoomReminderStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideChatDatabase(@ApplicationContext context: Context): ChatDatabase =
        ChatDatabase.create(context)

    @Provides
    @Singleton
    fun provideConversationRepository(db: ChatDatabase): ConversationRepository =
        ConversationRepository(db)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ConversationStoreModule {
    @Binds
    @Singleton
    abstract fun bindConversationStore(impl: ConversationRepository): ConversationStore

    @Binds
    @Singleton
    abstract fun bindReminderStore(impl: RoomReminderStore): ReminderStore

    @Binds
    @Singleton
    abstract fun bindAutomationStore(impl: RoomAutomationStore): AutomationStore
}
