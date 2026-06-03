package com.example.aimobileagent.ui.screen.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aimobileagent.data.remote.StreamEvent
import com.example.aimobileagent.data.remote.StreamingLLMClient
import com.example.aimobileagent.domain.model.Task
import com.example.aimobileagent.domain.model.TaskStatus
import com.example.aimobileagent.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val inputText: String = "",
    val currentTask: Task? = null,
    val isProcessing: Boolean = false,
    val streamingText: String = "",     // 流式实时文本
    val messages: List<ChatMessage> = emptyList(),
    val error: String? = null
)

data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val isThinking: Boolean = false,
    val isStreaming: Boolean = false,   // 正在流式输出中
    val timestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val streamingClient: StreamingLLMClient,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // 对话历史 (role, content)
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text, error = null) }
    }

    fun sendCommand() {
        val command = _uiState.value.inputText.trim()
        if (command.isBlank()) return

        android.util.Log.e("ChatVM", "发送: $command")

        // 保存用户消息到历史
        conversationHistory.add("user" to command)

        _uiState.update { state ->
            state.copy(
                inputText = "",
                isProcessing = true,
                error = null,
                streamingText = "",
                messages = state.messages +
                    ChatMessage(text = command, isFromUser = true) +
                    ChatMessage(text = "", isFromUser = false, isThinking = true)
            )
        }

        viewModelScope.launch {
            try {
                streamingClient.streamChat(command, conversationHistory.toList())
                    .collect { event ->
                        handleStreamEvent(event)
                    }
            } catch (e: Exception) {
                android.util.Log.e("ChatVM", "流式失败: ${e.message}", e)
                _uiState.update { state ->
                    val clean = state.messages.filter { !it.isThinking && !it.isStreaming }
                    state.copy(isProcessing = false, error = e.message, messages = clean)
                }
            }
        }
    }

    private fun handleStreamEvent(event: StreamEvent) {
        when (event) {
            is StreamEvent.Thinking -> {
                // thinking 状态已在 sendCommand 中设置
            }
            is StreamEvent.Chunk -> {
                // 实时更新流式文本
                _uiState.update { state ->
                    val msgs = state.messages.toMutableList()
                    // 找到 thinking 消息，替换为 streaming 消息
                    val idx = msgs.indexOfLast { it.isThinking || it.isStreaming }
                    if (idx >= 0) {
                        msgs[idx] = ChatMessage(
                            text = event.fullText,
                            isFromUser = false,
                            isStreaming = true
                        )
                    }
                    state.copy(messages = msgs, streamingText = event.fullText)
                }
            }
            is StreamEvent.Done -> {
                val resp = event.response
                conversationHistory.add("assistant" to (resp.reply.ifBlank { resp.rawText }))

                _uiState.update { state ->
                    val clean = state.messages.filter { !it.isThinking && !it.isStreaming }
                    val replyText = if (resp.mode == "task") {
                        "📋 任务: ${resp.intent}, ${resp.reply}"
                    } else {
                        resp.reply.ifBlank { resp.rawText }
                    }
                    state.copy(
                        isProcessing = false,
                        streamingText = "",
                        messages = clean + ChatMessage(text = replyText, isFromUser = false)
                    )
                }
            }
            is StreamEvent.Error -> {
                _uiState.update { state ->
                    val clean = state.messages.filter { !it.isThinking && !it.isStreaming }
                    state.copy(isProcessing = false, error = event.message, messages = clean)
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
