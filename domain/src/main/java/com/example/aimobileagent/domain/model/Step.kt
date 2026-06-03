package com.example.aimobileagent.domain.model

import java.util.UUID

/**
 * 领域模型：任务中的一个执行步骤。
 *
 * @property id 步骤唯一标识
 * @property taskId 所属任务 ID
 * @property orderIndex 步骤序号（从 1 开始）
 * @property actionType 操作类型：open_app, tap, type, swipe, search, share, confirm, wait, go_back, go_home
 * @property targetApp 目标 App 包名（如 com.tencent.mm）
 * @property targetElement 目标 UI 元素描述（如 "搜索框"、"发送按钮"）
 * @property params 操作参数，JSON 格式（如 {"text": "你好", "query": "猫"})
 * @property status 步骤执行状态
 * @property errorMessage 失败时的错误信息
 * @property executionDurationMs 执行耗时（毫秒）
 */
data class Step(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val orderIndex: Int,
    val actionType: String,
    val targetApp: String? = null,
    val targetElement: String? = null,
    val params: Map<String, String> = emptyMap(),
    val status: StepStatus = StepStatus.PENDING,
    val errorMessage: String? = null,
    val executionDurationMs: Long? = null
)
