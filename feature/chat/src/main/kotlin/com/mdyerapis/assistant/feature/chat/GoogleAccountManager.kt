package com.mdyerapis.assistant.feature.chat

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the Google OAuth connection state for the user.
 *
 * - [status] queries the backend for whether Google is connected
 * - [connect] launches the OAuth flow in a Chrome Custom Tab
 * - [disconnect] revokes the stored tokens on the backend
 *
 * The backend handles all the OAuth dance (see docs/adr/007); the phone
 * just opens a Custom Tab at /oauth/google/start and gets deep-linked
 * back via assistantapp://oauth-complete when the flow finishes.
 */
@Singleton
class GoogleAccountManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    @Volatile
    private var baseUrl: String = "https://assistant.llmclouds.au"

    fun configureBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    suspend fun status(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/oauth/google/status")
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val body = resp.body?.string().orEmpty()
                body.contains("\"connected\":true")
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Launch the OAuth flow in a Chrome Custom Tab. The backend redirects
     * to Google's consent screen, then back to /oauth/google/callback,
     * which itself redirects to assistantapp://oauth-complete.
     */
    fun launchOAuthFlow() {
        val uri = "$baseUrl/oauth/google/start".toUri()
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .build()
        intent.launchUrl(context, uri)
    }

    suspend fun disconnect(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/oauth/google")
                .delete()
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val OAUTH_COMPLETE_URI: String = "assistantapp://oauth-complete"
    }
}
