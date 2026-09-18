package com.mdyerapis.sable.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.mdyerapis.sable.core.model.DeviceSmsMessage
import com.mdyerapis.sable.core.model.SmsOperations
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-process SMS used by on-device chat (P2) and by the cloud FCM relay.
 * Neither path talks to FastAPI to actually send or read.
 */
@Singleton
class AndroidSmsOperations @Inject constructor(
    @ApplicationContext private val context: Context,
) : SmsOperations {
    override fun hasSendPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    override fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun send(phone: String, message: String) {
        withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            SmsManager.getDefault().sendTextMessage(phone, null, message, null, null)
        }
    }

    override suspend fun readInbox(phoneFilter: String?, limit: Int): List<DeviceSmsMessage> =
        withContext(Dispatchers.IO) {
            queryInbox(phoneFilter, limit)
        }

    private fun queryInbox(phoneFilter: String?, limit: Int): List<DeviceSmsMessage> {
        val resolver = context.contentResolver
        val projection = arrayOf(
            Telephony.Sms.Inbox.ADDRESS,
            Telephony.Sms.Inbox.BODY,
            Telephony.Sms.Inbox.DATE,
        )
        val selection = phoneFilter?.let { "${Telephony.Sms.Inbox.ADDRESS} LIKE ?" }
        val selectionArgs = phoneFilter?.let { arrayOf("%$it%") }
        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val results = mutableListOf<DeviceSmsMessage>()
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
                    DeviceSmsMessage(
                        fromNumber = address ?: "",
                        body = body ?: "",
                        receivedAtMillis = dateMs,
                    ),
                )
                count++
            }
        }
        return results
    }
}
