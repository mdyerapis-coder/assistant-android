package com.mdyerapis.sable.reminders

import com.mdyerapis.sable.core.database.reminder.ReminderNotifier
import com.mdyerapis.sable.core.database.reminder.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderBindings {
    @Binds
    @Singleton
    abstract fun bindScheduler(impl: WorkManagerReminderScheduler): ReminderScheduler

    @Binds
    @Singleton
    abstract fun bindNotifier(impl: AndroidReminderNotifier): ReminderNotifier
}
