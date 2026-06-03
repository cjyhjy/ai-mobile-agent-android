package com.example.aimobileagent.ui.screen.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aimobileagent.domain.model.Task
import com.example.aimobileagent.domain.model.TaskStatus
import com.example.aimobileagent.domain.repository.TaskRepository
import com.example.aimobileagent.domain.usecase.ProcessCommandUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val inputText: String = "",
    val currentTask: Task? = null,
    val isProcessing: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val error: String? = null
)

data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val processCommandUseCase: ProcessCommandUseCase,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text, error = null) }
    }

    fun sendCommand() {
        val command = _uiState.value.inputText.trim()
        if (command.isBlank()) return

        _uiState.update { state ->
            state.copy(
                inputText = "",
                isProcessing = true,
                error = null,
                messages = state.messages + ChatMessage(text = command, isFromUser = true)
            )
        }

        viewModelScope.launch {
            try {
                // 获取可用 App 列表（简化：从 TaskRepository 获取）
                val availableApps = listOf(
                    "com.android.gallery3d",
                    "com.tencent.mm",
                    "com.google.android.apps.messaging",
                    "com.android.settings",
                    "com.android.browser",
                    "com.google.android.apps.maps"
                )

                val task = processCommandUseCase(command, availableApps)

                _uiState.update { state ->
                    state.copy(
                        isProcessing = false,
                        currentTask = task,
                        messages = state.messages + ChatMessage(
                            text = "📋 计划生成完毕，共 ${task.steps.size} 步，需要你确认后执行。",
                            isFromUser = false
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isProcessing = false,
                        error = e.message ?: "未知错误",
                        messages = state.messages + ChatMessage(
                            text = "❌ 出错了：${e.message}",
                            isFromUser = false
                        )
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
