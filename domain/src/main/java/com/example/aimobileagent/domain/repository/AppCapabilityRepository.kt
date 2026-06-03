package com.example.aimobileagent.domain.repository

import com.example.aimobileagent.domain.model.AppCapability
import kotlinx.coroutines.flow.Flow

/**
 * App 能力目录仓库接口。
 */
interface AppCapabilityRepository {
    fun observeAll(): Flow<List<AppCapability>>
    suspend fun getAll(): List<AppCapability>
    suspend fun add(packageName: String, appName: String, supportedActions: List<String>)
    suspend fun remove(packageName: String)
    suspend fun getInstalledApps(): List<InstalledAppInfo>
}

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean
)
