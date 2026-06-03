package com.example.aimobileagent.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.aimobileagent.domain.model.Step
import com.example.aimobileagent.domain.model.StepStatus

/**
 * Room 实体：步骤表。
 */
@Entity(
    tableName = "steps",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["task_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("task_id")]
)
data class StepEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    @ColumnInfo(name = "action_type") val actionType: String,
    @ColumnInfo(name = "target_app") val targetApp: String?,
    @ColumnInfo(name = "target_element") val targetElement: String?,
    @ColumnInfo(name = "params_json") val paramsJson: String, // JSON object
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
    @ColumnInfo(name = "execution_duration_ms") val executionDurationMs: Long?
) {
    companion object {
        /** Domain → Entity */
        fun fromDomain(step: Step): StepEntity {
            val paramsJson = if (step.params.isNotEmpty()) {
                step.params.entries.joinToString(",", "{", "}") { (k, v) ->
                    "\"$k\":\"$v\""
                }
            } else "{}"
            return StepEntity(
                id = step.id,
                taskId = step.taskId,
                orderIndex = step.orderIndex,
                actionType = step.actionType,
                targetApp = step.targetApp,
                targetElement = step.targetElement,
                paramsJson = paramsJson,
                status = step.status.name,
                errorMessage = step.errorMessage,
                executionDurationMs = step.executionDurationMs
            )
        }
    }

    /** Entity → Domain */
    fun toDomain(): Step = Step(
        id = id,
        taskId = taskId,
        orderIndex = orderIndex,
        actionType = actionType,
        targetApp = targetApp,
        targetElement = targetElement,
        params = parseParams(paramsJson),
        status = try { StepStatus.valueOf(status) } catch (_: Exception) { StepStatus.PENDING },
        errorMessage = errorMessage,
        executionDurationMs = executionDurationMs
    )

    private fun parseParams(json: String): Map<String, String> {
        if (json.isBlank() || json == "{}") return emptyMap()
        return try {
            json.removeSurrounding("{", "}")
                .split(",")
                .map { it.trim() }
                .filter { it.contains(":") }
                .associate {
                    val (k, v) = it.split(":", limit = 2)
                    k.trim().removeSurrounding("\"") to v.trim().removeSurrounding("\"")
                }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
