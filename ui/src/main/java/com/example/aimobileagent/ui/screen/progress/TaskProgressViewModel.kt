package com.example.aimobileagent.ui.screen.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aimobileagent.domain.model.ExecutionResult
import com.example.aimobileagent.domain.model.Task
import com.example.aimobileagent.domain.model.TaskStatus
import com.example.aimobileagent.domain.repository.TaskRepository
import com.example.aimobileagent.domain.usecase.ExecuteTaskUseCase
import com.example.aimobileagent.domain.usecase.StepExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskProgressUiState(
    val task: Task? = null,
    val isExecuting: Boolean = false,
    val result: ExecutionResult? = null,
    val error: String? = null
)

@HiltViewModel
class TaskProgressViewModel @Inject constructor(
    private val executeTaskUseCase: ExecuteTaskUseCase,
    private val stepExecutor: StepExecutor,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskProgressUiState())
    val uiState: StateFlow<TaskProgressUiState> = _uiState.asStateFlow()

    fun loadTask(taskId: String) {
        viewModelScope.launch {
            taskRepository.observeTask(taskId).collect { task ->
                _uiState.update { it.copy(task = task) }
            }
        }
    }

    fun executeTask(taskId: String) {
        if (_uiState.value.isExecuting) return

        _uiState.update { it.copy(isExecuting = true, error = null) }

        viewModelScope.launch {
            try {
                val result = executeTaskUseCase(taskId, stepExecutor)
                _uiState.update { it.copy(isExecuting = false, result = result) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isExecuting = false, error = e.message ?: "执行失败")
                }
            }
        }
    }
}
