package com.example.aimobileagent.data.repository

import android.content.SharedPreferences
import com.example.aimobileagent.data.remote.DeepSeekApiService
import com.example.aimobileagent.data.remote.LLMResponseParser
import com.example.aimobileagent.data.remote.PromptTemplateEngine
import com.example.aimobileagent.data.remote.dto.LLMChatRequest
import com.example.aimobileagent.data.remote.dto.Message
import com.example.aimobileagent.data.remote.dto.ResponseFormat
import android.util.Log
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
        // 读取用户设置
        val model = prefs.getString("model_name", "deepseek-chat") ?: "deepseek-chat"
        val endpoint = prefs.getString("api_endpoint", "https://api.deepseek.com") ?: "https://api.deepseek.com"
        val apiKey = prefs.getString("api_key", "") ?: ""

        if (apiKey.isBlank()) {
            throw LLMException("请先在设置中配置 API Key")
        }

        // 1. 构建系统提示词
        val capabilities = availableApps.map { pkg ->
            com.example.aimobileagent.data.remote.AppCapabilityInfo(
                appName = pkg.substringAfterLast("."),
                packageName = pkg,
                supportedActions = listOf("open_app", "tap", "type", "search")
            )
        }
        val appCapText = promptEngine.formatAppCapabilities(capabilities)
        val systemPrompt = promptEngine.buildSystemPrompt(appCapText)

        // 2. 构建请求（不用 response_format，避免某些 API 版本不兼容）
        val request = LLMChatRequest(
            model = model,
            messages = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = userCommand)
            ),
            responseFormat = null,  // DeepSeek 可能不支持，先去掉
            temperature = 0.1,
            maxTokens = 2000
        )

        Log.e("LLMRepository", "请求模型: $model, 端点: $endpoint")
        Log.e("LLMRepository", "用户命令: $userCommand")

        // 3. 发送请求
        val response = try {
            apiService.chatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: "无详情"
            Log.e("LLMRepository", "HTTP ${e.code()}: $errorBody")
            throw LLMException("API 错误 (${e.code()}): ${errorBody.take(200)}")
        } catch (e: Exception) {
            Log.e("LLMRepository", "API 请求失败", e)
            throw LLMException("LLM API 请求失败: ${e.message}", e)
        }

        // 4. 提取响应内容
        val content = response.choices.firstOrNull()?.message?.content
            ?: run {
                Log.e("LLMRepository", "响应为空, choices=${response.choices.size}")
                throw LLMException("LLM 返回空响应")
            }

        Log.e("LLMRepository", "LLM 原始响应: ${content.take(500)}")

        // 5. 解析任务计划
        val planDto = responseParser.parse(content)

        // 6. 转换为 Domain 模型
        // 聊天模式：有 reply 内容 或 mode=chat → 作为对话回复
        if (planDto.mode == "chat" || planDto.reply.isNotBlank()) {
            val reply = planDto.reply.ifBlank { content.take(200) }
            return Task(
                userCommand = userCommand,
                llmRawResponse = content,
                intent = "chat:$reply",
                confidence = 1.0f,
                steps = emptyList(),
                appsInvolved = emptyList()
            )
        }

        val steps = planDto.steps.map { dto ->
            Step(
                taskId = "",
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
