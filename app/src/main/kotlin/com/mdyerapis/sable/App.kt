package com.mdyerapis.sable

import android.app.Application
import com.mdyerapis.sable.core.security.BearerTokenRepository
import com.mdyerapis.sable.backendclient.DeviceTokenRegistrar
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {

    @Inject
    lateinit var deviceTokenRegistrar: DeviceTokenRegistrar

    @Inject
    lateinit var bearerTokenRepository: BearerTokenRepository

    override fun onCreate() {
        super.onCreate()
        // Register the FCM device token with the backend on startup.
        // Only if onboarding is already complete (bearer token saved).
        // Fresh installs register the token after onboarding completes
        // (see OnboardingViewModel.submit).
        // The token is also re-registered automatically whenever Firebase
        // issues a new one (see AssistantMessagingService.onNewToken).
        if (bearerTokenRepository.getToken() != null) {
            deviceTokenRegistrar.registerCurrentToken()
        }
    }
}
