package com.mdyerapis.sable.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.media.AudioAttributes
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mdyerapis.sable.backendclient.DeviceTokenRegistrar
import com.mdyerapis.sable.R
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
        // A versioned channel ensures existing installs pick up Sable's sound.
        // Android intentionally freezes a channel's sound after creation.
        val channelId = "sable_reminders_v2"
        val sound = Uri.parse("android.resource://$packageName/${R.raw.notification_chirp}")
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Sable reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setSound(sound, audioAttributes)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setSound(sound)
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
