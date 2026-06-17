package com.example.aimobileagent.data.remote

import android.content.SharedPreferences
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Singleton
class StreamingLLMClient @Inject constructor(
    private val prefs: SharedPreferences,
    private val okHttpClient: OkHttpClient
) {
    fun streamChat(
        userMessage: String,
        history: List<Pair<String, String>> = emptyList(),
        availableApps: List<String> = emptyList()
    ): Flow<StreamEvent> = callbackFlow {
        val apiKey = prefs.getString("api_key", "") ?: ""
        val model = prefs.getString("model_name", "deepseek-chat") ?: "deepseek-chat"

        if (apiKey.isBlank()) { trySend(StreamEvent.Error("API Key未配置")); close(); return@callbackFlow }

        val appListText = if (availableApps.isNotEmpty())
            "\n\n可操作的手机App（格式: 应用名(包名)）:\n${availableApps.joinToString(", ")}\n\n当用户想操作手机时，用JSON回复: {\"mode\":\"task\",\"intent\":\"描述\",\"steps\":[{\"order\":1,\"action\":\"open_app\",\"target\":\"包名\",\"params\":{}}]}。target字段填括号里的包名。其他时候正常聊天。"
        else ""
        val sysPrompt = "你是智能手机助手，能聊天也能帮执行手机任务。用中文回复，支持Markdown。$appListText"

        // 用 org.json 构建 body——自动转义所有控制字符
        val msgs = org.json.JSONArray()
        msgs.put(org.json.JSONObject().apply {
            put("role", "system"); put("content", sysPrompt)
        })
        for ((role, content) in history.takeLast(10)) {
            msgs.put(org.json.JSONObject().apply { put("role", role); put("content", content) })
        }
        msgs.put(org.json.JSONObject().apply { put("role", "user"); put("content", userMessage) })

        val body = org.json.JSONObject().apply {
            put("model", model); put("messages", msgs); put("stream", true)
        }.toString()

        val req = Request.Builder()
            .url("https://api.deepseek.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        trySend(StreamEvent.Thinking)
        val resp = okHttpClient.newCall(req).execute()
        if (!resp.isSuccessful) {
            trySend(StreamEvent.Error("API ${resp.code}: ${resp.body?.string()?.take(200)}"))
            close(); return@callbackFlow
        }

        val respBody = resp.body
        if (respBody == null) {
            trySend(StreamEvent.Error("API 响应体为空"))
            close(); return@callbackFlow
        }

        val reader = BufferedReader(InputStreamReader(respBody.byteStream()))
        val fullText = StringBuilder()
        var line: String?
        var chunkCount = 0
        var lastEmit = 0L
        try {
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: break
                if (!l.startsWith("data: ")) continue
                val data = l.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                try {
                    val content = extractContent(data)
                    if (content.isNotEmpty()) {
                        fullText.append(content)
                        chunkCount++
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 50 || fullText.length < 30) {
                            lastEmit = now
                            send(StreamEvent.Chunk(content, fullText.toString()))
                        }
                    }
                } catch (_: Exception) {}
            }
            if (fullText.isNotEmpty()) send(StreamEvent.Chunk("", fullText.toString()))
        } finally {
            reader.close(); resp.close()
        }

        val final = fullText.toString().trim()
        if (final.isBlank()) send(StreamEvent.Error("空响应"))
        else send(StreamEvent.Done(parseResponse(final)))
        close()
    }

    private fun extractContent(jsonLine: String): String {
        // Simple JSON parsing to avoid kotlinx.serialization issues
        val pattern = Regex(""""content"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        return pattern.find(jsonLine)?.groupValues?.get(1)?.let {
            it.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\")
        } ?: ""
    }

    private fun parseResponse(text: String): ParsedResponse {
        val modeM = Regex(""""mode"\s*:\s*"(chat|task)"""").find(text)
        val mode = modeM?.groupValues?.get(1) ?: "chat"

        if (mode == "task") {
            // 提取 intent
            val intentM = Regex(""""intent"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(text)
            val intent = intentM?.groupValues?.get(1)?.replace("\\n","\n")?.replace("\\\"","\"") ?: ""

            // 提取 steps JSON 数组（用 org.json 验证格式）
            val stepsJson = try {
                val jsonObj = org.json.JSONObject(text)
                val stepsArr = jsonObj.optJSONArray("steps")
                stepsArr?.toString() ?: ""
            } catch (_: Exception) {
                // 回退：正则提取 steps 数组
                val stepsM = Regex(""""steps"\s*:\s*(\[[\s\S]*?\])\s*\}""").find(text)
                stepsM?.groupValues?.get(1)?.trim() ?: ""
            }
            return ParsedResponse(mode = "task", intent = intent, rawText = text, stepsJson = stepsJson)
        }

        // chat 模式
        val replyM = Regex(""""reply"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(text)
        val reply = replyM?.groupValues?.get(1)?.replace("\\n","\n") ?: text
        if (modeM != null) {
            return ParsedResponse(mode = "chat", reply = reply, rawText = text)
        }
        return ParsedResponse("chat", text)
    }
}

sealed class StreamEvent {
    data object Thinking : StreamEvent()
    data class Chunk(val delta: String, val fullText: String) : StreamEvent()
    data class Done(val response: ParsedResponse) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}
data class ParsedResponse(val mode: String, val reply: String = "", val intent: String = "", val rawText: String = "", val stepsJson: String = "")
