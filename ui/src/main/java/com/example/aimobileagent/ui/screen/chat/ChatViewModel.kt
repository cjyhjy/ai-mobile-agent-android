package com.example.aimobileagent.ui.screen.chat

import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aimobileagent.data.remote.StreamEvent
import com.example.aimobileagent.data.remote.StreamingLLMClient
import com.example.aimobileagent.domain.model.Task
import com.example.aimobileagent.domain.model.Step
import com.example.aimobileagent.domain.model.TaskStatus
import com.example.aimobileagent.domain.repository.AppCapabilityRepository
import com.example.aimobileagent.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ModelInfo(val name: String, val endpoint: String, val provider: String)

val AVAILABLE_MODELS = listOf(
    ModelInfo("deepseek-chat", "https://api.deepseek.com", "DeepSeek"),
    ModelInfo("deepseek-reasoner", "https://api.deepseek.com", "DeepSeek"),
    ModelInfo("gpt-4o-mini", "https://api.openai.com", "OpenAI"),
    ModelInfo("gpt-4o", "https://api.openai.com", "OpenAI"),
    ModelInfo("claude-3.5-haiku", "https://api.anthropic.com", "Anthropic"),
    ModelInfo("claude-sonnet-4-6", "https://api.anthropic.com", "Anthropic"),
    ModelInfo("gemini-2.0-flash", "https://generativelanguage.googleapis.com", "Google"),
)

data class ChatUiState(
    val inputText: String = "",
    val currentTask: Task? = null,
    val isProcessing: Boolean = false,
    val streamingText: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val error: String? = null,
    val showModelPicker: Boolean = false,
    val showApiKeyDialog: Boolean = false,
    val apiKeyModelName: String = "",
    val selectedModel: String = "deepseek-chat",
    val availableModels: List<ModelInfo> = AVAILABLE_MODELS,
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
    private val prefs: SharedPreferences,
    private val appCapRepo: AppCapabilityRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {

    // 缓存的已注册 App 包名
    private var registeredApps: List<String> = emptyList()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private var streamJob: Job? = null

    init {
        val savedModel = prefs.getString("model_name", "deepseek-chat") ?: "deepseek-chat"
        _uiState.update { it.copy(selectedModel = savedModel) }
        // 加载已注册 App（含名称，帮助 LLM 理解"火影忍者"→"com.tencent.KiHan"）
        viewModelScope.launch {
            try { registeredApps = appCapRepo.getAll().map { "${it.appName}(${it.packageName})" } }
            catch (_: Exception) { registeredApps = emptyList() }
        }
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
                streamingClient.streamChat(displayText, conversationHistory.toList(), registeredApps)
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

    fun selectModel(model: ModelInfo) {
        // 检查该模型是否已配 API Key
        val existingKey = prefs.getString("api_key_${model.name}", null)
        if (existingKey.isNullOrBlank() && model.name != "deepseek-chat") {
            // 需要配置 API Key
            _uiState.update { it.copy(showModelPicker = false, apiKeyModelName = model.name, showApiKeyDialog = true) }
        } else {
            applyModelSelection(model.name)
        }
    }

    private fun applyModelSelection(name: String) {
        prefs.edit().putString("model_name", name).apply()
        // 更新对应模型的 API Key 为当前 key
        val modelKey = prefs.getString("api_key_$name", null)
            ?: prefs.getString("api_key", "") ?: ""
        prefs.edit().putString("api_key", modelKey).apply()
        _uiState.update { it.copy(selectedModel = name, showModelPicker = false) }
    }

    // === API Key 弹窗 ===
    fun showApiKeyDialogFor(model: String) { _uiState.update { it.copy(showApiKeyDialog = true, apiKeyModelName = model) } }
    fun dismissApiKeyDialog() { _uiState.update { it.copy(showApiKeyDialog = false) } }
    fun saveApiKey(key: String) {
        val model = _uiState.value.apiKeyModelName.ifBlank { _uiState.value.selectedModel }
        if (key.isNotBlank()) {
            prefs.edit().putString("api_key_$model", key).apply()
            prefs.edit().putString("api_key", key).apply()
            prefs.edit().putString("model_name", model).apply()
            _uiState.update { it.copy(showApiKeyDialog = false, selectedModel = model) }
        }
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

    private suspend fun handleStreamEvent(event: StreamEvent) {
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
                if (resp.mode == "task" && resp.stepsJson.isNotBlank()) {
                    // === 任务模式：创建 Task，展示计划卡片 ===
                    try {
                        val stepsArr = org.json.JSONArray(resp.stepsJson)
                        val domainSteps = (0 until stepsArr.length()).map { i ->
                            val s = stepsArr.getJSONObject(i)
                            val action = s.optString("action", "")
                            val target = s.optString("target", null)
                            val order = s.optInt("order", i + 1)
                            // 解析 params：可能是数字(0)、空对象({})、或真实 map
                            val paramsJson = s.opt("params")
                            val params = when (paramsJson) {
                                is org.json.JSONObject -> {
                                    val map = mutableMapOf<String, String>()
                                    paramsJson.keys().forEach { key -> map[key] = paramsJson.optString(key, "") }
                                    map
                                }
                                else -> emptyMap()
                            }
                            val targetApp = if (action == "open_app") target else null
                            val targetElement = if (action != "open_app") target else null
                            Step(taskId = "", orderIndex = order, actionType = action,
                                targetApp = targetApp, targetElement = targetElement, params = params)
                        }
                        val task = Task(
                            userCommand = conversationHistory.lastOrNull { it.first == "user" }?.second ?: resp.intent,
                            llmRawResponse = resp.rawText,
                            intent = resp.intent,
                            confidence = 1.0f,
                            steps = domainSteps,
                            status = TaskStatus.READY,
                            appsInvolved = domainSteps.mapNotNull { it.targetApp }.distinct()
                        )
                        taskRepository.saveTask(task)
                        // 修正 taskId 后重新保存
                        val fixedSteps = domainSteps.map { it.copy(taskId = task.id) }
                        val finalTask = task.copy(steps = fixedSteps)
                        taskRepository.saveTask(finalTask)
                        // 显示计划卡片 + 摘要消息
                        conversationHistory.add("assistant" to "📋 任务计划: ${resp.intent}")
                        _uiState.update { state ->
                            val clean = state.messages.filter { !it.isThinking && !it.isStreaming }
                            state.copy(isProcessing = false, streamingText = "",
                                currentTask = finalTask,
                                messages = clean + ChatMessage(
                                    text = "📋 已生成任务计划：${resp.intent}\n共 ${domainSteps.size} 步，请在下方确认执行",
                                    isFromUser = false))
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ChatVM", "解析task步骤失败: ${e.message}", e)
                        conversationHistory.add("assistant" to (resp.reply.ifBlank { resp.rawText }))
                        _uiState.update { state ->
                            val clean = state.messages.filter { !it.isThinking && !it.isStreaming }
                            state.copy(isProcessing = false, streamingText = "",
                                messages = clean + ChatMessage(text = resp.reply.ifBlank { resp.rawText }, isFromUser = false))
                        }
                    }
                } else {
                    // === 聊天模式 ===
                    conversationHistory.add("assistant" to (resp.reply.ifBlank { resp.rawText }))
                    _uiState.update { state ->
                        val clean = state.messages.filter { !it.isThinking && !it.isStreaming }
                        state.copy(isProcessing = false, streamingText = "",
                            messages = clean + ChatMessage(text = resp.reply.ifBlank { resp.rawText }, isFromUser = false))
                    }
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
