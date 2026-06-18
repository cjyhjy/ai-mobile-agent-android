package com.example.aimobileagent.ui.screen.progress

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.aimobileagent.domain.model.ExecutionResult
import com.example.aimobileagent.domain.model.StepStatus
import com.example.aimobileagent.domain.model.TaskStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskProgressScreen(
    taskId: String,
    onBack: () -> Unit,
    viewModel: TaskProgressViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(taskId) {
        viewModel.loadTask(taskId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("任务执行") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            val task = uiState.task

            if (task == null) {
                CircularProgressIndicator()
                return@Column
            }

            // 任务头部
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📋 ${task.userCommand}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "意图: ${task.intent} | 状态: ${task.status.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 步骤列表
            Text("执行步骤", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(task.steps.sortedBy { it.orderIndex }, key = { it.id }) { step ->
                    StepItem(step = step)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 执行按钮 / 结果
            when {
                uiState.isExecuting -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("正在执行...", style = MaterialTheme.typography.bodyMedium)
                }
                uiState.result != null -> {
                    when (val result = uiState.result!!) {
                        is ExecutionResult.Success -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("✅ 任务完成", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "完成 ${result.completedSteps} 步，耗时 ${result.totalDurationMs}ms",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        is ExecutionResult.Failure -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("❌ 执行失败", style = MaterialTheme.typography.titleMedium)
                                    Text(result.error, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                else -> {
                    Button(
                        onClick = { viewModel.executeTask(taskId) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = task.status == TaskStatus.READY
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("开始执行")
                    }
                }
            }
        }
    }
}

@Composable
private fun StepItem(step: com.example.aimobileagent.domain.model.Step) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (step.status) {
                StepStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                StepStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                StepStatus.RUNNING -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            Icon(
                imageVector = when (step.status) {
                    StepStatus.PENDING -> Icons.Default.Circle
                    StepStatus.RUNNING -> Icons.Default.PlayArrow
                    StepStatus.SUCCESS -> Icons.Default.CheckCircle
                    StepStatus.FAILED -> Icons.Default.Error
                    StepStatus.SKIPPED -> Icons.Default.RemoveCircle
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = when (step.status) {
                    StepStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                    StepStatus.FAILED -> MaterialTheme.colorScheme.error
                    StepStatus.RUNNING -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Step ${step.orderIndex}: ${step.actionType}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = step.targetElement ?: step.targetApp ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                step.errorMessage?.let {
                    Text(
                        text = "错误: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
