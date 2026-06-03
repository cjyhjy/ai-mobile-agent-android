package com.example.aimobileagent.ui.screen.appmanage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.aimobileagent.domain.model.AppCapability
import com.example.aimobileagent.domain.repository.InstalledAppInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManageScreen(
    onBack: () -> Unit,
    viewModel: AppManageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理 App 能力") },
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
            // 头部说明
            Text(
                text = "已注册 ${uiState.registeredApps.size} 个 App",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "这些 App 可以被智能助手调用和操控",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 已注册列表
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.registeredApps) { app ->
                    RegisteredAppItem(
                        app = app,
                        onRemove = { viewModel.removeApp(app.packageName, app.appName) }
                    )
                }

                // 添加按钮
                item {
                    Button(
                        onClick = {
                            showAddSheet = true
                            viewModel.scanInstalledApps()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("从手机添加 App")
                    }
                }
            }

            // 消息提示
            uiState.message?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Snackbar(
                    modifier = Modifier.fillMaxWidth(),
                    action = {
                        TextButton(onClick = { viewModel.clearMessage() }) {
                            Text("关闭")
                        }
                    }
                ) { Text(msg) }
            }
        }
    }

    // 底部弹出：选择要添加的 App
    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .heightIn(max = 500.dp)
            ) {
                Text(
                    text = "选择要添加的 App",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.isScanning) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (uiState.installedApps.isEmpty()) {
                    Text(
                        text = "没有可添加的 App（所有已安装 App 已注册）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                } else {
                    LazyColumn {
                        items(uiState.installedApps) { app ->
                            InstalledAppRow(
                                app = app,
                                onAdd = {
                                    viewModel.addApp(app.packageName, app.appName)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegisteredAppItem(
    app: AppCapability,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.appName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            TextButton(
                onClick = onRemove,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("移除")
            }
        }
    }
}

@Composable
private fun InstalledAppRow(
    app: InstalledAppInfo,
    onAdd: () -> Unit
) {
    ListItem(
        headlineContent = { Text(app.appName) },
        supportingContent = {
            Text(
                text = "${app.packageName}${if (app.isSystemApp) " (系统)" else ""}",
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Icon(
                Icons.Default.Android,
                contentDescription = null,
                tint = if (app.isSystemApp) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        },
        trailingContent = {
            TextButton(onClick = onAdd) {
                Text("添加")
            }
        }
    )
}
