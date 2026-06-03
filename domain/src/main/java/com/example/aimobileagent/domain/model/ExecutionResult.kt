package com.example.aimobileagent.domain.model

/**
 * 执行结果：Sealed class 表示成功或失败。
 */
sealed class ExecutionResult {
    /**
     * 任务全部步骤成功完成。
     * @property completedSteps 成功完成的步骤数
     * @property totalDurationMs 总耗时
     */
    data class Success(
        val completedSteps: Int,
        val totalDurationMs: Long
    ) : ExecutionResult()

    /**
     * 任务执行失败。
     * @property failedStep 失败的步骤
     * @property error 错误信息
     */
    data class Failure(
        val failedStep: Step,
        val error: String
    ) : ExecutionResult()
}
