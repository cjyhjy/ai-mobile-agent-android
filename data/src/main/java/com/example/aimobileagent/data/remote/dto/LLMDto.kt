package com.example.aimobileagent.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ===== Request DTOs =====

@Serializable
data class LLMChatRequest(
    val model: String,
    val messages: List<Message>,
    @SerialName("response_format")
    val responseFormat: ResponseFormat? = null,
    val temperature: Double = 0.1,
    @SerialName("max_tokens")
    val maxTokens: Int = 2000
)

@Serializable
data class Message(
    val role: String, // "system" | "user" | "assistant"
    val content: String
)

@Serializable
data class ResponseFormat(
    val type: String = "json_object"
)

// ===== Response DTOs =====

@Serializable
data class LLMChatResponse(
    val id: String = "",
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: MessageContent? = null
)

@Serializable
data class MessageContent(
    val role: String = "",
    val content: String = ""
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

// ===== Task Plan DTO (LLM 返回的结构化 JSON) =====

@Serializable
data class TaskPlanDto(
    val intent: String = "",
    val confidence: Double = 0.0,
    @SerialName("needs_confirmation")
    val needsConfirmation: Boolean = true,
    @SerialName("estimated_duration_seconds")
    val estimatedDurationSeconds: Int = 0,
    val steps: List<StepDto> = emptyList()
)

@Serializable
data class StepDto(
    val order: Int = 0,
    val action: String = "",
    val target: String? = null,
    val params: Map<String, String> = emptyMap()
)
