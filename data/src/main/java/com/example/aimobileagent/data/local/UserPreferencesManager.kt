package com.example.aimobileagent.data.local

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户偏好记忆管理器（改进5：记忆系统）。
 *
 * 记录用户的 App 使用习惯、操作偏好和历史行为，
 * 提升 LLM 任务规划的个性化和准确度。
 *
 * 记忆维度：
 * 1. 常用联系人（"妈妈"、"张三" → 手机号/微信号）
 * 2. 常用 App 使用频率（用于任务规划时的 App 选择优先级）
 * 3. 用户偏好设置（默认分享方式、语言偏好等）
 * 4. 成功执行历史（哪些命令类型执行成功率高）
 */
@Singleton
class UserPreferencesManager @Inject constructor(
    private val prefs: SharedPreferences
) {
    /**
     * 记录一次成功的任务执行。
     * @param intent 任务意图类型
     * @param command 用户命令
     * @param appsUsed 使用的 App 列表
     */
    fun recordSuccess(intent: String, command: String, appsUsed: List<String>) {
        // 记录意图成功次数
        val count = prefs.getInt("success_$intent", 0) + 1
        prefs.edit().putInt("success_$intent", count).apply()

        // 记录 App 使用频率
        appsUsed.forEach { pkg ->
            val freq = prefs.getInt("app_freq_$pkg", 0) + 1
            prefs.edit().putInt("app_freq_$pkg", freq).apply()
        }

        // 保存最近的命令（最多 5 条）
        val recent = getRecentCommands().toMutableList()
        recent.add(0, command)
        prefs.edit()
            .putString("recent_commands", recent.take(5).joinToString("|||"))
            .apply()
    }

    /**
     * 记录联系人别名。
     * 例如："妈妈" → "138xxxx" 或 "wechat:wxid_xxx"
     */
    fun recordContact(alias: String, contactInfo: String) {
        prefs.edit().putString("contact_$alias", contactInfo).apply()
    }

    /**
     * 查找联系人。
     */
    fun findContact(alias: String): String? {
        return prefs.getString("contact_$alias", null)
    }

    /**
     * 获取最近命令。
     */
    fun getRecentCommands(): List<String> {
        val raw = prefs.getString("recent_commands", "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("|||")
    }

    /**
     * 获取 App 使用频率排名。
     */
    fun getTopApps(limit: Int = 5): List<String> {
        return prefs.all
            .filterKeys { it.startsWith("app_freq_") }
            .mapKeys { it.key.removePrefix("app_freq_") }
            .entries
            .sortedByDescending { it.value as Int }
            .take(limit)
            .map { it.key }
    }

    /**
     * 获取最常用的意图类型。
     */
    fun getTopIntents(limit: Int = 3): List<String> {
        return prefs.all
            .filterKeys { it.startsWith("success_") }
            .mapKeys { it.key.removePrefix("success_") }
            .entries
            .sortedByDescending { it.value as Int }
            .take(limit)
            .map { it.key }
    }

    /**
     * 生成记忆摘要，可注入到 LLM 系统提示词中。
     */
    fun generateMemoryContext(): String {
        val recent = getRecentCommands()
        val topApps = getTopApps()
        val topIntents = getTopIntents()

        return buildString {
            if (recent.isNotEmpty()) {
                appendLine("最近命令: ${recent.joinToString("; ")}")
            }
            if (topApps.isNotEmpty()) {
                appendLine("常用App: ${topApps.joinToString(", ")}")
            }
            if (topIntents.isNotEmpty()) {
                appendLine("常用操作: ${topIntents.joinToString(", ")}")
            }
        }
    }
}
