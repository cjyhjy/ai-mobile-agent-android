package com.example.aimobileagent.domain.model

import java.util.UUID

/**
 * 领域模型：一个由 LLM 生成的自动化任务。
 *
 * @property id 任务唯一标识
 * @property userCommand 用户输入的自然语言命令
 * @property llmRawResponse LLM 返回的原始 JSON（用于调试和重放）
 * @property intent LLM 识别的意图类型（如 share_media, send_message）
 * @property confidence LLM 给出的置信度 (0.0 - 1.0)
 * @property steps 按顺序排列的执行步骤
 * @property status 任务当前状态
 * @property createdAt 创建时间戳 (epoch millis)
 * @property completedAt 完成时间戳，未完成时为 null
 * @property appsInvolved 涉及的 App 包名列表
 */
data class Task(
    val id: String = UUID.randomUUID().toString(),
    val userCommand: String,
    val llmRawResponse: String = "",
    val intent: String = "",
    val confidence: Float = 0f,
    val steps: List<Step> = emptyList(),
    val status: TaskStatus = TaskStatus.PLANNING,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val appsInvolved: List<String> = emptyList()
)
