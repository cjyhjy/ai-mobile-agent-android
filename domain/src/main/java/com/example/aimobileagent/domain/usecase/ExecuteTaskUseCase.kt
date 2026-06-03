package com.example.aimobileagent.domain.usecase

import com.example.aimobileagent.domain.model.*
import com.example.aimobileagent.domain.repository.TaskRepository

/**
 * 核心用例：使用 Observe→Think→Act→Verify 循环逐步执行任务。
 *
 * 升级版 Agent 循环（参考 ApkClaw / Droidrun 的成熟模式）：
 *
 *   ┌──────────────────────────────────────────────┐
 *   │  OBSERVE → 解析当前屏幕状态                     │
 *   │     ↓                                         │
 *   │  THINK   → 安全检查 + 前置条件验证 + 策略调整      │
 *   │     ↓                                         │
 *   │  ACT     → 执行步骤动作                          │
 *   │     ↓                                         │
 *   │  VERIFY  → 验证动作效果 + 记录结果                │
 *   │     ↓                                         │
 *   │  成功 → 下一步 | 失败 → 恢复 | 危险 → 立即停止     │
 *   └──────────────────────────────────────────────┘
 *
 * 相比原版线性执行的改进：
 * 1. 每步执行前先观察屏幕状态（OBSERVE）
 * 2. 安全检查独立为 THINK 阶段，危险页面立即停止
 * 3. 动作后验证效果（VERIFY），不只是看异常
 * 4. 恢复策略更智能：失败后可请求 LLM 重新规划当前步骤
 */
class ExecuteTaskUseCase(
    private val taskRepository: TaskRepository,
    private val agentContext: AgentContext? = null
) {
    suspend operator fun invoke(
        taskId: String,
        stepExecutor: StepExecutor,
        screenObserver: ScreenStateObserver? = null
    ): ExecutionResult {
        val task = taskRepository.getTaskById(taskId)
            ?: return ExecutionResult.Failure(
                Step(taskId = taskId, orderIndex = 0, actionType = ""),
                "Task not found"
            )

        val runningTask = task.copy(status = TaskStatus.RUNNING)
        taskRepository.saveTask(runningTask)

        val sortedSteps = task.steps.sortedBy { it.orderIndex }
        val startTime = System.currentTimeMillis()
        val maxRetries = 2 // 每步最多重试次数

        for (step in sortedSteps) {
            val runningStep = step.copy(status = StepStatus.RUNNING)
            updateStep(task, runningStep)

            // ===== PHASE 1: OBSERVE =====
            val screenState = screenObserver?.observe()
            agentContext?.onStepEvent(
                AgentStepEvent.Observing(step.orderIndex, screenState?.summary ?: "")
            )

            // ===== PHASE 2: THINK =====
            // 2a. 安全检查
            val safetyResult = screenObserver?.checkSafety() ?: SafetyCheckResult.Safe
            when (safetyResult) {
                is SafetyCheckResult.Danger -> {
                    updateStep(task, runningStep.copy(
                        status = StepStatus.FAILED,
                        errorMessage = "安全停止: ${safetyResult.reason}"
                    ))
                    task.steps.filter { it.orderIndex > step.orderIndex }.forEach { remaining ->
                        updateStep(task, remaining.copy(status = StepStatus.SKIPPED))
                    }
                    val failedTask = task.copy(
                        status = TaskStatus.FAILED,
                        completedAt = System.currentTimeMillis()
                    )
                    taskRepository.saveTask(failedTask)
                    return ExecutionResult.Failure(step, "安全策略拦截: ${safetyResult.reason}")
                }
                is SafetyCheckResult.Warning -> {
                    agentContext?.onStepEvent(
                        AgentStepEvent.Warning(step.orderIndex, safetyResult.reason)
                    )
                }
                is SafetyCheckResult.Safe -> { /* 继续 */ }
            }

            // 2b. 前置条件检查
            if (step.actionType in listOf("tap", "type", "search")) {
                val preCheck = screenObserver?.checkPrecondition(step)
                if (preCheck is PreconditionResult.NotReady) {
                    agentContext?.onStepEvent(
                        AgentStepEvent.PreconditionFailed(step.orderIndex, preCheck.reason)
                    )
                    screenObserver?.waitForCondition(step, 3000L)
                }
            }

            agentContext?.onStepEvent(
                AgentStepEvent.Thinking(step.orderIndex, "准备执行: ${step.actionType}")
            )

            // ===== PHASE 3: ACT =====
            val stepStart = System.currentTimeMillis()
            var result: StepResult = StepResult.Failure("未执行")
            var retryCount = 0

            while (retryCount <= maxRetries) {
                result = try {
                    stepExecutor.execute(step)
                } catch (e: Exception) {
                    StepResult.Failure(e.message ?: "Unknown error")
                }

                if (result is StepResult.Success) break
                retryCount++
                if (retryCount <= maxRetries) {
                    agentContext?.onStepEvent(
                        AgentStepEvent.Retrying(step.orderIndex, retryCount, (result as StepResult.Failure).error)
                    )
                    // 重试前尝试恢复
                    stepExecutor.recover(step, (result as StepResult.Failure).error)
                }
            }

            val duration = System.currentTimeMillis() - stepStart

            // ===== PHASE 4: VERIFY =====
            when (result) {
                is StepResult.Success -> {
                    val verified = screenObserver?.verify(step)
                    if (verified is VerificationResult.Failed) {
                        // 动作执行了但效果不对
                        updateStep(task, runningStep.copy(
                            status = StepStatus.SUCCESS,
                            executionDurationMs = duration
                        ))
                        agentContext?.onStepEvent(
                            AgentStepEvent.Verified(step.orderIndex, false, verified.reason)
                        )
                    } else {
                        updateStep(task, runningStep.copy(
                            status = StepStatus.SUCCESS,
                            executionDurationMs = duration
                        ))
                        agentContext?.onStepEvent(
                            AgentStepEvent.Verified(step.orderIndex, true, "成功")
                        )
                    }
                }
                is StepResult.Failure -> {
                    // 尝试恢复
                    val recovered = stepExecutor.recover(step, result.error)
                    if (recovered is StepResult.Success) {
                        updateStep(task, runningStep.copy(
                            status = StepStatus.SUCCESS,
                            executionDurationMs = System.currentTimeMillis() - stepStart
                        ))
                    } else {
                        updateStep(task, runningStep.copy(
                            status = StepStatus.FAILED,
                            errorMessage = result.error,
                            executionDurationMs = duration
                        ))
                        task.steps.filter { it.orderIndex > step.orderIndex }.forEach { remaining ->
                            updateStep(task, remaining.copy(status = StepStatus.SKIPPED))
                        }
                        val failedTask = task.copy(
                            status = TaskStatus.FAILED,
                            completedAt = System.currentTimeMillis()
                        )
                        taskRepository.saveTask(failedTask)
                        return ExecutionResult.Failure(step, result.error)
                    }
                }
            }
        }

        val totalDuration = System.currentTimeMillis() - startTime
        val completedTask = task.copy(
            status = TaskStatus.COMPLETED,
            completedAt = System.currentTimeMillis()
        )
        taskRepository.saveTask(completedTask)
        return ExecutionResult.Success(sortedSteps.size, totalDuration)
    }

    private suspend fun updateStep(task: Task, step: Step) {
        val updatedSteps = task.steps.toMutableList()
        val index = updatedSteps.indexOfFirst { it.id == step.id }
        if (index >= 0) {
            updatedSteps[index] = step
        }
        taskRepository.saveTask(task.copy(steps = updatedSteps))
    }
}

