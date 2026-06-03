package com.example.aimobileagent.domain.usecase

import com.example.aimobileagent.domain.model.Task
import com.example.aimobileagent.domain.repository.LLMRepository
import com.example.aimobileagent.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow

/**
 * 核心用例：接收用户自然语言命令，调用 LLM 生成任务计划并持久化。
 *
 * @param llmRepository LLM 服务
 * @param taskRepository 本地任务存储
 */
class ProcessCommandUseCase(
    private val llmRepository: LLMRepository,
    private val taskRepository: TaskRepository
) {
    /**
     * @param command 用户输入的自然语言命令
     * @param availableApps 当前已注册的 App 包名列表
     * @return 包含 LLM 生成计划的 Task（状态为 READY）
     */
    suspend operator fun invoke(command: String, availableApps: List<String>): Task {
        // 1. 创建 PLANNING 状态的任务
        val planningTask = Task(
            userCommand = command,
            status = com.example.aimobileagent.domain.model.TaskStatus.PLANNING
        )
        taskRepository.saveTask(planningTask)

        // 2. 调用 LLM 生成计划
        val plannedTask = llmRepository.planTask(command, availableApps)

        // 3. 更新为 READY，等待用户确认（修正步骤的 taskId）
        val fixedSteps = plannedTask.steps.map { it.copy(taskId = planningTask.id) }
        val readyTask = plannedTask.copy(
            id = planningTask.id,
            steps = fixedSteps,
            status = com.example.aimobileagent.domain.model.TaskStatus.READY,
            createdAt = planningTask.createdAt
        )
        taskRepository.saveTask(readyTask)

        return readyTask
    }

    /** 观察任务流（供 UI 层使用） */
    fun observeTask(taskId: String): Flow<Task?> = taskRepository.observeTask(taskId)
}
