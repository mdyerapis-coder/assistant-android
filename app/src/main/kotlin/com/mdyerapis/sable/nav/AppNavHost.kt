package com.mdyerapis.sable.nav

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import com.mdyerapis.sable.core.security.BearerTokenRepository
import com.mdyerapis.sable.feature.chat.ChatScreen
import com.mdyerapis.sable.feature.chat.SessionsScreen
import com.mdyerapis.sable.feature.chat.SettingsScreen
import com.mdyerapis.sable.feature.onboarding.OnboardingScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

private const val TRANSITION_DURATION = 300

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val gateViewModel: NavGateViewModel = hiltViewModel()

    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        startDestination = if (gateViewModel.hasToken()) "sessions" else "onboarding"
    }

    val resolved = startDestination ?: return

    val intakeViewModel: NavIntakeViewModel = hiltViewModel()
    LaunchedEffect(intakeViewModel) {
        intakeViewModel.intake.events.collect { event ->
            when (event) {
                is com.mdyerapis.sable.feature.chat.ExternalIntake.IntakeEvent.SharedText,
                is com.mdyerapis.sable.feature.chat.ExternalIntake.IntakeEvent.OpenConversation -> {
                    navController.navigate("chat") { launchSingleTop = true }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = resolved) {
        composable("onboarding") {
            OnboardingScreen(onTokenAccepted = {
                navController.navigate("chat") {
                    popUpTo("onboarding") { inclusive = true }
                }
            })
        }
        composable(
            "chat",
            enterTransition = { slideInHorizontally(tween(TRANSITION_DURATION, easing = FastOutSlowInEasing)) { it } },
            exitTransition = { slideOutHorizontally(tween(TRANSITION_DURATION, easing = FastOutSlowInEasing)) { -it } },
            popEnterTransition = { slideInHorizontally(tween(TRANSITION_DURATION, easing = FastOutSlowInEasing)) { -it } },
            popExitTransition = { slideOutHorizontally(tween(TRANSITION_DURATION, easing = FastOutSlowInEasing)) { it } }
        ) {
            ChatScreen(
                onNavigateSettings = { navController.navigate("settings") },
                onNavigateSessions = { navController.navigate("sessions") },
                onReconfigure = {
                    navController.navigate("onboarding") { popUpTo(0) { inclusive = true } }
                }
            )
        }
        composable(
            "sessions",
            enterTransition = { slideInHorizontally(tween(TRANSITION_DURATION, easing = FastOutSlowInEasing)) { it } },
            exitTransition = { slideOutHorizontally(tween(TRANSITION_DURATION, easing = FastOutSlowInEasing)) { -it } },
            popEnterTransition = { slideInHorizontally(tween(TRANSITION_DURATION, easing = FastOutSlowInEasing)) { -it } },
            popExitTransition = { slideOutHorizontally(tween(TRANSITION_DURATION, easing = FastOutSlowInEasing)) { it } }
        ) {
            SessionsScreen(
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate("chat") { launchSingleTop = true }
                    }
                },
                onOpenConversation = {
                    navController.navigate("chat") { launchSingleTop = true }
                }
            )
        }
        composable(
            "settings",
            enterTransition = { slideInHorizontally(tween(TRANSITION_DURATION, easing = FastOutSlowInEasing)) { it } },
            exitTransition = { slideOutHorizontally(tween(TRANSITION_DURATION, easing = FastOutSlowInEasing)) { -it } },
            popEnterTransition = { slideInHorizontally(tween(TRANSITION_DURATION, easing = FastOutSlowInEasing)) { -it } },
            popExitTransition = { slideOutHorizontally(tween(TRANSITION_DURATION, easing = FastOutSlowInEasing)) { it } }
        ) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@HiltViewModel
class NavIntakeViewModel @Inject constructor(
    val intake: com.mdyerapis.sable.feature.chat.ExternalIntake,
) : ViewModel()

@HiltViewModel
class NavGateViewModel @Inject constructor(
    private val tokenRepository: BearerTokenRepository,
) : ViewModel() {
    fun hasToken(): Boolean = tokenRepository.getToken() != null
}
