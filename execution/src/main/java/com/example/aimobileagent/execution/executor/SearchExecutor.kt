package com.example.aimobileagent.execution.executor

import android.view.accessibility.AccessibilityNodeInfo
import com.example.aimobileagent.domain.model.Step
import com.example.aimobileagent.domain.usecase.StepExecutor
import com.example.aimobileagent.domain.usecase.StepResult
import com.example.aimobileagent.execution.AgentAccessibilityService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchExecutor @Inject constructor() : StepExecutor {
    companion object { const val ACTION = "search" }

    override suspend fun execute(step: Step): StepResult {
        val query = step.params["query"] ?: return StepResult.Failure("Search: params.query 为空")
        val service = AgentAccessibilityService.instance ?: return StepResult.Failure("AccessibilityService 未运行")
        service.waitForStable(500L)

        val searchNode = service.findElementByText("搜索")
            ?: service.findElementByDescription("搜索")
            ?: service.findElementByDescription("search")

        if (searchNode == null) {
            val root = service.getRoot()
            val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null) { service.setText(focused, query); kotlinx.coroutines.delay(300); return StepResult.Success("已搜索 '$query'") }
            return StepResult.Failure("找不到搜索框")
        }
        service.performClick(searchNode)
        kotlinx.coroutines.delay(300)
        service.setText(searchNode, query)
        return StepResult.Success("已搜索 '$query'")
    }

    override suspend fun recover(step: Step, error: String): StepResult {
        AgentAccessibilityService.instance?.waitForStable(1000L)
        return execute(step)
    }
}
