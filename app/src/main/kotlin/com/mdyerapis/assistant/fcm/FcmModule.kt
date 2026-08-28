package com.mdyerapis.assistant.fcm

import com.mdyerapis.assistant.backendclient.DeviceTokenApi
import com.mdyerapis.assistant.core.network.BearerAuthInterceptor
import com.mdyerapis.assistant.core.security.BearerTokenRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
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
    fun provideFcmOkHttpClient(tokenRepository: BearerTokenRepository): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(BearerAuthInterceptor { tokenRepository.getToken() })
            .build()

    @Provides
    @Singleton
    fun provideDeviceTokenApi(client: OkHttpClient): DeviceTokenApi =
        DeviceTokenApi(client, BACKEND_BASE_URL)
}
