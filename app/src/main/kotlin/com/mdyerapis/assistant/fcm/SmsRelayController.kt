package com.mdyerapis.assistant.fcm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.mdyerapis.assistant.backendclient.SmsRelayApi
import com.mdyerapis.assistant.core.network.OkHttpClientFactory
import com.mdyerapis.assistant.core.security.BearerTokenRepository
import com.mdyerapis.assistant.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phone-side SMS relay (phase 10). Executes `send_sms` / `read_sms` FCM
 * data actions and reports the outcome to the backend's
 * `POST /v1/sms/results` (see docs/CONTRACT.md "SMS relay").
 *
 * Request-driven only: SMS is touched exclusively when an FCM action
 * arrives — no background upload, no inbox mirroring, no periodic sync.
 * If the app lacks SEND_SMS/READ_SMS permission, the failure is reported
 * to the backend, the action is retained for retry, and a notification
 * guides the user to grant permission in-app.
 */
@Singleton
class SmsRelayController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenRepository: BearerTokenRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pending = PendingHolder()

    /** Called from the FCM service on an SMS data action. */
    fun handle(action: String, data: Map<String, String>) {
        when (action) {
            ACTION_SEND, ACTION_READ -> scope.launch { execute(action, data) }
            else -> Log.w(TAG, "Unknown SMS relay action: $action")
        }
    }

    /** Re-run the last action after the user grants SMS permission. */
    fun retryPending() {
        val saved = pending.take() ?: return
        scope.launch { execute(saved.action, saved.data) }
    }

    private suspend fun execute(action: String, data: Map<String, String>) {
        val requestId = data["request_id"] ?: return
        val api = buildRelayApi()
        if (api == null) {
            Log.w(TAG, "SMS relay skipped: no backend configured")
            return
        }

        if (!hasSmsPermission()) {
            pending.set(action, data)
            report(api, SmsRelayApi.SmsResultRequest(
                request_id = requestId,
                ok = false,
                error = "sms permission not granted on the phone",
            ))
            notifyPermissionRequired()
            return
        }

        try {
            when (action) {
                ACTION_SEND -> executeSend(api, requestId, data)
                ACTION_READ -> executeRead(api, requestId, data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "SMS relay action failed", e)
            report(api, SmsRelayApi.SmsResultRequest(
                request_id = requestId,
                ok = false,
                error = e.message ?: "sms relay failed",
            ))
        }
    }

    private suspend fun executeSend(
        api: SmsRelayApi,
        requestId: String,
        data: Map<String, String>,
    ) {
        val phone = data["phone"] ?: throw IllegalArgumentException("send_sms missing phone")
        val message = data["message"] ?: throw IllegalArgumentException("send_sms missing message")
        withContext(Dispatchers.IO) {
            SmsManager.getDefault().sendTextMessage(phone, null, message, null, null)
        }
        report(api, SmsRelayApi.SmsResultRequest(
            request_id = requestId,
            ok = true,
        ))
        Log.i(TAG, "SMS sent to $phone (request $requestId)")
    }

    private suspend fun executeRead(
        api: SmsRelayApi,
        requestId: String,
        data: Map<String, String>,
    ) {
        val phoneFilter = data["phone"]?.takeIf { it.isNotBlank() }
        val limit = data["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 10
        val messages = withContext(Dispatchers.IO) {
            queryInbox(phoneFilter, limit)
        }
        report(api, SmsRelayApi.SmsResultRequest(
            request_id = requestId,
            ok = true,
            messages = messages,
        ))
        Log.i(TAG, "SMS read returned ${messages.size} messages (request $requestId)")
    }

    private fun queryInbox(phoneFilter: String?, limit: Int): List<SmsRelayApi.SmsResultMessage> {
        val resolver = context.contentResolver
        val projection = arrayOf(
            Telephony.Sms.Inbox.ADDRESS,
            Telephony.Sms.Inbox.BODY,
            Telephony.Sms.Inbox.DATE,
        )
        val selection = phoneFilter?.let {
            "${Telephony.Sms.Inbox.ADDRESS} LIKE ?"
        }
        val selectionArgs = phoneFilter?.let { arrayOf("%$it%") }
        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val results = mutableListOf<SmsRelayApi.SmsResultMessage>()
        resolver.query(
            uri, projection, selection, selectionArgs,
            "${Telephony.Sms.Inbox.DATE} DESC",
        )?.use { cursor ->
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.ADDRESS))
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.BODY))
                val dateMs = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.Inbox.DATE))
                results.add(
                    SmsRelayApi.SmsResultMessage(
                        from_number = address,
                        message = body,
                        received_at = ISO_FORMAT.format(Date(dateMs)),
                    )
                )
                count++
            }
        }
        return results
    }

    private fun hasSmsPermission(): Boolean {
        val send = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
        return send == PackageManager.PERMISSION_GRANTED &&
            read == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun report(api: SmsRelayApi, request: SmsRelayApi.SmsResultRequest) {
        try {
            api.reportResult(request)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report SMS result", e)
        }
    }

    private fun buildRelayApi(): SmsRelayApi? {
        val token = tokenRepository.getToken() ?: return null
        val baseUrl = tokenRepository.getBaseUrl() ?: return null
        val client = OkHttpClientFactory.create().newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                )
            }
            .build()
        return SmsRelayApi(client, baseUrl)
    }

    private fun notifyPermissionRequired() {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val channelId = "sms_relay"
        val notification = android.app.Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Assistant needs SMS permission")
            .setContentText("Open the app to allow SMS relay")
            .setAutoCancel(true)
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    context,
                    0,
                    android.content.Intent(context, MainActivity::class.java)
                        .putExtra(EXTRA_SMS_PERMISSION, true),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
        manager.notify(EXTRA_SMS_PERMISSION.hashCode(), notification)
    }

    /** Retains the most recent action for retry after permission grant. */
    class PendingHolder {
        private var action: String? = null
        private var data: Map<String, String> = emptyMap()

        @Synchronized
        fun set(action: String, data: Map<String, String>) {
            this.action = action
            this.data = data
        }

        @Synchronized
        fun take(): PendingAction? {
            val a = action ?: return null
            val d = data
            action = null
            data = emptyMap()
            return PendingAction(a, d)
        }
    }

    data class PendingAction(val action: String, val data: Map<String, String>)

    companion object {
        const val ACTION_SEND = "send_sms"
        const val ACTION_READ = "read_sms"
        const val EXTRA_SMS_PERMISSION = "sms_permission_requested"

        private const val TAG = "SmsRelay"
        private val ISO_FORMAT: SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
    }
}
