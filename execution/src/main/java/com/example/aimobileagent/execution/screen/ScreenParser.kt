package com.example.aimobileagent.execution.screen

import android.view.accessibility.AccessibilityNodeInfo
import com.example.aimobileagent.execution.AgentAccessibilityService

/**
 * 屏幕解析器：将当前 Accessibility 节点树转为结构化数据。
 *
 * 用途：
 * 1. 帮助 LLM 理解当前屏幕状态（在执行失败时提供上下文）
 * 2. 调试和日志记录
 */
data class ScreenState(
    val packageName: String = "",
    val className: String = "",
    val visibleElements: List<UIElement> = emptyList()
)

data class UIElement(
    val text: String = "",
    val contentDescription: String = "",
    val resourceId: String = "",
    val className: String = "",
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val bounds: String = "" // "left,top,right,bottom"
)

class ScreenParser {

    /**
     * 解析当前屏幕，返回结构化描述。
     */
    fun parse(): ScreenState {
        val service = AgentAccessibilityService.instance ?: return ScreenState()
        val root = service.getRoot() ?: return ScreenState()

        val state = ScreenState(
            packageName = root.packageName?.toString() ?: "",
            className = root.className?.toString() ?: ""
        )

        val elements = mutableListOf<UIElement>()
        collectElements(root, elements)
        return state.copy(visibleElements = elements)
    }

    /**
     * 生成当前屏幕的人类可读描述文本（可发送给 LLM 用于错误恢复）。
     */
    fun generateDescription(): String {
        val state = parse()
        val sb = StringBuilder()
        sb.appendLine("当前App: ${state.packageName}")
        sb.appendLine("当前页面: ${state.className}")
        sb.appendLine("可见交互元素:")
        state.visibleElements.take(20).forEach { el ->
            val label = when {
                el.text.isNotBlank() -> "\"${el.text}\""
                el.contentDescription.isNotBlank() -> "[${el.contentDescription}]"
                el.resourceId.isNotBlank() -> "#${el.resourceId}"
                else -> "(无标签)"
            }
            val type = when {
                el.isClickable -> "可点击"
                el.isEditable -> "输入框"
                else -> "其他"
            }
            sb.appendLine("  - $label ($type)")
        }
        return sb.toString()
    }

    private fun collectElements(node: AccessibilityNodeInfo, elements: MutableList<UIElement>) {
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val id = node.viewIdResourceName ?: ""

        // 只收集有意义元素（有文本/描述/ID，且可交互）
        if ((text.isNotBlank() || desc.isNotBlank() || id.isNotBlank()) &&
            (node.isClickable || node.isEditable || node.isCheckable)
        ) {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            elements.add(
                UIElement(
                    text = text,
                    contentDescription = desc,
                    resourceId = id,
                    className = node.className?.toString() ?: "",
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    bounds = "${rect.left},${rect.top},${rect.right},${rect.bottom}"
                )
            )
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectElements(it, elements) }
        }
    }
}
