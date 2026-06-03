package com.example.aimobileagent.execution.executor

import com.example.aimobileagent.domain.model.Step
import com.example.aimobileagent.domain.usecase.StepExecutor
import com.example.aimobileagent.domain.usecase.StepResult
import com.example.aimobileagent.execution.AgentAccessibilityService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TapElementExecutor @Inject constructor() : StepExecutor {
    companion object { const val ACTION = "tap" }

    override suspend fun execute(step: Step): StepResult {
        val target = step.targetElement ?: return StepResult.Failure("Tap: targetElement 为空")
        val service = AgentAccessibilityService.instance ?: return StepResult.Failure("AccessibilityService 未运行")
        service.waitForStable(500L)

        val node = service.findElementByText(target)
            ?: service.findElementByDescription(target)
        if (node != null) {
            val clicked = service.performClick(node)
            return if (clicked) StepResult.Success("已点击 '$target'")
            else StepResult.Failure("点击 '$target' 失败")
        }

        if (target.contains(":id/")) {
            val idNode = service.findElementByResourceId(target)
            if (idNode != null) {
                val clicked = service.performClick(idNode)
                return if (clicked) StepResult.Success("已点击 $target") else StepResult.Failure("点击失败")
            }
        }

        return StepResult.Failure("未找到 '$target'。可见: ${service.getAllVisibleTexts().take(10)}")
    }

    override suspend fun recover(step: Step, error: String): StepResult {
        AgentAccessibilityService.instance?.waitForStable(2000L)
        return execute(step)
    }
}
