package com.example.aimobileagent.execution

import com.example.aimobileagent.domain.model.Step
import com.example.aimobileagent.domain.usecase.StepExecutor
import com.example.aimobileagent.domain.usecase.StepResult
import com.example.aimobileagent.execution.executor.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepExecutorFactory @Inject constructor(
    private val openApp: OpenAppExecutor,
    private val tap: TapElementExecutor,
    private val type: TypeTextExecutor,
    private val swipe: SwipeExecutor,
    private val search: SearchExecutor,
    private val shareTo: ShareToExecutor
) : StepExecutor {

    private fun getExecutor(action: String): StepExecutor? = when (action) {
        OpenAppExecutor.ACTION -> openApp
        TapElementExecutor.ACTION -> tap
        TypeTextExecutor.ACTION -> type
        SwipeExecutor.ACTION -> swipe
        SearchExecutor.ACTION -> search
        ShareToExecutor.ACTION -> shareTo
        else -> null
    }

    override suspend fun execute(step: Step): StepResult {
        return getExecutor(step.actionType)?.execute(step)
            ?: StepResult.Failure("未知 action_type: ${step.actionType}")
    }

    override suspend fun recover(step: Step, error: String): StepResult {
        return when (step.actionType) {
            TapElementExecutor.ACTION, TypeTextExecutor.ACTION, SearchExecutor.ACTION -> {
                AgentAccessibilityService.instance?.waitForStable(2000L)
                getExecutor(step.actionType)?.execute(step)
                    ?: StepResult.Failure("恢复失败")
            }
            else -> StepResult.Failure("无法自动恢复: $error")
        }
    }
}
