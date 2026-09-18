package com.mdyerapis.sable.sms

import com.mdyerapis.sable.core.model.SmsOperations
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SmsBindings {
    @Binds
    @Singleton
    abstract fun bindSmsOperations(impl: AndroidSmsOperations): SmsOperations
}
