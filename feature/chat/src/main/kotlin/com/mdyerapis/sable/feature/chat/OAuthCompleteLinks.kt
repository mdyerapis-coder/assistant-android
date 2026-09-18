package com.mdyerapis.sable.feature.chat

/**
 * Google OAuth return URIs.
 *
 * The live backend (`oauth_google.py` `DEEPLINK_SCHEME` / `DEEPLINK_HOST`)
 * redirects the Custom Tab to `assistantapp://oauth-complete`. Historical
 * installs and docs used `sableapp://oauth-complete`. The app listens for
 * both so Connect Google cannot miss the return.
 *
 * `applicationId` stays `com.mdyerapis.sable`: changing it would install as
 * a new app (lost Room/Keystore state) and break the FCM Android app
 * registered as `com.mdyerapis.sable` in `google-services.json`.
 */
object OAuthCompleteLinks {
    const val SCHEME = "assistantapp"
    const val SCHEME_LEGACY = "sableapp"
    const val HOST = "oauth-complete"
    const val URI = "$SCHEME://$HOST"
    const val URI_LEGACY = "$SCHEME_LEGACY://$HOST"

    fun matches(scheme: String?, host: String?): Boolean =
        host.equals(HOST, ignoreCase = true) &&
            (
                scheme.equals(SCHEME, ignoreCase = true) ||
                    scheme.equals(SCHEME_LEGACY, ignoreCase = true)
                )
}
