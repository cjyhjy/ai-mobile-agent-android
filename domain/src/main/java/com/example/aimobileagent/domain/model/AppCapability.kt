package com.example.aimobileagent.domain.model

/**
 * 领域模型：App 能力信息。
 */
data class AppCapability(
    val id: Int = 0,
    val packageName: String,
    val appName: String,
    val supportedActions: List<String>,
    val resourceIdMap: Map<String, String> = emptyMap()
)