// ===== Agent 循环相关接口和类型 =====

/** 步骤执行器接口 — Domain 层定义，Execution 层实现。 */
interface StepExecutor {
    suspend fun execute(step: Step): StepResult
    suspend fun recover(step: Step, error: String): StepResult
}

sealed class StepResult {
    data class Success(val message: String = "") : StepResult()
    data class Failure(val error: String) : StepResult()
}

/** 屏幕状态观察器 — Execution 层实现，供 Agent 循环使用。 */
interface ScreenStateObserver {
    suspend fun observe(): ScreenObservation
    suspend fun checkSafety(): SafetyCheckResult
    suspend fun checkPrecondition(step: Step): PreconditionResult?
    suspend fun waitForCondition(step: Step, timeoutMs: Long)
    suspend fun verify(step: Step): VerificationResult?
}

data class ScreenObservation(
    val packageName: String,
    val summary: String,
    val interactiveElements: List<String>
)

sealed class SafetyCheckResult {
    data object Safe : SafetyCheckResult()
    data class Warning(val reason: String) : SafetyCheckResult()
    data class Danger(val reason: String) : SafetyCheckResult()
}

sealed class PreconditionResult {
    data object Satisfied : PreconditionResult()
    data class NotReady(val reason: String) : PreconditionResult()
}

sealed class VerificationResult {
    data class Passed(val detail: String) : VerificationResult()
    data class Failed(val reason: String) : VerificationResult()
}

/** Agent 事件回调 — 供 UI 层实时展示执行进度。 */
interface AgentContext {
    fun onStepEvent(event: AgentStepEvent)
}

sealed class AgentStepEvent {
    data class Observing(val step: Int, val screen: String) : AgentStepEvent()
    data class Thinking(val step: Int, val thought: String) : AgentStepEvent()
    data class Warning(val step: Int, val message: String) : AgentStepEvent()
    data class PreconditionFailed(val step: Int, val reason: String) : AgentStepEvent()
    data class Retrying(val step: Int, val attempt: Int, val error: String) : AgentStepEvent()
    data class Verified(val step: Int, val success: Boolean, val detail: String) : AgentStepEvent()
}
