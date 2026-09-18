package com.mdyerapis.sable.reminders

import com.mdyerapis.sable.automations.WorkManagerAutomationScheduler
import com.mdyerapis.sable.core.database.automation.AutomationNotifier
import com.mdyerapis.sable.core.database.automation.AutomationScheduler
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

@Module
@InstallIn(SingletonComponent::class)
abstract class AutomationBindings {
    @Binds
    @Singleton
    abstract fun bindAutomationScheduler(impl: WorkManagerAutomationScheduler): AutomationScheduler

    @Binds
    @Singleton
    abstract fun bindAutomationNotifier(impl: AndroidReminderNotifier): AutomationNotifier
}
