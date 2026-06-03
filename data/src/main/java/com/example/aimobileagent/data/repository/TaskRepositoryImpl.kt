package com.example.aimobileagent.data.repository

import com.example.aimobileagent.data.local.AppDatabase
import com.example.aimobileagent.data.local.TaskDao
import com.example.aimobileagent.data.local.entity.StepEntity
import com.example.aimobileagent.data.local.entity.TaskEntity
import com.example.aimobileagent.domain.model.Task
import com.example.aimobileagent.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TaskRepository 的 Room 实现。
 * Domain 层定义接口，此处用 Room 完成实际数据存取。
 */
@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val database: AppDatabase
) : TaskRepository {

    private val taskDao: TaskDao = database.taskDao()

    override fun observeAllTasks(): Flow<List<Task>> {
        return taskDao.observeAllTasks().map { entities ->
            entities.map { entity ->
                val steps = taskDao.getStepsForTask(entity.id)
                entity.toDomain(steps.map { it.toDomain() })
            }
        }
    }

    override fun observeTask(taskId: String): Flow<Task?> {
        return taskDao.observeTask(taskId).map { entity ->
            entity?.let {
                val steps = taskDao.getStepsForTask(taskId)
                it.toDomain(steps.map { s -> s.toDomain() })
            }
        }
    }

    override suspend fun saveTask(task: Task) {
        val entity = TaskEntity.fromDomain(task)
        taskDao.insertTask(entity)

        // 同时保存步骤（自动修正空的 taskId）
        if (task.steps.isNotEmpty()) {
            val fixedSteps = task.steps.map { step ->
                if (step.taskId.isBlank()) step.copy(taskId = task.id) else step
            }
            val stepEntities = fixedSteps.map { StepEntity.fromDomain(it) }
            taskDao.insertSteps(stepEntities)
        }
    }

    override suspend fun deleteTask(taskId: String) {
        taskDao.deleteTask(taskId)
    }

    override suspend fun getAllTasks(): List<Task> {
        return taskDao.getAllTasks().map { entity ->
            val steps = taskDao.getStepsForTask(entity.id)
            entity.toDomain(steps.map { it.toDomain() })
        }
    }

    override suspend fun getTaskById(taskId: String): Task? {
        val entity = taskDao.getTaskById(taskId) ?: return null
        val steps = taskDao.getStepsForTask(taskId)
        return entity.toDomain(steps.map { it.toDomain() })
    }
}
