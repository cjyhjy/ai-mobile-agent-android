package com.example.aimobileagent.execution.executor

import android.view.accessibility.AccessibilityNodeInfo
import com.example.aimobileagent.domain.model.Step
import com.example.aimobileagent.domain.usecase.StepExecutor
import com.example.aimobileagent.domain.usecase.StepResult
import com.example.aimobileagent.execution.AgentAccessibilityService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TypeTextExecutor @Inject constructor() : StepExecutor {
    companion object { const val ACTION = "type" }

    override suspend fun execute(step: Step): StepResult {
        val text = step.params["text"] ?: return StepResult.Failure("Type: params.text 为空")
        val service = AgentAccessibilityService.instance ?: return StepResult.Failure("AccessibilityService 未运行")
        service.waitForStable(500L)

        val target = step.targetElement
        val inputNode = if (target != null) {
            service.findElementByText(target) ?: service.findElementByDescription(target)
        } else {
            service.getRoot()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }

        if (inputNode == null) return StepResult.Failure("找不到输入框")
        try {
            service.performClick(inputNode)
            kotlinx.coroutines.delay(200)
            return if (service.setText(inputNode, text)) StepResult.Success("已输入 '$text'")
            else StepResult.Failure("输入失败")
        } finally {
            inputNode.recycle()
        }
    }

    override suspend fun recover(step: Step, error: String): StepResult {
        AgentAccessibilityService.instance?.waitForStable(1000L)
        return execute(step)
    }
}
