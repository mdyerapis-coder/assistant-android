package com.mdyerapis.assistant

import android.app.Application
import com.mdyerapis.assistant.fcm.DeviceTokenRegistrar
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {

    @Inject
    lateinit var deviceTokenRegistrar: DeviceTokenRegistrar

    override fun onCreate() {
        super.onCreate()
        // Register the FCM device token with the backend on startup.
        // The token is also re-registered automatically whenever Firebase
        // issues a new one (see AssistantMessagingService.onNewToken).
        deviceTokenRegistrar.registerCurrentToken()
    }
}
