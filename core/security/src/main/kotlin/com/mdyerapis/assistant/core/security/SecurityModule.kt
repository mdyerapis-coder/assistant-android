package com.mdyerapis.assistant.core.security

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    @Provides
    @Singleton
    fun provideBearerTokenRepository(@ApplicationContext context: Context): BearerTokenRepository =
        BearerTokenRepository(context)
}
