package com.mdyerapis.sable.core.security

import android.content.Context

open class BearerTokenRepository(context: Context) {
    private val store by lazy { KeystoreSecretStore(context) }
    private val prefs by lazy { context.getSharedPreferences("bearer_prefs", Context.MODE_PRIVATE) }
    private val key = "bearer_token"
    private val baseUrlKey = "base_url"
    private val oauthRelayUrlKey = "oauth_relay_url"
    private val onDeviceAccessKey = "on_device_access"

    open fun saveBaseUrl(baseUrl: String) {
        prefs.edit().putString(baseUrlKey, baseUrl).apply()
    }

    open fun getBaseUrl(): String? = prefs.getString(baseUrlKey, null)

    open fun clearBaseUrl() {
        prefs.edit().remove(baseUrlKey).apply()
    }

    /**
     * ADR-013 O1: Calendar/Gmail Custom Tab + tool turns always hit this
     * host, even when [getBaseUrl] is loopback or unused (pure on-device chat).
     */
    open fun saveOauthRelayUrl(url: String) {
        prefs.edit().putString(oauthRelayUrlKey, url.trim().trimEnd('/')).apply()
    }

    open fun getOauthRelayUrl(): String {
        val stored = prefs.getString(oauthRelayUrlKey, null)?.trim()?.trimEnd('/')
        return stored?.takeIf { it.isNotBlank() } ?: DEFAULT_OAUTH_RELAY_URL
    }

    open fun clearOauthRelayUrl() {
        prefs.edit().remove(oauthRelayUrlKey).apply()
    }

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

    /**
     * Onboarding can skip the cloud bearer check (ADR-012 Option C).
     * Does not imply a model is downloaded — only that the user may
     * enter the app and use the MediaPipe path.
     */
    open fun hasOnDeviceAccess(): Boolean = prefs.getBoolean(onDeviceAccessKey, false)

    open fun setOnDeviceAccess(enabled: Boolean) {
        prefs.edit().putBoolean(onDeviceAccessKey, enabled).apply()
    }

    open suspend fun verifyToken(baseUrl: String): Boolean {
        return getToken() != null
    }

    companion object {
        const val DEFAULT_OAUTH_RELAY_URL = "https://assistant.llmclouds.au"
    }
}
