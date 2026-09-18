package com.mdyerapis.sable.feature.localmodel

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalReminderModule {
    @Binds
    @Singleton
    abstract fun bindLocalReminderGateway(impl: DefaultLocalReminderGateway): LocalReminderGateway

    @Binds
    @Singleton
    abstract fun bindLocalAutomationGateway(impl: DefaultLocalAutomationGateway): LocalAutomationGateway
}
