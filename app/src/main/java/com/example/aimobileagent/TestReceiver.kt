package com.example.aimobileagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.aimobileagent.domain.model.TaskStatus
import com.example.aimobileagent.domain.repository.LLMRepository
import com.example.aimobileagent.domain.repository.TaskRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * 测试用 BroadcastReceiver。
 * 通过 ADB 发送命令，无需 UI 交互:
 *   adb shell am broadcast -a com.example.aimobileagent.TEST_CMD --es cmd "打开飞行模式"
 */
@AndroidEntryPoint
class TestReceiver : BroadcastReceiver() {

    @Inject lateinit var llmRepository: LLMRepository
    @Inject lateinit var taskRepository: TaskRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: return
        val pending = goAsync() // 保护协程生命周期，系统不会过早回收
        Log.i("TestReceiver", "收到测试命令: $cmd")

        scope.launch {
            try {
                val task = llmRepository.planTask(cmd,
                    listOf("com.android.settings", "com.tencent.mm", "com.android.gallery3d")
                )
                val readyTask = task.copy(status = TaskStatus.READY)
                taskRepository.saveTask(readyTask)

                if (task.intent.startsWith("chat:")) {
                    Log.i("TestReceiver", "✅ 聊天: ${task.intent.removePrefix("chat:")}")
                } else {
                    Log.i("TestReceiver", "✅ 任务: intent=${task.intent}, steps=${task.steps.size}")
                    task.steps.forEach { step ->
                        Log.i("TestReceiver", "  Step ${step.orderIndex}: ${step.actionType} → ${step.targetApp ?: step.targetElement}")
                    }
                }
            } catch (e: Exception) {
                Log.w("TestReceiver", "❌ 失败: ${e.message}", e)
            } finally {
                pending.finish()
            }
        }
    }
}
