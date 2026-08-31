package com.mdyerapis.assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mdyerapis.assistant.core.designsystem.theme.AssistantTheme
import com.mdyerapis.assistant.feature.chat.ExternalIntake
import com.mdyerapis.assistant.feature.chat.GoogleAccountManager
import com.mdyerapis.assistant.feature.chat.GoogleOAuthCompletionNotifier
import com.mdyerapis.assistant.fcm.SmsPermissionRationaleDialog
import com.mdyerapis.assistant.fcm.SmsRelayController
import com.mdyerapis.assistant.nav.AppNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var googleOAuthCompletionNotifier: GoogleOAuthCompletionNotifier
    @Inject
    lateinit var smsRelayController: SmsRelayController
    @Inject
    lateinit var externalIntake: ExternalIntake

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOAuthDeepLink(intent)
        handleShareAndDeepLink(intent)
        setContent {
            AssistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavHost()
                    SmsPermissionRationaleDialog(
                        visible = intent?.getBooleanExtra(
                            SmsRelayController.EXTRA_SMS_PERMISSION, false
                        ) ?: false,
                        onDismiss = { intent?.removeExtra(SmsRelayController.EXTRA_SMS_PERMISSION) },
                        onGranted = { smsRelayController.retryPending() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthDeepLink(intent)
        handleShareAndDeepLink(intent)
    }

    /**
     * Handle the assistantapp://oauth-complete deep link that Google's
     * OAuth flow redirects to after the backend completes the token
     * exchange. We just refresh the connection status; the Custom Tab
     * closes itself when the app comes to the foreground.
     */
    private fun handleOAuthDeepLink(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (data.toString() != GoogleAccountManager.OAUTH_COMPLETE_URI) return
        googleOAuthCompletionNotifier.notifyCompletion()
        intent.action = null
        intent.data = null
    }

    /**
     * Share-sheet intake (ACTION_SEND) and deep links
     * (assistant://session/{id}). Both are queued on [externalIntake]
     * and consumed by the chat screen.
     */
    private fun handleShareAndDeepLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!shared.isNullOrBlank()) {
                externalIntake.offerSharedText(shared)
            }
            intent.action = null
        }
        val data: Uri = intent?.data ?: return
        if (data.scheme == "assistant" && data.host == "session") {
            data.pathSegments.firstOrNull()?.let { id ->
                externalIntake.offerOpenConversation(id)
            }
            intent.data = null
        }
    }
}
