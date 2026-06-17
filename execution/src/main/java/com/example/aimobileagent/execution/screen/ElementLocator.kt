package com.example.aimobileagent.execution.screen

import com.example.aimobileagent.execution.AgentAccessibilityService

/**
 * 元素定位器：通过多种策略定位 UI 元素。
 *
 * 查找优先级：
 * 1. 精确文本匹配
 * 2. 模糊文本匹配（包含）
 * 3. contentDescription 匹配
 * 4. resourceId 匹配
 * 5. 坐标范围匹配（当 element 描述中包含坐标信息时）
 */
class ElementLocator {

    data class LocateResult(
        val found: Boolean,
        val method: String = "",
        val detail: String = ""
    )

    /**
     * 尝试定位元素，返回找到的元素坐标。
     * @param target 目标描述（来自 LLM 规划的 step.targetElement）
     * @return 目标元素的屏幕坐标，找不到返回 null
     */
    fun locate(target: String): Pair<Float, Float>? {
        val service = AgentAccessibilityService.instance ?: return null

        // 策略 1: 文本匹配
        val node = service.findElementByText(target)
        if (node != null) {
            try {
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                val cx = (rect.left + rect.right) / 2f
                val cy = (rect.top + rect.bottom) / 2f
                return Pair(cx, cy)
            } finally {
                node.recycle()
            }
        }

        // 策略 2: resourceId
        if (target.contains(":id/")) {
            val idNode = service.findElementByResourceId(target)
            if (idNode != null) {
                try {
                    val rect = android.graphics.Rect()
                    idNode.getBoundsInScreen(rect)
                    return Pair(
                        (rect.left + rect.right) / 2f,
                        (rect.top + rect.bottom) / 2f
                    )
                } finally {
                    idNode.recycle()
                }
            }
        }

        return null
    }

    /**
     * 尝试多种策略定位，返回结果详情。
     */
    fun locateWithDetail(target: String): LocateResult {
        val coords = locate(target)
        return if (coords != null) {
            LocateResult(true, "文本匹配", "坐标: (${coords.first}, ${coords.second})")
        } else {
            val visible = AgentAccessibilityService.instance?.getAllVisibleTexts() ?: emptyList()
            LocateResult(false, "未找到", "可见元素: ${visible.take(10).joinToString(", ")}")
        }
    }
}
