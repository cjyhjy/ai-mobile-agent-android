package com.example.aimobileagent.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.aimobileagent.domain.model.Task
import com.example.aimobileagent.domain.model.TaskStatus

/**
 * Room 实体：任务表。
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_command") val userCommand: String,
    @ColumnInfo(name = "llm_raw_response") val llmRawResponse: String,
    @ColumnInfo(name = "intent") val intent: String,
    @ColumnInfo(name = "confidence") val confidence: Float,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "apps_involved") val appsInvolved: String // JSON array
) {
    companion object {
        /** Entity → Domain */
        fun fromDomain(task: Task): TaskEntity = TaskEntity(
            id = task.id,
            userCommand = task.userCommand,
            llmRawResponse = task.llmRawResponse,
            intent = task.intent,
            confidence = task.confidence,
            status = task.status.name,
            createdAt = task.createdAt,
            completedAt = task.completedAt,
            appsInvolved = task.appsInvolved.joinToString(",")
        )
    }

    /** Entity → Domain（不含 steps，steps 需要单独查询） */
    fun toDomain(steps: List<com.example.aimobileagent.domain.model.Step> = emptyList()): Task = Task(
        id = id,
        userCommand = userCommand,
        llmRawResponse = llmRawResponse,
        intent = intent,
        confidence = confidence,
        steps = steps,
        status = try { TaskStatus.valueOf(status) } catch (_: Exception) { TaskStatus.PLANNING },
        createdAt = createdAt,
        completedAt = completedAt,
        appsInvolved = if (appsInvolved.isNotBlank()) appsInvolved.split(",") else emptyList()
    )
}
