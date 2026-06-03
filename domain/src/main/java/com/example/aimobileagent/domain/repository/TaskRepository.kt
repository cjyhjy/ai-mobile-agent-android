package com.example.aimobileagent.domain.repository

import com.example.aimobileagent.domain.model.Task
import kotlinx.coroutines.flow.Flow

/**
 * 任务持久化接口。
 * Domain 层只定义契约，Data 层用 Room 实现。
 */
interface TaskRepository {
    /** 观察所有任务（按创建时间倒序） */
    fun observeAllTasks(): Flow<List<Task>>

    /** 观察单个任务及其步骤 */
    fun observeTask(taskId: String): Flow<Task?>

    /** 保存或更新任务 */
    suspend fun saveTask(task: Task)

    /** 删除任务 */
    suspend fun deleteTask(taskId: String)

    /** 获取所有任务（一次性） */
    suspend fun getAllTasks(): List<Task>

    /** 根据 ID 获取任务 */
    suspend fun getTaskById(taskId: String): Task?
}
