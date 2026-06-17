package com.example.aimobileagent.execution.executor

import com.example.aimobileagent.domain.model.Step
import com.example.aimobileagent.domain.usecase.StepExecutor
import com.example.aimobileagent.domain.usecase.StepResult
import com.example.aimobileagent.execution.AgentAccessibilityService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SwipeExecutor @Inject constructor() : StepExecutor {
    companion object { const val ACTION = "swipe" }

    override suspend fun execute(step: Step): StepResult {
        val service = AgentAccessibilityService.instance ?: return StepResult.Failure("AccessibilityService 未运行")
        service.waitForStable(300L)
        val dm = service.resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val dir = step.params["direction"] ?: "up"
        val (sx, sy, ex, ey) = when (dir) {
            "up" -> listOf(w/2, h*0.7f, w/2, h*0.3f)
            "down" -> listOf(w/2, h*0.3f, w/2, h*0.7f)
            "left" -> listOf(w*0.8f, h/2, w*0.2f, h/2)
            else -> listOf(w*0.2f, h/2, w*0.8f, h/2)
        }
        return if (service.performSwipe(sx, sy, ex, ey)) StepResult.Success("已向 $dir 滑动")
        else StepResult.Failure("滑动失败")
    }

    override suspend fun recover(step: Step, error: String): StepResult =
        StepResult.Failure("滑动恢复: $error")
}
