package com.example.aimobileagent.data.repository

import android.content.SharedPreferences
import com.example.aimobileagent.data.remote.DeepSeekApiService
import com.example.aimobileagent.data.remote.LLMResponseParser
import com.example.aimobileagent.data.remote.PromptTemplateEngine
import com.example.aimobileagent.data.remote.dto.LLMChatRequest
import com.example.aimobileagent.data.remote.dto.Message
import com.example.aimobileagent.data.remote.dto.ResponseFormat
import com.example.aimobileagent.domain.repository.LLMException
import com.example.aimobileagent.domain.model.Step
import com.example.aimobileagent.domain.model.Task
import com.example.aimobileagent.domain.repository.LLMRepository
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LLMRepository 的 Retrofit 实现。
 * 直连 DeepSeek API（兼容 OpenAI 格式）。
 */
@Singleton
class LLMRepositoryImpl @Inject constructor(
    private val apiService: DeepSeekApiService,
    private val promptEngine: PromptTemplateEngine,
    private val responseParser: LLMResponseParser,
    private val prefs: SharedPreferences
) : LLMRepository {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val DEFAULT_MODEL = "deepseek-chat"
    }

    override suspend fun planTask(userCommand: String, availableApps: List<String>): Task {
        // 1. 构建系统提示词
        val capabilities = availableApps.map { pkg ->
            // 简化处理 — 真实项目中此处会从 AppCapabilityDao 查询详细信息
            com.example.aimobileagent.data.remote.AppCapabilityInfo(
                appName = pkg.substringAfterLast("."),
                packageName = pkg,
                supportedActions = listOf("open_app", "tap", "type", "search")
            )
        }
        val appCapText = promptEngine.formatAppCapabilities(capabilities)
        val systemPrompt = promptEngine.buildSystemPrompt(appCapText)

        // 2. 构建请求
        val request = LLMChatRequest(
            model = DEFAULT_MODEL,
            messages = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = userCommand)
            ),
            responseFormat = ResponseFormat("json_object"),
            temperature = 0.1,
            maxTokens = 2000
        )

        // 3. 发送请求
        val response = try {
            apiService.chatCompletion(
                authorization = "Bearer ${prefs.getString("api_key", "") ?: ""}",
                request = request
            )
        } catch (e: Exception) {
            throw LLMException("LLM API 请求失败: ${e.message}", e)
        }

        // 4. 提取响应内容
        val content = response.choices.firstOrNull()?.message?.content
            ?: throw LLMException("LLM 返回空响应")

        // 5. 解析任务计划
        val planDto = responseParser.parse(content)

        // 6. 转换为 Domain 模型
        val steps = planDto.steps.map { dto ->
            Step(
                taskId = "", // 稍后由 UseCase 填充
                orderIndex = dto.order,
                actionType = dto.action,
                targetApp = dto.target,
                targetElement = dto.target,
                params = dto.params
            )
        }

        return Task(
            userCommand = userCommand,
            llmRawResponse = content,
            intent = planDto.intent,
            confidence = planDto.confidence.toFloat(),
            steps = steps,
            appsInvolved = steps.mapNotNull { it.targetApp }.distinct()
        )
    }
}
