package com.mdyerapis.assistant.fcm

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.mdyerapis.assistant.backendclient.DeviceTokenApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the current FCM token and registers it with the backend.
 * Called on app startup and whenever Firebase issues a new token.
 */
@Singleton
class DeviceTokenRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceTokenApi: DeviceTokenApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun registerCurrentToken() {
        scope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                register(token)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get FCM token: ${e.message}")
            }
        }
    }

    fun register(token: String) {
        scope.launch {
            val deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID,
            )
            val ok = deviceTokenApi.registerToken(token, deviceId)
            if (ok) {
                Log.i(TAG, "FCM token registered with backend")
            } else {
                Log.w(TAG, "Failed to register FCM token with backend")
            }
        }
    }

    companion object {
        private const val TAG = "DeviceTokenRegistrar"
    }
}
