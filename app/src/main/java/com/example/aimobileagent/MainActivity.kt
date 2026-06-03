package com.example.aimobileagent

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.aimobileagent.domain.model.TaskStatus
import com.example.aimobileagent.domain.repository.LLMRepository
import com.example.aimobileagent.domain.repository.TaskRepository
import com.example.aimobileagent.ui.navigation.NavGraph
import com.example.aimobileagent.ui.theme.AIMobileAgentTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var llmRepository: LLMRepository
    @Inject lateinit var taskRepository: TaskRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 处理 intent 传来的测试指令
        handleIntent(intent)

        setContent {
            AIMobileAgentTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val cmd = intent?.getStringExtra("cmd") ?: return
        if (cmd.isBlank()) return

        // 如果传了 api_key，先保存到 SharedPreferences
        intent.getStringExtra("api_key")?.let { key ->
            getSharedPreferences("agent_prefs", MODE_PRIVATE)
                .edit().putString("api_key", key).apply()
            Log.e("MainActivity", "API Key 已写入")
        }

        Log.e("MainActivity", "CLI 命令: $cmd")

        lifecycleScope.launch {
            try {
                val task = withContext(Dispatchers.IO) {
                    llmRepository.planTask(cmd,
                        listOf("com.android.settings", "com.tencent.mm",
                               "com.android.gallery3d", "com.android.browser"))
                }

                if (task.intent.startsWith("chat:")) {
                    val reply = task.intent.removePrefix("chat:")
                    Log.e("MainActivity", "✅ 聊天回复: $reply")
                } else {
                    Log.e("MainActivity", "✅ 任务: ${task.intent}, ${task.steps.size}步")
                    task.steps.forEach { s ->
                        Log.e("MainActivity", "  Step${s.orderIndex}: ${s.actionType}→${s.targetApp ?: s.targetElement}")
                    }
                    // 修复步骤的 taskId（LLMRepositoryImpl 中设为空字符串）
                    val fixedSteps = task.steps.map { it.copy(taskId = task.id) }
                    taskRepository.saveTask(task.copy(steps = fixedSteps, status = TaskStatus.READY))
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ 失败: ${e.message}", e)
            }
        }
    }
}
