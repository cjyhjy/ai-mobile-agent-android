package com.example.aimobileagent.execution.screen

import android.view.accessibility.AccessibilityNodeInfo
import com.example.aimobileagent.execution.AgentAccessibilityService

/**
 * 安全检测器。
 * 在执行每个步骤前检查当前屏幕是否包含敏感内容。
 * 灵感来源：Roubao 项目的支付/密码页面自动停止机制。
 *
 * 检测规则：
 * - 支付相关关键词（微信支付、支付宝、银行卡、金额等）
 * - 密码相关关键词（输入密码、支付密码、验证码等）
 * - 隐私页面（身份证、手机号验证等）
 */
class SafetyChecker {

    companion object {
        // 危险关键词（匹配到则立即停止执行）
        private val DANGER_KEYWORDS = listOf(
            // 支付相关
            "支付", "付款", "转账", "收款", "金额", "¥", "￥",
            "微信支付", "支付宝", "银行卡", "信用卡", "花呗", "余额",
            "确认支付", "立即付款", "pay", "payment",
            // 密码相关
            "密码", "输入密码", "支付密码", "登录密码", "验证码",
            "短信验证", "password", "PIN",
            // 隐私相关
            "身份证", "手机号", "实名", "人脸识别", "指纹",
            // 金额数字模式（¥123.45 或 ￥123.45）
        )

        // 警告关键词（提示用户但不强制停止）
        private val WARNING_KEYWORDS = listOf(
            "登录", "注册", "绑定", "授权", "同意",
            "个人信息", "隐私政策", "用户协议"
        )
    }

    data class SafetyResult(
        val isSafe: Boolean,
        val reason: String = "",
        val severity: Severity = Severity.SAFE
    )

    enum class Severity {
        SAFE,       // 安全，可以继续
        WARNING,    // 警告，建议用户确认后继续
        DANGER      // 危险，立即停止
    }

    /**
     * 检查当前屏幕是否安全。
     * 在执行敏感操作（tap, type, confirm, share_to）前调用。
     */
    fun check(): SafetyResult {
        val service = AgentAccessibilityService.instance
            ?: return SafetyResult(true, "AccessibilityService 未运行")

        val root = service.getRoot() ?: return SafetyResult(true, "无法获取屏幕内容")

        try {
            val allTexts = collectAllTexts(root)
            val fullText = allTexts.joinToString(" ")

            // 检测危险关键词
            for (keyword in DANGER_KEYWORDS) {
                if (fullText.contains(keyword, ignoreCase = true)) {
                    return SafetyResult(
                        isSafe = false,
                        reason = "检测到敏感内容: \"$keyword\"。涉及支付/密码/隐私的操作已自动停止，请手动完成。",
                        severity = Severity.DANGER
                    )
                }
            }

            // 检测警告关键词
            for (keyword in WARNING_KEYWORDS) {
                if (fullText.contains(keyword, ignoreCase = true)) {
                    return SafetyResult(
                        isSafe = true,
                        reason = "检测到: \"$keyword\"，可能需要你确认后继续。",
                        severity = Severity.WARNING
                    )
                }
            }

            return SafetyResult(true, severity = Severity.SAFE)
        } finally {
            root.recycle()
        }
    }

    /**
     * 检查当前屏幕是否包含支付界面（更严格的检测）。
     * 用于 confirm/share_to 等不可逆操作前的最后防线。
     */
    fun isPaymentScreen(): Boolean {
        val service = AgentAccessibilityService.instance ?: return false
        val root = service.getRoot() ?: return false
        try {
            val texts = collectAllTexts(root).joinToString(" ")
            val paymentKeywords = listOf("支付", "付款", "转账", "金额", "¥", "￥", "确认支付")
            return paymentKeywords.any { texts.contains(it, ignoreCase = true) }
        } finally {
            root.recycle()
        }
    }

    private fun collectAllTexts(node: AccessibilityNodeInfo): List<String> {
        val texts = mutableListOf<String>()
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        if (!text.isNullOrBlank()) texts.add(text)
        if (!desc.isNullOrBlank()) texts.add(desc)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            texts.addAll(collectAllTexts(child))
            child.recycle()
        }
        return texts
    }
}
