package com.example.aimobileagent.domain.repository

import com.example.aimobileagent.domain.model.Task

/**
 * LLM 服务接口。
 * Domain 层只定义契约，Data 层用 Retrofit 实现。
 */
interface LLMRepository {
    /**
     * 向 LLM 发送用户命令，返回结构化的任务计划。
     *
     * @param userCommand 用户自然语言命令
     * @param availableApps 当前设备上已注册能力的 App 列表
     * @return 解析后的 Task（包含 LLM 规划的所有 Step）
     * @throws LLMException 网络错误或 LLM 返回无法解析的响应时抛出
     */
    suspend fun planTask(userCommand: String, availableApps: List<String>): Task
}

/**
 * LLM 相关异常。
 */
class LLMException(message: String, cause: Throwable? = null) : Exception(message, cause)
