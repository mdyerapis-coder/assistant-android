package com.mdyerapis.sable.feature.chat

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.mdyerapis.sable.core.security.BearerTokenRepository
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
 * - [status] queries the O1 relay for whether Google is connected
 * - [connect] launches the OAuth flow in a Chrome Custom Tab
 * - [disconnect] revokes the stored tokens on the relay
 *
 * The relay handles all the OAuth dance (see docs/adr/007); the phone
 * just opens a Custom Tab at /oauth/google/start and gets deep-linked
 * back via assistantapp://oauth-complete (sableapp:// alias also accepted)
 * when the flow finishes.
 * The Google client_secret never lives in this APK.
 */
@Singleton
open class GoogleAccountManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    @Volatile
    private var oauthRelayUrl: String = BearerTokenRepository.DEFAULT_OAUTH_RELAY_URL

    open fun configureOauthRelayUrl(url: String) {
        oauthRelayUrl = url.trim().trimEnd('/').ifBlank {
            BearerTokenRepository.DEFAULT_OAUTH_RELAY_URL
        }
    }

    /** @deprecated Use [configureOauthRelayUrl]; kept so older call sites compile. */
    open fun configureBaseUrl(url: String) = configureOauthRelayUrl(url)

    open fun oauthStartUrl(): String = "$oauthRelayUrl/oauth/google/start"

    open fun configuredOauthRelayUrl(): String = oauthRelayUrl

    open suspend fun status(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$oauthRelayUrl/oauth/google/status")
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
     * Launch the OAuth flow in a Chrome Custom Tab. The relay redirects
     * to Google's consent screen, then back to /oauth/google/callback,
     * which itself redirects to assistantapp://oauth-complete
     * (legacy sableapp://oauth-complete is still accepted).
     *
     * Note: we launch from the Application context with FLAG_ACTIVITY_NEW_TASK
     * because this is called from a ViewModel that doesn't hold an Activity
     * reference. The Custom Tab will be brought to the foreground; our app's
     * MainActivity is still in the back stack and gets resumed when the user
     * finishes (or dismisses) the Custom Tab.
     */
    fun launchOAuthFlow() {
        val uri = oauthStartUrl().toUri()
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .build()
        intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.launchUrl(context, uri)
    }

    suspend fun disconnect(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$oauthRelayUrl/oauth/google")
                .delete()
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val OAUTH_COMPLETE_URI: String = OAuthCompleteLinks.URI
    }
}
