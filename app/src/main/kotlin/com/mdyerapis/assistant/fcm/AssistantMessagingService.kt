package com.mdyerapis.assistant.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mdyerapis.assistant.backendclient.DeviceTokenRegistrar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives FCM token updates and push notifications from the backend
 * reminder scheduler. The token is registered with the backend via
 * DeviceTokenRegistrar on startup and whenever it refreshes.
 */
@AndroidEntryPoint
class AssistantMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var deviceTokenRegistrar: DeviceTokenRegistrar
    @Inject
    lateinit var smsRelayController: SmsRelayController

    override fun onNewToken(token: String) {
        Log.i(TAG, "New FCM token: ${token.take(10)}...")
        deviceTokenRegistrar.register(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.i(TAG, "FCM message received: ${message.messageId}")
        val action = message.data["action"]
        if (action == SmsRelayController.ACTION_SEND ||
            action == SmsRelayController.ACTION_READ
        ) {
            // Phase 10: SMS relay — the phone executes and reports back.
            smsRelayController.handle(action, message.data)
            return
        }
        val title = message.notification?.title ?: message.data["title"] ?: "Reminder"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH,
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        private const val TAG = "AssistantFCM"
    }
}
