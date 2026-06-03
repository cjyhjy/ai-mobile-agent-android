package com.example.aimobileagent.data.local

import androidx.room.*
import com.example.aimobileagent.data.local.entity.StepEntity
import com.example.aimobileagent.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO：任务数据访问。
 */
@Dao
interface TaskDao {
    // ========== Task CRUD ==========

    @Query("SELECT * FROM tasks ORDER BY created_at DESC")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    fun observeTask(taskId: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY created_at DESC")
    suspend fun getAllTasks(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: String)

    // ========== Step CRUD ==========

    @Query("SELECT * FROM steps WHERE task_id = :taskId ORDER BY order_index ASC")
    suspend fun getStepsForTask(taskId: String): List<StepEntity>

    @Query("SELECT * FROM steps WHERE task_id = :taskId ORDER BY order_index ASC")
    fun observeStepsForTask(taskId: String): Flow<List<StepEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<StepEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: StepEntity)

    @Query("DELETE FROM steps WHERE task_id = :taskId")
    suspend fun deleteStepsForTask(taskId: String)
}
