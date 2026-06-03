package com.example.aimobileagent.ui.screen.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SettingsUiState(
    val apiKey: String = "",
    val apiEndpoint: String = "https://api.deepseek.com",
    val modelName: String = "deepseek-chat",
    val isAccessibilityEnabled: Boolean = false,
    val isSaved: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // 从加密存储加载已保存的设置
        _uiState.update {
            it.copy(
                apiKey = prefs.getString("api_key", "") ?: "",
                apiEndpoint = prefs.getString("api_endpoint", "https://api.deepseek.com") ?: "https://api.deepseek.com",
                modelName = prefs.getString("model_name", "deepseek-chat") ?: "deepseek-chat"
            )
        }
    }

    fun onApiKeyChanged(key: String) {
        _uiState.update { it.copy(apiKey = key, isSaved = false) }
    }

    fun onEndpointChanged(endpoint: String) {
        _uiState.update { it.copy(apiEndpoint = endpoint, isSaved = false) }
    }

    fun onModelChanged(model: String) {
        _uiState.update { it.copy(modelName = model, isSaved = false) }
    }

    fun saveSettings() {
        prefs.edit()
            .putString("api_key", _uiState.value.apiKey)
            .putString("api_endpoint", _uiState.value.apiEndpoint)
            .putString("model_name", _uiState.value.modelName)
            .apply()
        _uiState.update { it.copy(isSaved = true) }
    }
}
