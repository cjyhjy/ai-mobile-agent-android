package com.example.aimobileagent.domain.usecase

import com.example.aimobileagent.domain.model.Task
import com.example.aimobileagent.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow

/**
 * 获取历史任务列表。
 */
class GetTaskHistoryUseCase(
    private val taskRepository: TaskRepository
) {
    /** 观察所有历史任务（按时间倒序） */
    fun observeAll(): Flow<List<Task>> = taskRepository.observeAllTasks()

    /** 获取单个任务详情 */
    suspend fun getTask(taskId: String): Task? = taskRepository.getTaskById(taskId)
}
