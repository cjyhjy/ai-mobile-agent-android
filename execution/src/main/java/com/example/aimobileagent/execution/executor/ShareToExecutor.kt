package com.example.aimobileagent.execution.executor

import com.example.aimobileagent.domain.model.Step
import com.example.aimobileagent.domain.usecase.StepExecutor
import com.example.aimobileagent.domain.usecase.StepResult
import com.example.aimobileagent.execution.AgentAccessibilityService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareToExecutor @Inject constructor() : StepExecutor {
    companion object { const val ACTION = "share_to" }

    override suspend fun execute(step: Step): StepResult {
        val targetApp = step.targetApp ?: return StepResult.Failure("ShareTo: targetApp 为空")
        val service = AgentAccessibilityService.instance ?: return StepResult.Failure("AccessibilityService 未运行")
        service.waitForStable(500L)

        val label = when (targetApp) {
            "com.tencent.mm" -> "微信"
            "com.android.mms", "com.google.android.apps.messaging" -> "短信"
            else -> targetApp.substringAfterLast(".")
        }
        val shareTarget = service.findElementByText(label)
        return if (shareTarget != null) {
            try {
                if (service.performClick(shareTarget)) StepResult.Success("已选择: $targetApp")
                else StepResult.Failure("点击分享目标失败")
            } finally {
                shareTarget.recycle()
            }
        } else StepResult.Failure("找不到 '$label'")
    }

    override suspend fun recover(step: Step, error: String): StepResult =
        StepResult.Failure("分享恢复: $error")
}
