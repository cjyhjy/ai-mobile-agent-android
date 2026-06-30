package com.example.aimobileagent.data.remote

import com.example.aimobileagent.data.remote.dto.StepDto
import com.example.aimobileagent.data.remote.dto.TaskPlanDto
import com.example.aimobileagent.data.BuildConfig
import com.example.aimobileagent.domain.repository.LLMException
import kotlinx.serialization.json.Json

/**
 * LLM 响应解析器。
 * 负责：
 * 1. 从 LLM 响应中提取 JSON（处理 markdown 代码块包裹等格式问题）
 * 2. 反序列化为 TaskPlanDto
 * 3. 验证必填字段
 * 4. 处理 inability 类型的响应
 */
class LLMResponseParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * 从原始 LLM 文本响应中提取并解析任务计划。
     *
     * @param rawContent LLM 返回的 message.content
     * @return 解析后的 TaskPlanDto
     * @throws LLMException 无法解析时抛出
     */
    fun parse(rawContent: String): TaskPlanDto {
        if (BuildConfig.DEBUG) {
            android.util.Log.d("LLMParser", "原始响应: ${rawContent.take(300)}")
        }
        val extracted = extractJson(rawContent)
        if (extracted == null) {
            // 容错：尝试正则提取 reply 字段
            val replyMatch = Regex("\"reply\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(rawContent)
            if (replyMatch != null) {
                val reply = replyMatch.groupValues[1]
                    .replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"")
                if (BuildConfig.DEBUG) {
                    android.util.Log.d("LLMParser", "正则提取reply: ${reply.take(100)}")
                }
                return TaskPlanDto(mode = "chat", reply = reply, steps = emptyList())
            }
            throw LLMException("无法从LLM响应中提取JSON，原始响应: ${rawContent.take(300)}")
        }

        if (BuildConfig.DEBUG) {
            android.util.Log.d("LLMParser", "提取JSON: ${extracted.take(200)}")
        }

        return try {
            val plan = json.decodeFromString<TaskPlanDto>(extracted)

            if (plan.intent == "inability") {
                throw LLMException("LLM判断此任务无法执行")
            }

            if (plan.steps.isEmpty() && plan.intent != "inability" && plan.mode != "chat" && plan.reply.isBlank()) {
                throw LLMException("请描述你想要完成的具体任务，例如'打开飞行模式'、'发微信给张三说我到了'")
            }

            val orders = plan.steps.map { it.order }.sorted()
            if (orders.isNotEmpty() && orders.first() != 1) {
                val fixedSteps = plan.steps.mapIndexed { index, step ->
                    step.copy(order = index + 1)
                }
                return plan.copy(steps = fixedSteps)
            }

            if (BuildConfig.DEBUG) {
                android.util.Log.d("LLMParser", "mode=${plan.mode} reply=${plan.reply.take(50)} steps=${plan.steps.size}")
            }
            plan
        } catch (e: LLMException) {
            throw e
        } catch (e: Exception) {
            throw LLMException("JSON解析失败: ${e.message}。提取到的JSON: ${extracted.take(200)}", e)
        }
    }

    /**
     * 从文本中提取 JSON 字符串。
     */
    private fun extractJson(text: String): String? {
        val t = text.trim()

        // 1. Markdown ```json 代码块
        val md = Regex("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```")
        md.find(t)?.let { return it.groupValues[1].trim() }

        // 2. 用 "mode" 定位 JSON —— 从 mode 位置逐字符回溯找真正的 {
        val modeMarker = Regex("\"mode\"\\s*:\\s*\"(chat|task)\"")
        val modeMatch = modeMarker.find(t)
        if (modeMatch != null) {
            val modePos = modeMatch.range.first
            // 逐字符回溯，跳过字符串内容，找 JSON 的 {
            var depth = 0
            var inStr = false
            var esc = false
            for (i in modePos - 1 downTo 0) {
                val c = t[i]
                if (esc) { esc = false; continue }
                if (c == '\\' && inStr) { esc = true; continue }
                if (c == '"') { inStr = !inStr; continue }
                if (inStr) continue
                when (c) {
                    '}' -> depth++
                    '{' -> if (depth == 0) return extractBalancedJson(t, i)
                           else depth--
                }
            }
        }

        // 3. 搜索 {"mode" 或 {"intent" 字面
        for (marker in listOf("{\"mode\"", "{\"intent\"")) {
            val idx = t.indexOf(marker)
            if (idx >= 0) return extractBalancedJson(t, idx)
        }

        // 4. 兜底
        val start = t.indexOf('{')
        if (start >= 0) return extractBalancedJson(t, start)
        return null
    }

    private fun extractBalancedJson(text: String, startIdx: Int): String? {
        var depth = 0
        var inString = false
        var escape = false
        for (i in startIdx until text.length) {
            val c = text[i]
            if (escape) { escape = false; continue }
            if (c == '\\' && inString) { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            when (c) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(startIdx, i + 1)
                }
            }
        }
        return null
    }
}
