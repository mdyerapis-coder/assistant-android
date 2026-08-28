package com.mdyerapis.assistant.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mdyerapis.assistant.core.security.BearerTokenRepository
import com.mdyerapis.assistant.feature.chat.ChatScreen
import com.mdyerapis.assistant.feature.onboarding.OnboardingScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Root nav graph. Decides initial destination based on whether a bearer
 * token is already persisted in the Android Keystore — the assistant
 * only shows the onboarding screen on first launch (or after the user
 * explicitly clears app data), never again.
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val gateViewModel: NavGateViewModel = hiltViewModel()

    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        startDestination = if (gateViewModel.hasToken()) "chat" else "onboarding"
    }

    val resolved = startDestination ?: return

    NavHost(navController = navController, startDestination = resolved) {
        composable("onboarding") {
            OnboardingScreen(onTokenAccepted = {
                navController.navigate("chat") {
                    popUpTo("onboarding") { inclusive = true }
                }
            })
        }
        composable("chat") {
            ChatScreen()
        }
    }
}

@HiltViewModel
class NavGateViewModel @Inject constructor(
    private val tokenRepository: BearerTokenRepository,
) : ViewModel() {
    fun hasToken(): Boolean = tokenRepository.getToken() != null
}
