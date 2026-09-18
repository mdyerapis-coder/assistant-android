package com.mdyerapis.sable.feature.localmodel

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalSmsModule {
    @Binds
    @Singleton
    abstract fun bindLocalSmsGateway(impl: DefaultLocalSmsGateway): LocalSmsGateway
}
