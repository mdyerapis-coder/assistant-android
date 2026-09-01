package com.mdyerapis.sable.feature.chat

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class AppModelMode {
    Backend,
    OnDevice
}

@Singleton
class ModelPreferenceRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("model_preferences", Context.MODE_PRIVATE)

    private val _appModelMode = MutableStateFlow(getAppModelMode())
    val appModelMode: StateFlow<AppModelMode> = _appModelMode.asStateFlow()

    fun getAppModelMode(): AppModelMode {
        val saved = preferences.getString("app_model_mode", AppModelMode.Backend.name)
        return try {
            AppModelMode.valueOf(saved ?: AppModelMode.Backend.name)
        } catch (_: Exception) {
            AppModelMode.Backend
        }
    }

    fun setAppModelMode(mode: AppModelMode) {
        preferences.edit().putString("app_model_mode", mode.name).apply()
        _appModelMode.value = mode
    }

    fun getSelectedModelId(): String? = preferences.getString("selected_model_id", null)

    fun setSelectedModelId(modelId: String?) {
        preferences.edit().apply {
            if (modelId == null) remove("selected_model_id") else putString("selected_model_id", modelId)
        }.apply()
    }
}
