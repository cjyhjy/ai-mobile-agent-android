package com.example.aimobileagent.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.aimobileagent.data.local.entity.AppCapabilityEntity
import com.example.aimobileagent.data.local.entity.StepEntity
import com.example.aimobileagent.data.local.entity.TaskEntity

/**
 * Room 数据库单例。
 * 包含三张表：tasks, steps, app_capabilities
 */
@Database(
    entities = [
        TaskEntity::class,
        StepEntity::class,
        AppCapabilityEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun appCapabilityDao(): AppCapabilityDao
}
