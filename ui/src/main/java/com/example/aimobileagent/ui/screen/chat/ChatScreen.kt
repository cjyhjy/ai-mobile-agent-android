package com.example.aimobileagent.ui.screen.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.aimobileagent.ui.component.MessageBubble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToProgress: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
    }
    // 流式输出时也滚动
    LaunchedEffect(uiState.streamingText) {
        if (uiState.streamingText.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
    }

    // 文件选择器
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readFile(context, it, viewModel) }
    }

    // === API Key 弹窗 ===
    if (uiState.showApiKeyDialog) {
        var keyInput by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { viewModel.dismissApiKeyDialog() },
            title = { Text("配置 API Key") },
            text = {
                OutlinedTextField(value = keyInput, onValueChange = { keyInput = it },
                    label = { Text("API Key") }, singleLine = true)
            },
            confirmButton = { TextButton(onClick = { viewModel.saveApiKey(keyInput) }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { viewModel.dismissApiKeyDialog() }) { Text("取消") } }
        )
    }

    // === 模型选择器 Bottom Sheet ===
    if (uiState.showModelPicker) {
        ModalBottomSheet(onDismissRequest = { viewModel.toggleModelPicker() }) {
            Column(Modifier.padding(16.dp)) {
                Text("选择模型", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                uiState.availableModels.forEach { model ->
                    val isSelected = model.name == uiState.selectedModel
                    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        onClick = { viewModel.selectModel(model) },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = isSelected, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(model.name, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.width(8.dp))
                                    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = MaterialTheme.shapes.extraSmall) {
                                        Text(model.provider, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                                    }
                                }
                                Text(model.endpoint, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🤖 AI 手机智能体") },
                actions = {
                    // 模型选择按钮
                    TextButton(onClick = { viewModel.toggleModelPicker() }) {
                        Text(uiState.selectedModel.take(10), fontSize = 11.sp)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onNavigateToHistory) { Icon(Icons.Default.History, contentDescription = "历史") }
                    IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, contentDescription = "设置") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.messages.isEmpty()) {
                    item {
                        Text("👋 你好！我是你的手机智能助手。\n\n你可以告诉我你想做什么，比如：\n• 聊天提问\n• 发送文件让我分析\n• 执行手机任务",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                items(uiState.messages, key = { it.timestamp }) { message -> MessageBubble(message = message) }
                uiState.currentTask?.let { task ->
                    item { TaskPlanCard(task = task, onConfirm = { onNavigateToProgress(task.id) }) }
                }
            }

            uiState.error?.let { error ->
                Snackbar(modifier = Modifier.padding(16.dp),
                    action = { TextButton(onClick = { viewModel.clearError() }) { Text("关闭") } }) {
                    Text(error)
                }
            }

            InputBar(text = uiState.inputText, onTextChange = { viewModel.onInputChanged(it) },
                onSend = { viewModel.sendCommand() },
                onStop = { viewModel.stopStreaming() },
                onAttach = { filePicker.launch(arrayOf("text/*", "application/json", "*/*")) },
                isProcessing = uiState.isProcessing)
        }
    }
}

@Composable
private fun InputBar(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit,
                     onStop: () -> Unit, onAttach: () -> Unit, isProcessing: Boolean) {
    Surface(tonalElevation = 4.dp, shadowElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // 附件按钮
            IconButton(onClick = onAttach) {
                Icon(Icons.Default.AttachFile, contentDescription = "上传文件")
            }
            OutlinedTextField(value = text, onValueChange = onTextChange, modifier = Modifier.weight(1f),
                placeholder = { Text("告诉我你想做什么...") }, enabled = !isProcessing, maxLines = 3)
            Spacer(Modifier.width(8.dp))
            if (isProcessing) {
                FloatingActionButton(onClick = onStop, modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.error) {
                    Icon(Icons.Default.Stop, contentDescription = "停止")
                }
            } else {
                FloatingActionButton(onClick = onSend, modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Send, contentDescription = "发送")
                }
            }
        }
    }
}

@Composable
private fun TaskPlanCard(task: com.example.aimobileagent.domain.model.Task, onConfirm: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("📋 任务计划", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("意图: ${task.intent}", style = MaterialTheme.typography.bodyMedium)
            Text("步骤: ${task.steps.size} 步 | 置信度: ${(task.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(Modifier.height(8.dp))
            task.steps.forEach { step ->
                Text("  ${step.orderIndex}. ${step.actionType} → ${step.targetElement ?: step.targetApp ?: "?"}",
                    style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) { Text("确认执行") }
        }
    }
}

private fun readFile(context: android.content.Context, uri: Uri, viewModel: ChatViewModel) {
    try {
        val name = uri.lastPathSegment ?: "file"
        val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
        viewModel.onFileSelected(uri, name, content)
    } catch (e: Exception) {
        android.util.Log.e("ChatScreen", "文件读取失败: ${e.message}", e)
    }
}
