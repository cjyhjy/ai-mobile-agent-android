package com.example.aimobileagent.execution.executor

import com.example.aimobileagent.domain.model.Step
import com.example.aimobileagent.domain.usecase.StepExecutor
import com.example.aimobileagent.domain.usecase.StepResult
import com.example.aimobileagent.execution.AgentAccessibilityService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAppExecutor @Inject constructor() : StepExecutor {
    companion object { const val ACTION = "open_app" }

    override suspend fun execute(step: Step): StepResult {
        val pkg = step.targetApp ?: return StepResult.Failure("OpenApp: targetApp 为空")
        val service = AgentAccessibilityService.instance ?: return StepResult.Failure("AccessibilityService 未运行")
        val success = service.launchApp(pkg)
        return if (success) { service.waitForStable(1500L); StepResult.Success("已打开 $pkg") }
        else StepResult.Failure("无法打开 App: $pkg")
    }

    override suspend fun recover(step: Step, error: String): StepResult =
        StepResult.Failure("App 启动失败，无法恢复: $error")
}
