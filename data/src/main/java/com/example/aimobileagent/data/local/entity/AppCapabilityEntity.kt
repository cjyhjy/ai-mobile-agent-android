package com.example.aimobileagent.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room 实体：App 能力目录（预置数据）。
 * 告诉 LLM 每个 App 支持哪些操作，以及如何定位 UI 元素。
 */
@Entity(tableName = "app_capabilities")
data class AppCapabilityEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_name") val appName: String,
    @ColumnInfo(name = "supported_actions") val supportedActions: String, // JSON array
    @ColumnInfo(name = "resource_id_map") val resourceIdMap: String        // JSON map
)
