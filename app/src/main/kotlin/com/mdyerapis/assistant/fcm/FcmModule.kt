package com.mdyerapis.assistant.fcm

import android.content.Context
import com.mdyerapis.assistant.backendclient.DeviceTokenApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FcmModule {

    /**
     * Hardcoded for now — the backend URL should be configurable
     * (e.g. via a build config field or Settings). Matches the
     * production assistant.llmclouds.au endpoint.
     */
    private const val BACKEND_BASE_URL = "https://assistant.llmclouds.au"

    @Provides
    @Singleton
    fun provideDeviceTokenApi(client: OkHttpClient): DeviceTokenApi =
        DeviceTokenApi(client, BACKEND_BASE_URL)
}
