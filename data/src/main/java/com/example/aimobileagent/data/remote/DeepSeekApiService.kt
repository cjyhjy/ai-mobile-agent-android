package com.example.aimobileagent.data.remote

import com.example.aimobileagent.data.remote.dto.LLMChatRequest
import com.example.aimobileagent.data.remote.dto.LLMChatResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * DeepSeek API 接口（兼容 OpenAI 格式）。
 */
interface DeepSeekApiService {
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: LLMChatRequest
    ): LLMChatResponse
}
