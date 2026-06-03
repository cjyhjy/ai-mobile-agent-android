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
        val extracted = extractJson(rawContent)
            ?: throw LLMException("无法从LLM响应中提取JSON: ${rawContent.take(200)}...")

        return try {
            val plan = json.decodeFromString<TaskPlanDto>(extracted)

            // 检查 inability
            if (plan.intent == "inability") {
                throw LLMException("LLM判断此任务无法执行: $extracted")
            }

            // 验证基本字段
            if (plan.steps.isEmpty() && plan.intent != "inability") {
                throw LLMException("LLM返回的计划中没有步骤")
            }

            // 验证步骤顺序
            val orders = plan.steps.map { it.order }.sorted()
            if (orders.isNotEmpty() && orders.first() != 1) {
                // 修复顺序（从1开始重新编号）
                val fixedSteps = plan.steps.mapIndexed { index, step ->
                    step.copy(order = index + 1)
                }
                return plan.copy(steps = fixedSteps)
            }

            plan
        } catch (e: LLMException) {
            throw e
        } catch (e: Exception) {
            throw LLMException("JSON解析失败: ${e.message}", e)
        }
    }

    /**
     * 从文本中提取 JSON 字符串。
     * 处理以下情况：
     * - 纯 JSON 字符串
     * - markdown 代码块包裹: ```json ... ```
     * - markdown 代码块无语言: ``` ... ```
     * - JSON 前后有说明文字
     */
    private fun extractJson(text: String): String? {
        val trimmed = text.trim()

        // 1. 尝试找到 markdown 代码块中的 JSON
        val codeBlockPattern = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
        val codeMatch = codeBlockPattern.find(trimmed)
        if (codeMatch != null) {
            return codeMatch.groupValues[1].trim()
        }

        // 2. 尝试直接找到 JSON 对象（从 { 到 }）
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            var depth = 0
            var jsonEnd = firstBrace
            for (i in firstBrace until trimmed.length) {
                when (trimmed[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            jsonEnd = i + 1
                            break
                        }
                    }
                }
            }
            if (depth == 0) {
                return trimmed.substring(firstBrace, jsonEnd)
            }
        }

        return null
    }
}
