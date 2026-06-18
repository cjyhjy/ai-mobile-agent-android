# AI Mobile Agent — 架构文档

> **适用读者**：接手本项目的开发者 / AI 助手  
> **最后更新**：2026-06-17  
> **代码版本**：v2.4.0

---

## 目录

1. [项目概述](#1-项目概述)
2. [架构总览](#2-架构总览)
3. [模块详解](#3-模块详解)
4. [数据流](#4-数据流)
5. [数据库设计](#5-数据库设计)
6. [依赖注入 (Hilt)](#6-依赖注入-hilt)
7. [LLM 集成](#7-llm-集成)
8. [执行引擎](#8-执行引擎)
9. [UI 层](#9-ui-层)
10. [如何添加新功能](#10-如何添加新功能)
11. [已知问题与注意事项](#11-已知问题与注意事项)

---

## 1. 项目概述

AI Mobile Agent 是一个 Android 应用，用户用自然语言描述需求，AI 自动分解为多步操作并通过 AccessibilityService 执行。

**核心能力**：
- 🤖 自然语言聊天（流式 SSE，多轮对话）
- 📋 任务规划（LLM 生成结构化 JSON 步骤）
- ▶️ 自动执行（AccessibilityService 操控手机）
- 🧠 多模型支持（DeepSeek / OpenAI / Anthropic / Google）

**技术栈**：Kotlin + Jetpack Compose + Room + Hilt + OkHttp + org.json  
**最小 SDK**：Android 10 (API 29)  
**模块数**：5（app / domain / data / execution / ui）

---

## 2. 架构总览

### 2.1 分层架构（Clean Architecture + 课程三层）

```
┌──────────────────────────────────────────────┐
│                   UI 层                        │
│  Compose Screens + ViewModels (Hilt)          │
│  ChatScreen, TaskProgressScreen, ...         │
├──────────────────────────────────────────────┤
│                 Domain 层 (纯 Kotlin)          │
│  Models, UseCases, Repository 接口            │
│  不依赖任何 Android 框架                       │
├──────────────────────────────────────────────┤
│                 Data 层                        │
│  Room DB, Retrofit, SSE Streaming,           │
│  Repository 实现, LLMResponseParser          │
├──────────────────────────────────────────────┤
│               Execution 层                     │
│  AccessibilityService, StepExecutors,        │
│  ScreenParser, SafetyChecker                 │
└──────────────────────────────────────────────┘
         ↑              ↑
    app (入口)    app (Hilt DI 集中配置)
```

### 2.2 模块依赖

```
app ──► domain ◄── data
 │                 │
 ├──► execution    │
 └──► ui ◄─────────┘
```

规则：
- **domain** 不依赖任何模块（纯 Kotlin）
- **data** 依赖 domain（实现 Repository 接口）
- **execution** 依赖 domain（实现 StepExecutor 接口）
- **ui** 依赖 domain + data（ViewModel 消费数据）
- **app** 依赖所有模块（Application 入口 + Hilt 配置）

### 2.3 文件统计

| 模块 | 文件数 | 关键类 |
|------|--------|--------|
| app | 5 | AgentApplication, MainActivity, AppModule, TestReceiver |
| domain | 9 | Task, Step, ExecuteTaskUseCase, ProcessCommandUseCase |
| data | 14 | LLMRepositoryImpl, StreamingLLMClient, LLMResponseParser, TaskRepositoryImpl |
| execution | 16 | AgentAccessibilityService, StepExecutorFactory, 6 Executors |
| ui | 15 | ChatScreen/ViewModel, TaskProgressScreen/ViewModel, NavGraph |

---

## 3. 模块详解

### 3.1 domain — 纯业务逻辑

```
domain/
├── model/
│   ├── Task.kt           # 任务实体
│   ├── Step.kt            # 步骤实体
│   ├── StepStatus.kt      # PENDING/RUNNING/SUCCESS/FAILED/SKIPPED
│   ├── TaskStatus.kt      # PLANNING/READY/RUNNING/COMPLETED/FAILED
│   ├── ExecutionResult.kt # Success(completedSteps, totalDurationMs) / Failure(step, error)
│   └── AppCapability.kt   # 应用能力实体
├── repository/
│   ├── TaskRepository.kt         # 接口: observeAll, observeTask, saveTask
│   ├── LLMRepository.kt          # 接口: planTask(command, availableApps) → Task
│   └── AppCapabilityRepository.kt # 接口: observeAll, getAll, add, remove
└── usecase/
    ├── ProcessCommandUseCase.kt   # LLM 规划 → 创建 Task
    ├── ExecuteTaskUseCase.kt      # OTAV 循环执行步骤
    └── GetTaskHistoryUseCase.kt  # 查询历史
```

**关键接口**：

```kotlin
// StepExecutor — Execution 层实现
interface StepExecutor {
    suspend fun execute(step: Step): StepResult
    suspend fun recover(step: Step, error: String): StepResult
}

// ScreenStateObserver — Execution 层实现
interface ScreenStateObserver {
    suspend fun observe(): ScreenObservation
    suspend fun checkSafety(): SafetyCheckResult
    suspend fun verify(step: Step): VerificationResult?
}
```

### 3.2 data — 数据持久化与网络

```
data/
├── local/
│   ├── AppDatabase.kt         # Room 数据库
│   ├── TaskDao.kt             # 任务 DAO
│   ├── AppCapabilityDao.kt    # 应用能力 DAO
│   ├── DataModule.kt          # Hilt: Room + Retrofit + Repository 绑定
│   ├── UserPreferencesManager.kt
│   └── entity/
│       ├── TaskEntity.kt      # Room 实体
│       ├── StepEntity.kt      # Room 实体
│       └── AppCapabilityEntity.kt
├── remote/
│   ├── StreamingLLMClient.kt  # **核心** SSE 流式客户端
│   ├── LLMResponseParser.kt   # JSON 提取 + 容错
│   ├── PromptTemplateEngine.kt # 提示词模板
│   ├── DeepSeekApiService.kt  # Retrofit 接口定义
│   └── dto/
│       └── LLMDto.kt          # 网络 DTO
└── repository/
    ├── LLMRepositoryImpl.kt       # planTask 实现（非流式路径）
    ├── TaskRepositoryImpl.kt      # Room CRUD + taskId 自动修正
    └── AppCapabilityRepositoryImpl.kt
```

**StreamingLLMClient** 是数据层的核心类：

```
streamChat(userMessage, history, availableApps) → Flow<StreamEvent>

StreamEvent:
  ├── Thinking          # 开始推理
  ├── Chunk(delta, fullText)  # 流式文本块（50ms 节流）
  ├── Done(ParsedResponse)     # 完成：含 mode(chat/task)、stepsJson
  └── Error(message)           # 错误

ParsedResponse:
  mode: "chat" | "task"
  reply: String        # chat 模式的回复文本
  intent: String       # task 模式的意图描述
  stepsJson: String    # task 模式的步骤 JSON 数组
  rawText: String      # LLM 原始返回全文
```

**LLMResponseParser** JSON 提取策略（4 级回退）：
1. Markdown ` ```json ``` ` 代码块提取
2. `"mode":"task"` 标记 + 逐字符回溯找 `{`
3. `{"mode"` 字面量搜索
4. 兜底：全文当 chat 回复

### 3.3 execution — 无障碍执行引擎

```
execution/
├── AgentAccessibilityService.kt  # **核心** AccessibilityService 实现
├── StepExecutorFactory.kt        # 按 actionType 分发到具体执行器
├── ExecutionModule.kt            # Hilt 模块
├── executor/
│   ├── OpenAppExecutor.kt    # open_app: 启动 App
│   ├── TapElementExecutor.kt # tap: 点击 UI 元素
│   ├── TypeTextExecutor.kt   # type: 输入文本
│   ├── SwipeExecutor.kt      # swipe: 滑动手势
│   ├── SearchExecutor.kt     # search: 搜索
│   └── ShareToExecutor.kt    # share_to: 分享
└── screen/
    ├── ScreenParser.kt             # UI 树解析
    ├── ElementLocator.kt           # 元素定位（文本/描述/resourceId）
    ├── SafetyChecker.kt            # 安全检测（支付/密码页面）
    ├── ScreenStateObserverImpl.kt  # OTAV 观察者实现
    └── VisionAnalyzer.kt           # 视觉分析（预留）
```

**AgentAccessibilityService 公开 API**：

```kotlin
// 全局单例
AgentAccessibilityService.instance

// UI 树操作
fun getRoot(): AccessibilityNodeInfo?
fun findElementByText(text: String, exact: Boolean = false): AccessibilityNodeInfo?
fun getAllVisibleTexts(): List<String>

// 手势操作
fun performClick(node: AccessibilityNodeInfo): Boolean
suspend fun performClickAt(x: Float, y: Float): Boolean
suspend fun performSwipe(startX, startY, endX, endY, duration = 300L): Boolean

// App 操作
fun launchApp(packageName: String): Boolean
fun getForegroundPackage(): String
fun setText(node: AccessibilityNodeInfo, text: String): Boolean
```

### 3.4 ui — 界面层

```
ui/
├── theme/
│   ├── Color.kt    # Slate + Indigo 色系
│   └── Theme.kt    # Material 3 暗色主题
├── navigation/
│   ├── Screen.kt   # 路由定义
│   └── NavGraph.kt # 导航图
├── component/
│   ├── MessageBubble.kt    # 聊天气泡组件
│   └── VoiceInputButton.kt # 语音输入按钮
└── screen/
    ├── chat/
    │   ├── ChatScreen.kt      # 主聊天界面
    │   └── ChatViewModel.kt   # **核心** 聊天+任务路由逻辑
    ├── progress/
    │   ├── TaskProgressScreen.kt   # 任务执行界面
    │   └── TaskProgressViewModel.kt
    ├── history/
    │   ├── HistoryScreen.kt
    │   └── HistoryViewModel.kt
    ├── settings/
    │   ├── SettingsScreen.kt
    │   └── SettingsViewModel.kt
    └── appmanage/
        ├── AppManageScreen.kt   # App 能力目录管理
        └── AppManageViewModel.kt
```

### 3.5 app — 入口与 DI

```
app/
├── AgentApplication.kt    # @HiltAndroidApp
├── MainActivity.kt        # @AndroidEntryPoint + CLI 测试入口
├── TestReceiver.kt        # ADB 广播接收器
└── di/AppModule.kt        # 集中 DI：Executor、UseCases、SharedPreferences
```

**MainActivity CLI 测试**（独立于 UI 的测试路径）：

```kotlin
// adb 命令触发：
// am start -n com.example.aimobileagent/.MainActivity --es cmd "打开设置" --es api_key "sk-xxx"

private fun handleIntent(intent: Intent?) {
    val cmd = intent?.getStringExtra("cmd") ?: return
    intent.getStringExtra("api_key")?.let { key ->
        // 写入 SharedPreferences
    }
    // 调用 llmRepository.planTask() → 打印日志
}
```

注意：CLI 路径使用 `llmRepository.planTask()`（非流式），**不经过 ChatViewModel**。CLI 只测试 LLM 规划，不测试执行。

---

## 4. 数据流

### 4.1 聊天模式 (Chat)

```
用户输入 "你好"
    │
    ▼
ChatViewModel.sendCommand()
    │
    ▼
StreamingLLMClient.streamChat(message, history, registeredApps)
    │  POST https://api.deepseek.com/v1/chat/completions  (stream: true)
    │  系统提示词含 App 列表 "应用名(包名)"
    ▼
SSE 事件流
    ├── Chunk(delta) → 更新 MessageBubble（50ms 节流）
    └── Done(ParsedResponse) → 检查 mode
                                  │
                    ┌─────────────┴─────────────┐
                    │ mode == "chat"            │ mode == "task"
                    ▼                           ▼
              显示为聊天气泡              创建 Task 并展示 TaskPlanCard
```

### 4.2 任务模式 (Task)

```
用户输入 "打开火影忍者"
    │
    ▼
ChatViewModel.sendCommand()
    │
    ▼
StreamingLLMClient.streamChat(..., ["火影忍者(com.tencent.KiHan)", ...])
    │  LLM 返回 JSON: {"mode":"task","intent":"...","steps":[...]}
    ▼
StreamEvent.Done → ParsedResponse(mode="task", stepsJson="[...]")
    │
    ▼
ChatViewModel.handleStreamEvent()
    │  ① org.json 解析 steps
    │  ② 创建 domain Task + domain Steps
    │  ③ taskRepository.saveTask(task)
    │  ④ 设置 ChatUiState.currentTask = task
    ▼
ChatScreen 渲染 TaskPlanCard
    │  "📋 已生成任务计划：打开火影忍者\n共 1 步，请在下方确认执行"
    │  [确认执行] 按钮
    ▼
用户点击 [确认执行]
    │
    ▼
导航到 TaskProgressScreen(taskId)
    │
    ▼
TaskProgressViewModel.loadTask(taskId) → 观察 Room 数据
    │
    ▼
用户点击 [开始执行]
    │
    ▼
TaskProgressViewModel.executeTask(taskId)
    │
    ▼
ExecuteTaskUseCase(taskId, stepExecutor)
    │
    │  OTAV 循环 for each step:
    │  ┌──────────────────────────────┐
    │  │ OBSERVE  → screenObserver.observe()     │
    │  │ THINK    → safetyChecker.checkSafety()  │
    │  │ ACT      → stepExecutor.execute(step)   │
    │  │ VERIFY   → screenObserver.verify(step)  │
    │  │ 失败     → retry (最多2次) + recover     │
    │  └──────────────────────────────┘
    │
    ▼
StepExecutorFactory → 按 actionType 分发
    │
    ├── "open_app"  → OpenAppExecutor
    │     └── AgentAccessibilityService.instance.launchApp(packageName)
    ├── "tap"       → TapElementExecutor
    │     └── findElementByText(target) → performClick(node)
    ├── "type"      → TypeTextExecutor
    ├── "swipe"     → SwipeExecutor
    ├── "search"    → SearchExecutor
    └── "share_to"  → ShareToExecutor
```

---

## 5. 数据库设计

### TaskEntity

| 字段 | 类型 | 说明 |
|------|------|------|
| id | TEXT PK | UUID |
| userCommand | TEXT | 用户原始命令 |
| llmRawResponse | TEXT | LLM 原始 JSON |
| intent | TEXT | 意图描述 |
| confidence | REAL | 置信度 0.0-1.0 |
| status | TEXT | PLANNING/READY/RUNNING/COMPLETED/FAILED |
| appsInvolved | TEXT | 涉及的 App 列表（逗号分隔） |
| createdAt | INTEGER | 创建时间 |
| completedAt | INTEGER? | 完成时间 |

### StepEntity

| 字段 | 类型 | 说明 |
|------|------|------|
| id | TEXT PK | UUID |
| taskId | TEXT FK | 关联 Task |
| orderIndex | INTEGER | 步骤序号 |
| actionType | TEXT | open_app/tap/type/swipe/search/share_to |
| targetApp | TEXT? | 目标 App 包名 |
| targetElement | TEXT? | 目标 UI 元素 |
| params | TEXT | JSON 参数字符串 |
| status | TEXT | PENDING/RUNNING/SUCCESS/FAILED/SKIPPED |
| errorMessage | TEXT? | 错误信息 |
| executionDurationMs | INTEGER? | 执行耗时 |

### AppCapabilityEntity

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PK | 自增 |
| packageName | TEXT | App 包名 |
| appName | TEXT | App 名称 |
| supportedActions | TEXT | 支持的操作 JSON |
| resourceIdMap | TEXT | 资源 ID 映射 JSON |

### 关键 SQL 操作

```kotlin
// TaskDao
@Query("SELECT * FROM tasks ORDER BY createdAt DESC")
fun observeAll(): Flow<List<TaskEntity>>

@Query("SELECT * FROM tasks WHERE id = :taskId")
fun observeTask(taskId: String): Flow<TaskEntity?>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertTask(task: TaskEntity)

// TaskRepositoryImpl.saveTask() 自动处理：
// - steps 的 taskId 为空时自动修正
// - 先删旧步骤再插入新步骤
// - 使用 @Transaction 保证原子性
```

---

## 6. 依赖注入 (Hilt)

### 配置位置

| 模块 | 配置 | 说明 |
|------|------|------|
| app | `AppModule.kt` | UseCases, Executors, SharedPreferences |
| data | `DataModule.kt` | Room, Retrofit, OkHttp, Repository 绑定 |
| execution | `ExecutionModule.kt` | ScreenParser 等（当前大部分已移到 AppModule） |

**重要**：执行器 DI 原来分散在各模块，因 KSP 多模块冲突**全部集中到 AppModule** 用 `@Provides` 显式绑定。

### 关键绑定链

```
SharedPreferences → StreamingLLMClient → ChatViewModel
Room DB → TaskDao → TaskRepositoryImpl → TaskRepository → ProcessCommandUseCase
OkHttpClient → StreamingLLMClient（流式，直接注入）
Retrofit → DeepSeekApiService → LLMRepositoryImpl → LLMRepository
OpenAppExecutor → StepExecutorFactory → StepExecutor → ExecuteTaskUseCase
```

---

## 7. LLM 集成

### 7.1 支持的模型

```kotlin
val AVAILABLE_MODELS = listOf(
    ModelInfo("deepseek-chat", "https://api.deepseek.com", "DeepSeek"),
    ModelInfo("deepseek-reasoner", "https://api.deepseek.com", "DeepSeek"),
    ModelInfo("gpt-4o-mini", "https://api.openai.com", "OpenAI"),
    ModelInfo("gpt-4o", "https://api.openai.com", "OpenAI"),
    ModelInfo("claude-3.5-haiku", "https://api.anthropic.com", "Anthropic"),
    ModelInfo("claude-sonnet-4-6", "https://api.anthropic.com", "Anthropic"),
    ModelInfo("gemini-2.0-flash", "https://generativelanguage.googleapis.com", "Google"),
)
```

### 7.2 API Key 管理

- 存储：`SharedPreferences`（`agent_prefs`）
- Key 命名规则：`api_key_<modelName>`（每个模型独立 Key）
- 切换模型时自动切换对应 Key
- 注意：**不能用 EncryptedSharedPreferences**（Mumu 模拟器 SIGBUS 崩溃）

### 7.3 请求格式

所有模型使用 OpenAI 兼容的 `/v1/chat/completions` 端点：

```json
{
  "model": "deepseek-chat",
  "messages": [
    {"role": "system", "content": "你是智能手机助手..."},
    {"role": "user", "content": "打开火影忍者"}
  ],
  "stream": true
}
```

**注意**：不能使用 `response_format: json_object`（DeepSeek 不兼容，会导致 HTTP 400）。

### 7.4 流式 SSE 解析

```
data: {"choices":[{"delta":{"content":"你好"}}]}
data: {"choices":[{"delta":{"content":"！"}}]}
data: [DONE]
```

- 用 OkHttp 直接读 `BufferedReader`，逐行解析 `data:` 行
- 用 Regex 提取 `"content"` 字段（org.json 在此处开销太大）
- 50ms 节流防止 callbackFlow 缓冲区溢出
- 使用 `send()` 而非 `trySend()`（前者在缓冲区满时挂起等待）

### 7.5 系统提示词

```
你是智能手机助手，能聊天也能帮执行手机任务。用中文回复，支持Markdown。

可操作的手机App（格式: 应用名(包名)）:
火影忍者(com.tencent.KiHan), 微信(com.tencent.mm), 设置(com.android.settings), ...

当用户想操作手机时，用JSON回复:
{"mode":"task","intent":"描述","steps":[{"order":1,"action":"open_app","target":"包名","params":{}}]}。
target字段填括号里的包名。其他时候正常聊天。
```

---

## 8. 执行引擎

### 8.1 OTAV Agent 循环

```
┌──────────────────────────────────────────────┐
│  OBSERVE → 解析当前屏幕状态                     │
│     ↓                                         │
│  THINK   → 安全检查 + 前置条件验证 + 策略调整      │
│     ↓                                         │
│  ACT     → 执行步骤动作                          │
│     ↓                                         │
│  VERIFY  → 验证动作效果 + 记录结果                │
│     ↓                                         │
│  成功 → 下一步 | 失败 → 恢复(最多2次) | 危险 → 立即停止 │
└──────────────────────────────────────────────┘
```

### 8.2 StepExecutor 实现

| Executor | actionType | 核心逻辑 |
|----------|------------|----------|
| OpenAppExecutor | `open_app` | `pm.getLaunchIntentForPackage(pkg)` → `startActivity` |
| TapElementExecutor | `tap` | `findElementByText(target)` → `performClick()` |
| TypeTextExecutor | `type` | `findElementByText` → `performAction(ACTION_SET_TEXT)` |
| SwipeExecutor | `swipe` | `dispatchGesture(GestureDescription)` |
| SearchExecutor | `search` | 找到搜索框 → `setText` → 按回车 |
| ShareToExecutor | `share_to` | 找到分享按钮 → 选择目标 App |

### 8.3 安全机制

```kotlin
SafetyChecker.checkSafety():
  - 检测支付页面 → SafetyCheckResult.Danger
  - 检测密码输入 → SafetyCheckResult.Warning
  - 其余 → SafetyCheckResult.Safe
```

危险页面：立即停止执行，跳过剩余步骤。

---

## 9. UI 层

### 9.1 路由定义

```kotlin
sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object TaskProgress : Screen("task_progress/{taskId}") {
        fun createRoute(taskId: String) = "task_progress/$taskId"
    }
    object History : Screen("history")
    object Settings : Screen("settings")
    object AppManage : Screen("app_manage")
}
```

### 9.2 主题

暗色主题（Material 3 Dark Color Scheme）：
- `primary`：Indigo (#6366F1)
- `background`：Slate (#0F172A)
- `surface`：Slate (#1E293B)
- 聊天气泡：用户 Indigo / AI Slate

---

## 10. 如何添加新功能

### 10.1 添加新的 LLM 模型

1. 在 `ChatViewModel.kt` 的 `AVAILABLE_MODELS` 列表中添加 `ModelInfo`
2. 确保新模型的 API 端点兼容 OpenAI `/v1/chat/completions` 格式
3. 如果不兼容，在 `StreamingLLMClient.streamChat()` 中添加端点适配逻辑

### 10.2 添加新的 Step Executor

1. 在 `execution/executor/` 下创建新 Executor 类
2. 实现 `StepExecutor` 接口（`execute` + `recover`）
3. 定义一个 `companion object { const val ACTION = "xxx" }` 作为 actionType 常量
4. 在 `StepExecutorFactory.getExecutor()` 的 `when` 分支中添加映射
5. 在 `AppModule.kt` 中添加 `@Provides @Singleton` 绑定
6. 在 `StepExecutorFactory` 构造函数中注入新 Executor

示例（添加一个截图 Executor）：

```kotlin
// 1. 创建文件
@Singleton
class ScreenshotExecutor @Inject constructor() : StepExecutor {
    companion object { const val ACTION = "screenshot" }
    override suspend fun execute(step: Step): StepResult { ... }
    override suspend fun recover(step: Step, error: String): StepResult { ... }
}

// 2. 注册到工厂
// StepExecutorFactory.kt
private fun getExecutor(action: String): StepExecutor? = when (action) {
    ...
    ScreenshotExecutor.ACTION -> screenshot
    else -> null
}

// 3. AppModule.kt
@Provides @Singleton
fun provideScreenshotExecutor(): ScreenshotExecutor = ScreenshotExecutor()
```

### 10.3 添加新页面

1. 在 `ui/screen/` 下创建 `NewScreen.kt` 和 `NewViewModel.kt`
2. 在 `Screen.kt` 添加路由
3. 在 `NavGraph.kt` 添加 `composable()` 注册
4. ViewModel 用 `@HiltViewModel` + `@Inject constructor`

### 10.4 修改系统提示词

- 位置：`StreamingLLMClient.streamChat()` 中的 `appListText` 和 `sysPrompt`
- 注意：提示词变更会导致 LLM 输出格式变化，需要同步检查 `LLMResponseParser`

---

## 11. 已知问题与注意事项

### 11.1 GitHub 仓库配置

```bash
git config --global http.proxy http://127.0.0.1:7897  # 需要代理
```

### 11.2 Mumu 模拟器兼容性

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| EncryptedSharedPreferences SIGBUS | Tink native lib x86+ARM 不兼容 | 使用普通 SharedPreferences |
| AccessibilityService 不绑定 | `settings put secure` 不完全生效 | 用 `content insert --uri content://settings/secure` |
| 火影忍者等第三方 App | 模拟器需单独安装 APK | 通过 Mumu 应用商店或拖拽安装 |

### 11.3 构建环境

```bash
# 必须使用 JDK 21（Android Studio 自带）
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug
```

### 11.4 JSON 序列化

- **流式客户端**使用 `org.json`（轻量、不依赖 kotlinx.serialization）
- **Retrofit 路径**使用 `kotlinx.serialization`
- 手动字符串拼接 JSON **已废弃**（控制字符转义问题），统一用 `org.json.JSONObject`

### 11.5 代码规范

- 所有文件使用 Unix 换行 (LF)
- 缩进：4 空格
- 语言：注释用中文，标识符用英文
- Android 日志 Tag：`ChatVM`, `LLMParser`, `MainActivity`

---

## 附录：关键文件索引

| 想了解... | 看这个文件 |
|-----------|-----------|
| 聊天+任务路由逻辑 | `ui/.../chat/ChatViewModel.kt` |
| SSE 流式请求 | `data/.../remote/StreamingLLMClient.kt` |
| JSON 解析容错 | `data/.../remote/LLMResponseParser.kt` |
| 任务执行引擎 | `domain/.../usecase/ExecuteTaskUseCase.kt` |
| 无障碍服务 | `execution/.../AgentAccessibilityService.kt` |
| 步骤分发 | `execution/.../StepExecutorFactory.kt` |
| Room 数据库 | `data/.../local/AppDatabase.kt` |
| Hilt DI 配置 | `app/.../di/AppModule.kt` + `data/.../local/DataModule.kt` |
| 导航路由 | `ui/.../navigation/NavGraph.kt` |
| 系统提示词 | `data/.../remote/StreamingLLMClient.kt` (streamChat 方法) |
| 领域模型 | `domain/.../model/` |
