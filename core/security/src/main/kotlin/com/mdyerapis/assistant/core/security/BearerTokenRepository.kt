package com.mdyerapis.assistant.core.security

import android.content.Context

open class BearerTokenRepository(context: Context) {
    private val store by lazy { KeystoreSecretStore(context) }
    private val prefs by lazy { context.getSharedPreferences("bearer_prefs", Context.MODE_PRIVATE) }
    private val key = "bearer_token"

    open fun saveToken(token: String) {
        prefs.edit().putString(key, store.encrypt(token)).apply()
    }

    open fun getToken(): String? {
        val encrypted = prefs.getString(key, null) ?: return null
        return store.decrypt(encrypted)
    }

    open fun clearToken() {
        prefs.edit().remove(key).apply()
    }

    open suspend fun verifyToken(baseUrl: String): Boolean {
        return getToken() != null
    }
}
