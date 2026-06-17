package com.example.aimobileagent.execution.screen

import com.example.aimobileagent.domain.model.Step
import com.example.aimobileagent.domain.usecase.*
import com.example.aimobileagent.execution.AgentAccessibilityService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ScreenStateObserver 的 AccessibilityService 实现。
 * 为 OBSERVE→THINK→ACT→VERIFY Agent 循环提供屏幕感知能力。
 */
@Singleton
class ScreenStateObserverImpl @Inject constructor(
    private val safetyChecker: SafetyChecker,
    private val screenParser: ScreenParser
) : ScreenStateObserver {

    override suspend fun observe(): ScreenObservation {
        val service = AgentAccessibilityService.instance
        if (service == null) {
            return ScreenObservation("", "AccessibilityService 未运行", emptyList())
        }

        val state = screenParser.parse()
        return ScreenObservation(
            packageName = state.packageName,
            summary = screenParser.generateDescription(),
            interactiveElements = state.visibleElements.map { el ->
                when {
                    el.text.isNotBlank() -> "\"${el.text}\""
                    el.contentDescription.isNotBlank() -> "[${el.contentDescription}]"
                    el.resourceId.isNotBlank() -> "#${el.resourceId}"
                    else -> "(无标签)"
                }
            }
        )
    }

    override suspend fun checkSafety(): SafetyCheckResult {
        val result = safetyChecker.check()
        return when (result.severity) {
            SafetyChecker.Severity.SAFE -> SafetyCheckResult.Safe
            SafetyChecker.Severity.WARNING -> SafetyCheckResult.Warning(result.reason)
            SafetyChecker.Severity.DANGER -> SafetyCheckResult.Danger(result.reason)
        }
    }

    override suspend fun checkPrecondition(step: Step): PreconditionResult? {
        val service = AgentAccessibilityService.instance ?: return null

        // 对于 tap/type 操作，检查目标 App 是否在前台
        val target = step.targetApp
        if (target != null && target.isNotBlank()) {
            val fg = service.getForegroundPackage()
            if (fg.isNotBlank() && fg != target) {
                return PreconditionResult.NotReady("期望 $target 在前台，实际 $fg")
            }
        }

        return PreconditionResult.Satisfied
    }

    override suspend fun waitForCondition(step: Step, timeoutMs: Long) {
        val service = AgentAccessibilityService.instance ?: return
        val tgt = step.targetApp ?: return
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            if (service.getForegroundPackage() == tgt) {
                return // 条件满足
            }
            kotlinx.coroutines.delay(300)
        }
    }

    override suspend fun verify(step: Step): VerificationResult? {
        val service = AgentAccessibilityService.instance ?: return null

        // 对于 open_app: 验证前台是否为目标 App
        if (step.actionType == "open_app" && step.targetApp != null) {
            val fg = service.getForegroundPackage()
            if (fg == step.targetApp) {
                return VerificationResult.Passed("App 已打开")
            } else {
                return VerificationResult.Failed("App 未按预期打开，当前前台: $fg")
            }
        }

        // 对于 tap: 检查屏幕内容是否变化（粗略验证）
        if (step.actionType == "tap") {
            val visible = service.getAllVisibleTexts()
            if (visible.isNotEmpty()) {
                return VerificationResult.Passed("屏幕有内容")
            }
        }

        return VerificationResult.Passed("已执行")
    }
}
