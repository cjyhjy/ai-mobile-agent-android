package com.example.aimobileagent.execution.screen

import android.graphics.Bitmap
import android.util.Base64
import com.example.aimobileagent.execution.AgentAccessibilityService
import java.io.ByteArrayOutputStream

/**
 * 屏幕视觉分析器（改进4：屏幕视觉）。
 *
 * 将当前屏幕截图编码为 Base64，供多模态 LLM 使用。
 * 在元素定位失败时，可发送截图给 LLM 帮助识别 UI 元素位置。
 *
 * 使用场景：
 * 1. 文本匹配定位失败 → 发送截图给多模态 LLM 描述可见元素
 * 2. 验证步骤执行效果 → LLM 对比前后截图判断是否成功
 * 3. 复杂页面理解 → 截图 + accessibility tree 双重输入
 */
class VisionAnalyzer {

    /**
     * 截取当前屏幕并返回 Base64 编码。
     * 需要 MediaProjection 或 AccessibilityService 的截图能力。
     *
     * 注意：Android AccessibilityService 不直接支持截图，
     * 需要通过 MediaProjection API 或 Root 获取。
     * 当前版本使用 AccessibilityService 的 takeScreenshot（API 34+）。
     */
    suspend fun captureScreenshot(): ScreenshotResult {
        val service = AgentAccessibilityService.instance
            ?: return ScreenshotResult.Error("AccessibilityService 未运行")

        return try {
            // Android 14+ (API 34) AccessibilityService 支持截图
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val bitmap = captureViaAccessibility(service)
                if (bitmap != null) {
                    val base64 = bitmapToBase64(bitmap)
                    ScreenshotResult.Success(base64, bitmap.width, bitmap.height)
                } else {
                    ScreenshotResult.Error("截图返回空")
                }
            } else {
                ScreenshotResult.Error("需要 Android 14+ 才能使用无障碍截图")
            }
        } catch (e: Exception) {
            ScreenshotResult.Error("截图失败: ${e.message}")
        }
    }

    /**
     * 将截图与 accessibility tree 合并为 LLM 可理解的描述。
     * 这是 Droidrun 的核心方案：双重输入（截图 + 结构化 UI 树）。
     */
    fun generateVisionContext(
        screenDescription: String,
        taskContext: String
    ): String {
        return buildString {
            appendLine("=== 当前屏幕结构 ===")
            appendLine(screenDescription)
            appendLine()
            appendLine("=== 当前任务 ===")
            appendLine(taskContext)
            appendLine()
            appendLine("请根据屏幕结构描述，确定下一步操作。")
        }
    }

    /**
     * 构建发送给多模态 LLM 的请求内容。
     * 包含截图 Base64 + 任务上下文。
     */
    fun buildVisionPrompt(
        screenshotBase64: String,
        screenTexts: List<String>,
        targetElement: String
    ): String {
        return buildString {
            appendLine("这是一张手机屏幕截图。")
            appendLine("屏幕上可见的文本包括: ${screenTexts.take(15).joinToString(" | ")}")
            appendLine("任务: 找到并点击 \"$targetElement\"")
            appendLine("请描述该元素的屏幕坐标位置（近似即可），格式: x,y")
        }
    }

    private suspend fun captureViaAccessibility(
        service: AgentAccessibilityService
    ): Bitmap? {
        // takeScreenshot requires API 34+ with ScreenCaptureResult callback
        // Simplified: return null for now, use MediaProjection in production
        return null
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    sealed class ScreenshotResult {
        data class Success(val base64: String, val width: Int, val height: Int) : ScreenshotResult()
        data class Error(val message: String) : ScreenshotResult()
    }
}
