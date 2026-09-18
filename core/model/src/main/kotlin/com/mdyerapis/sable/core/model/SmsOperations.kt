package com.mdyerapis.sable.core.model

/**
 * Phone SMS send/read without a FastAPI/FCM hop. The Android
 * implementation lives in `:app`; tests and the local chat gateway
 * depend only on this interface.
 */
data class DeviceSmsMessage(
    val fromNumber: String,
    val body: String,
    val receivedAtMillis: Long,
)

interface SmsOperations {
    fun hasSendPermission(): Boolean
    fun hasReadPermission(): Boolean
    suspend fun send(phone: String, message: String)
    suspend fun readInbox(phoneFilter: String?, limit: Int): List<DeviceSmsMessage>
}

object NoOpSmsOperations : SmsOperations {
    override fun hasSendPermission(): Boolean = false
    override fun hasReadPermission(): Boolean = false
    override suspend fun send(phone: String, message: String) {
        throw UnsupportedOperationException("SMS is not available in this process")
    }
    override suspend fun readInbox(phoneFilter: String?, limit: Int): List<DeviceSmsMessage> =
        emptyList()
}
