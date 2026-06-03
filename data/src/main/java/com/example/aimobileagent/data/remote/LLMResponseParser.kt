package com.example.aimobileagent.data.remote

import com.example.aimobileagent.data.remote.dto.StepDto
import com.example.aimobileagent.data.remote.dto.TaskPlanDto
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
        android.util.Log.e("LLMParser", "原始响应: ${rawContent.take(300)}")
        val extracted = extractJson(rawContent)
            ?: throw LLMException("无法从LLM响应中提取JSON，原始响应: ${rawContent.take(300)}")

        android.util.Log.e("LLMParser", "提取JSON: ${extracted.take(200)}")

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

            android.util.Log.e("LLMParser", "mode=${plan.mode} reply=${plan.reply.take(50)} steps=${plan.steps.size}")
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

        // 1. Markdown 代码块 ```
        val md = Regex("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```")
        md.find(t)?.let { return it.groupValues[1].trim() }

        // 2. 直接找第一个完整的 JSON 对象
        val start = t.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escape = false
        for (i in start until t.length) {
            val c = t[i]
            if (escape) { escape = false; continue }
            if (c == '\\' && inString) { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            when (c) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return t.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
