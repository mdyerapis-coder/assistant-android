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
     * Production fallback used only when onboarding has not configured a
     * server yet (no saved baseUrl). Device-token registration and SMS
     * relay must target the server the user onboarded with, so the
     * persisted baseUrl wins when present.
     */
    private const val DEFAULT_BACKEND_BASE_URL = "https://assistant.llmclouds.au"

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
    fun provideDeviceTokenApi(
        client: OkHttpClient,
        tokenRepository: BearerTokenRepository,
    ): DeviceTokenApi {
        val baseUrl = tokenRepository.getBaseUrl()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_BACKEND_BASE_URL
        return DeviceTokenApi(client, baseUrl)
    }
}
