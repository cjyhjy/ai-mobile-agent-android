package com.example.aimobileagent.ui.screen.chat

import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aimobileagent.data.remote.StreamEvent
import com.example.aimobileagent.data.remote.StreamingLLMClient
import com.example.aimobileagent.domain.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ChatUiState(
    val inputText: String = "",
    val currentTask: Task? = null,
    val isProcessing: Boolean = false,
    val streamingText: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val error: String? = null,
    val showModelPicker: Boolean = false,
    val showApiKeyDialog: Boolean = false,
    val selectedModel: String = "deepseek-chat",
    val availableModels: List<String> = listOf("deepseek-chat", "deepseek-v4-pro", "deepseek-v4-flash"),
    val pendingFileUri: Uri? = null
)

data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val isThinking: Boolean = false,
    val isStreaming: Boolean = false,
    val attachmentName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val streamingClient: StreamingLLMClient,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private var streamJob: Job? = null

    init {
        val savedModel = prefs.getString("model_name", "deepseek-chat") ?: "deepseek-chat"
        _uiState.update { it.copy(selectedModel = savedModel) }
    }

    fun onInputChanged(text: String) { _uiState.update { it.copy(inputText = text, error = null) } }

    fun sendCommand() {
        val command = _uiState.value.inputText.trim()
        val fileUri = _uiState.value.pendingFileUri
        if (command.isBlank() && fileUri == null) return

        val displayText = if (command.isNotBlank()) command else "[文件]"
        conversationHistory.add("user" to displayText)

        _uiState.update { state ->
            state.copy(inputText = "", isProcessing = true, error = null, streamingText = "",
                pendingFileUri = null,
                messages = state.messages +
                    ChatMessage(text = displayText, isFromUser = true, attachmentName = null) +
                    ChatMessage(text = "", isFromUser = false, isThinking = true))
        }

        streamJob = viewModelScope.launch {
            try {
                streamingClient.streamChat(displayText, conversationHistory.toList())
                    .collect { event -> handleStreamEvent(event) }
            } catch (e: CancellationException) {
                // 用户中止，保留已输出的文本
                _uiState.update { state ->
                    val clean = state.messages.filter { !it.isThinking }
                    state.copy(isProcessing = false, error = null, messages = clean)
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

    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
    }

    // === 模型选择 ===
    fun toggleModelPicker() { _uiState.update { it.copy(showModelPicker = !it.showModelPicker) } }
    fun selectModel(model: String) {
        prefs.edit().putString("model_name", model).apply()
        _uiState.update { it.copy(selectedModel = model, showModelPicker = false) }
    }

    // === API Key 弹窗 ===
    fun showApiKeyDialog() { _uiState.update { it.copy(showApiKeyDialog = true) } }
    fun dismissApiKeyDialog() { _uiState.update { it.copy(showApiKeyDialog = false) } }
    fun saveApiKey(key: String) {
        prefs.edit().putString("api_key", key).apply()
        _uiState.update { it.copy(showApiKeyDialog = false) }
    }

    // === 文件上传 ===
    fun onFileSelected(uri: Uri?, fileName: String, content: String) {
        if (uri == null || content.isBlank()) return
        _uiState.update { state ->
            val ctxMsg = "文件: $fileName\n内容:\n${content.take(2000)}"
            state.copy(inputText = ctxMsg, pendingFileUri = uri,
                messages = state.messages + ChatMessage(text = "📎 $fileName", isFromUser = true, attachmentName = fileName))
        }
    }

    private fun handleStreamEvent(event: StreamEvent) {
        when (event) {
            is StreamEvent.Thinking -> {}
            is StreamEvent.Chunk -> {
                _uiState.update { state ->
                    val msgs = state.messages.toMutableList()
                    val idx = msgs.indexOfLast { it.isThinking || it.isStreaming }
                    if (idx >= 0) msgs[idx] = ChatMessage(text = event.fullText, isFromUser = false, isStreaming = true)
                    state.copy(messages = msgs, streamingText = event.fullText)
                }
            }
            is StreamEvent.Done -> {
                val resp = event.response
                conversationHistory.add("assistant" to (resp.reply.ifBlank { resp.rawText }))
                _uiState.update { state ->
                    val clean = state.messages.filter { !it.isThinking && !it.isStreaming }
                    state.copy(isProcessing = false, streamingText = "",
                        messages = clean + ChatMessage(text = resp.reply.ifBlank { resp.rawText }, isFromUser = false))
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

    fun clearError() { _uiState.update { it.copy(error = null) } }
}
