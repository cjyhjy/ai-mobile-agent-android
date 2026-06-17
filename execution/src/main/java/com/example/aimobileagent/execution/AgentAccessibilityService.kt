package com.example.aimobileagent.execution

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 核心：Agent Accessibility Service。
 *
 * 通过 AccessibilityService API 实现以下能力：
 * - 遍历当前屏幕的 UI 树
 * - 查找指定元素（按文本/描述/resourceId）
 * - 执行点击、滑动、输入等手势
 * - 查询当前前台 App 包名
 *
 * 这是整个执行引擎的基础，所有 Executor 都依赖此 Service。
 *
 * 使用指南：
 * 1. 在 AndroidManifest.xml 中声明此 Service
 * 2. 在 res/xml/accessibility_service_config.xml 中配置
 * 3. 用户需在"设置 → 无障碍"中手动开启
 */
class AgentAccessibilityService : AccessibilityService() {

    companion object {
        /** 全局单例引用，供 Executor 使用 */
        @Volatile
        var instance: AgentAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 事件处理：可用于监听窗口变化、通知 Executor 页面已切换
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // 页面切换，可通知等待中的 executor
        }
    }

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // ===== 公开 API =====

    /**
     * 获取当前根节点（UI 树的入口）。
     */
    fun getRoot(): AccessibilityNodeInfo? = rootInActiveWindow

    /**
     * 查找包含指定文本的 UI 元素。
     * @param text 要匹配的文本（模糊匹配）
     * @param exact 是否精确匹配，默认 false
     */
    fun findElementByText(text: String, exact: Boolean = false): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findNodeByText(root, text, exact)
    }

    /**
     * 按 resourceId 查找 UI 元素。
     */
    fun findElementByResourceId(resourceId: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findAccessibilityNodeInfosByViewId(resourceId).firstOrNull()
    }

    /**
     * 按 contentDescription 查找 UI 元素。
     */
    fun findElementByDescription(description: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findNodeByDescription(root, description)
    }

    /**
     * 执行点击手势。
     */
    fun performClick(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * 在指定坐标执行点击。
     */
    suspend fun performClickAt(x: Float, y: Float): Boolean {
        return performGestureAt(x, y, 0) // 0 = tap gesture via Path
    }

    /**
     * 执行滑动手势。
     */
    suspend fun performSwipe(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        duration: Long = 300L
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * 在输入框中设置文本。
     */
    fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = android.os.Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /**
     * 获取当前前台 App 包名。
     */
    fun getForegroundPackage(): String {
        val root = rootInActiveWindow ?: return ""
        return root.packageName?.toString() ?: ""
    }

    /**
     * 启动 App（通过 Intent）。
     */
    fun launchApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 等待页面稳定（通过监听窗口变化事件）。
     * 简单实现：等待 500ms 让 UI 渲染完成。
     */
    suspend fun waitForStable(delayMs: Long = 500L) {
        kotlinx.coroutines.delay(delayMs)
    }

    /**
     * 获取当前屏幕所有可见文本。
     */
    fun getAllVisibleTexts(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val texts = mutableListOf<String>()
        collectAllTexts(root, texts)
        return texts
    }

    // ===== 私有方法 =====

    private fun findNodeByText(
        node: AccessibilityNodeInfo,
        text: String,
        exact: Boolean
    ): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString() ?: ""
        val nodeDesc = node.contentDescription?.toString() ?: ""

        val matches = if (exact) {
            nodeText == text || nodeDesc == text
        } else {
            nodeText.contains(text, ignoreCase = true) || nodeDesc.contains(text, ignoreCase = true)
        }

        if (matches && node.isClickable) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByText(child, text, exact)
            if (result != null) {
                if (result !== child) child.recycle()
                return result
            } else {
                child.recycle()
            }
        }
        return null
    }

    private fun findNodeByDescription(
        node: AccessibilityNodeInfo,
        description: String
    ): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString() ?: ""
        if (desc.contains(description, ignoreCase = true) && node.isClickable) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByDescription(child, description)
            if (result != null) {
                if (result !== child) child.recycle()
                return result
            } else {
                child.recycle()
            }
        }
        return null
    }

    private fun collectAllTexts(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        node.text?.toString()?.let { if (it.isNotBlank()) texts.add(it) }
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) texts.add("[$it]") }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAllTexts(child, texts)
            child.recycle()
        }
    }

    private suspend fun performGestureAt(x: Float, y: Float, action: Int): Boolean {
        return suspendCancellableCoroutine { cont ->
            val path = Path().apply { moveTo(x, y) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    cont.resume(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    cont.resume(false)
                }
            }
            dispatchGesture(gesture, callback, null)
        }
    }
}
