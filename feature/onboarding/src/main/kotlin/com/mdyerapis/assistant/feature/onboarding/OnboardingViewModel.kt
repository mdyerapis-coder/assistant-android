package com.mdyerapis.assistant.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdyerapis.assistant.backendclient.ChatApiClient
import com.mdyerapis.assistant.backendclient.DeviceTokenRegistrar
import com.mdyerapis.assistant.core.network.OkHttpClientFactory
import com.mdyerapis.assistant.core.security.BearerTokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class OnboardingUiState(
    val token: String = "",
    val baseUrl: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isDone: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val tokenRepository: BearerTokenRepository,
    private val deviceTokenRegistrar: DeviceTokenRegistrar,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState

    fun updateToken(token: String) {
        _uiState.value = _uiState.value.copy(token = token, error = null)
    }

    fun updateBaseUrl(baseUrl: String) {
        _uiState.value = _uiState.value.copy(baseUrl = baseUrl, error = null)
    }

    fun submit() {
        val state = _uiState.value
        if (state.token.isBlank() || state.baseUrl.isBlank()) {
            _uiState.value = state.copy(error = "Token and base URL are required")
            return
        }

        _uiState.value = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val client = OkHttpClientFactory.create().newBuilder()
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .addHeader("Authorization", "Bearer ${state.token}")
                                .build()
                        )
                    }
                    .build()
                val api = ChatApiClient(client, state.baseUrl)
                val healthy = withContext(Dispatchers.IO) { api.checkHealth() }
                if (healthy) {
                    tokenRepository.saveToken(state.token)
                    tokenRepository.saveBaseUrl(state.baseUrl)
                    // Register the FCM device token with the backend now
                    // that we have a valid bearer token.
                    deviceTokenRegistrar.registerCurrentToken()
                    _uiState.value = _uiState.value.copy(isLoading = false, isDone = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Server returned an error. Check your token and URL.",
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Connection failed: ${e.message}",
                )
            }
        }
    }
}
