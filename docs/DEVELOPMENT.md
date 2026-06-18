# AI Mobile Agent — 开发指南

> **适用读者**：接手本项目的开发者 / AI 助手  
> **前置阅读**：先读 [ARCHITECTURE.md](ARCHITECTURE.md) 了解整体架构  
> **代码版本**：v2.4.0

---

## 1. 环境搭建

### 1.1 必需工具

| 工具 | 版本 | 用途 |
|------|------|------|
| JDK | 21 (Android Studio 自带) | 编译 |
| Android Studio | 2024.1+ (Hedgehog) | IDE |
| Gradle | 8.11.1 (wrapper 自带) | 构建 |
| Android SDK | API 35 | 编译目标 |
| Git | 2.x | 版本控制 |
| ADB | platform-tools | 安装/调试 |

### 1.2 克隆并构建

```bash
git clone https://github.com/cjyhjy/ai-mobile-agent-android.git
cd ai-mobile-agent-android

# 构建（Windows 下用 gradlew.bat）
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat assembleDebug

# APK 位置
# app/build/outputs/apk/debug/app-debug.apk
```

### 1.3 网络配置

通过代理访问 GitHub：
```bash
git config --global http.proxy http://127.0.0.1:7897
```

Android Studio 代理：Settings → Appearance → System Settings → HTTP Proxy

---

## 2. 开发工作流

### 2.1 修改代码 → 构建 → 安装

```bash
# 1. 修改代码后构建
gradlew.bat assembleDebug

# 2. 安装到模拟器
adb -s 127.0.0.1:5555 install -r app/build/outputs/apk/debug/app-debug.apk

# 3. 强制重启 App
adb -s 127.0.0.1:5555 shell am force-stop com.example.aimobileagent

# 4. 查看日志
adb -s 127.0.0.1:5555 logcat -s ChatVM:* MainActivity:* LLMParser:*
```

### 2.2 CLI 快速测试（不需要操作 UI）

```bash
# 聊天测试
adb shell am start -n com.example.aimobileagent/.MainActivity --es cmd "你好"

# 任务测试
adb shell am start -n com.example.aimobileagent/.MainActivity --es cmd "打开设置"

# 查看结果
adb logcat -d -s MainActivity:* | tail -10
```

**注意**：CLI 路径走 `llmRepository.planTask()`（非流式），不经过 ChatViewModel。用于快速验证 LLM 规划，不能测试 UI 和执行。

### 2.3 启用无障碍服务（Mumu 模拟器）

```bash
# 列出设备
adb devices

# Mumu 模拟器启用
adb -s 127.0.0.1:5555 shell content insert --uri content://settings/secure --bind name:s:enabled_accessibility_services --bind value:s:"com.example.aimobileagent/com.example.aimobileagent.execution.AgentAccessibilityService"
adb -s 127.0.0.1:5555 shell settings put secure accessibility_enabled 1

# 验证
adb -s 127.0.0.1:5555 shell dumpsys accessibility | grep "Bound services"
```

---

## 3. 代码导航

### 3.1 从用户输入追踪到执行完成

```
1. ChatScreen.kt: InputBar → onSend → viewModel.sendCommand()
2. ChatViewModel.kt: sendCommand() → streamingClient.streamChat()
3. StreamingLLMClient.kt: streamChat() → SSE 请求 → parseResponse()
4. ChatViewModel.kt: handleStreamEvent(StreamEvent.Done)
   - mode == "chat" → 显示聊天气泡
   - mode == "task" → 创建 Task → show TaskPlanCard
5. TaskPlanCard [确认执行] → NavGraph → TaskProgressScreen(taskId)
6. TaskProgressScreen: [开始执行] → TaskProgressViewModel.executeTask()
7. ExecuteTaskUseCase.kt: OTAV 循环 → stepExecutor.execute(step)
8. StepExecutorFactory.kt: 按 actionType 分发 → OpenAppExecutor 等
9. AgentAccessibilityService.kt: launchApp() / performClick() 等
```

### 3.2 快速定位

| 需求 | 文件 |
|------|------|
| 改聊天 UI | `ui/.../chat/ChatScreen.kt` |
| 改聊天逻辑 | `ui/.../chat/ChatViewModel.kt` |
| 改提示词 | `data/.../remote/StreamingLLMClient.kt` → `streamChat()` 方法 |
| 改 JSON 解析 | `data/.../remote/LLMResponseParser.kt` |
| 改任务执行 | `domain/.../usecase/ExecuteTaskUseCase.kt` |
| 加新的执行动作 | `execution/executor/` → `StepExecutorFactory.kt` → `AppModule.kt` |
| 加新页面 | `ui/screen/` → `Screen.kt` → `NavGraph.kt` |
| 改数据库表 | `data/.../local/entity/` → `AppDatabase.kt`（version++） |
| 添加 LLM 模型 | `ChatViewModel.kt` → `AVAILABLE_MODELS` |

---

## 4. 调试技巧

### 4.1 日志标签

```kotlin
// 主要标签
android.util.Log.e("ChatVM", "...")      // 聊天/任务 ViewModel
android.util.Log.e("LLMParser", "...")   // JSON 解析
android.util.Log.e("MainActivity", "...") // CLI 入口
```

### 4.2 HTTP 请求调试

OkHttp 已配置 `HttpLoggingInterceptor(Level.BASIC)`，日志中可以看到请求 URL 和响应状态码。

### 4.3 常见调试场景

**LLM 返回了 JSON 但被当聊天显示**：
```
→ 检查 ChatViewModel.handleStreamEvent() 中 StreamEvent.Done 分支
→ 检查 parseResponse() 是否返回 mode == "task"
→ 检查 stepsJson 是否非空
```

**task JSON 解析失败**：
```
→ 查看 logcat LLMParser:* 
→ 检查 LLMResponseParser.extractJson() 的回退链
→ 可能是 LLM 输出格式变化，调整提示词
```

**无障碍服务执行失败**：
```
→ 检查 Bound services 是否为空
→ 检查 AgentAccessibilityService.instance 是否为 null
→ 如果是 Mumu，用 content insert 而非 settings put
```

---

## 5. 构建配置要点

### 5.1 Gradle 模块依赖

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":execution"))
    implementation(project(":ui"))
}

// data/build.gradle.kts
dependencies {
    implementation(project(":domain"))  // 依赖 domain 接口
}

// execution/build.gradle.kts
dependencies {
    implementation(project(":domain"))  // 依赖 domain 接口
}

// ui/build.gradle.kts
dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
}
```

### 5.2 org.json 可用性

`org.json` 是 Android SDK 内置的，无需额外依赖。在 `StreamingLLMClient.kt` 和 `ChatViewModel.kt` 中直接使用。

### 5.3 Hilt 注意事项

- 所有 Executor 的 `@Provides` 在 `app/di/AppModule.kt` 中
- 不要在各模块自己的 Module 中重复绑定（会导致 KSP 冲突）
- `@HiltViewModel` 的 ViewModel 自动获得注入能力

---

## 6. 平台差异

### 6.1 Android 真机 vs 模拟器

| 特性 | 真机 | Mumu 模拟器 |
|------|------|-------------|
| EncryptedSharedPreferences | ✅ | ❌ (SIGBUS) |
| AccessibilityService | 手动开启 | `content insert` 开启 |
| 第三方 App | 正常安装 | 需 APK 拖拽安装 |
| API 网络 | 正常 | 正常 |

### 6.2 HarmonyOS 移植

HarmonyOS 版本在 `D:\ai-mobile-agent-harmonyos\`，架构镜像 Android 版本。
- Android `AccessibilityService` → HarmonyOS `AccessibilityAbility`
- Android `Room` → HarmonyOS `RelationalStore`
- Android `Retrofit` → HarmonyOS `@ohos.net.http`
- Android `org.json` → HarmonyOS 内置 `JSON` 对象

---

## 7. Git 工作流

```bash
# 查看版本历史
git log --oneline -10

# 创建新功能分支
git checkout -b feature/xxx

# 提交
git add -A
git commit -m "vX.Y: 简短描述"
git push origin feature/xxx

# 合并回 main
git checkout main
git merge feature/xxx
git push

# 推送前确认作者
git log --format="%an <%ae>" -1  # 应为 cjy <621804911@qq.com>
```

---

## 8. 常见问题 FAQ

**Q: 为什么有两个 LLM 调用路径？**  
A: `StreamingLLMClient.streamChat()`（流式，用于 Chat UI）和 `LLMRepositoryImpl.planTask()`（非流式，用于 CLI 测试）。前者是主力，后者是快速验证。两者共用相同的提示词逻辑。

**Q: 为什么 JSON 不用 kotlinx.serialization？**  
A: 流式路径逐 chunk 到达，无法一次反序列化整个对象。用 Regex + org.json 更灵活。非流式路径（DTO）仍用 kotlinx.serialization。

**Q: 新加的执行器需要哪些步骤？**  
A: 详见 [ARCHITECTURE.md §10.2](ARCHITECTURE.md#102-添加新的-step-executor)

**Q: 怎么在模拟器上安装第三方 App？**  
A: 下载 APK → 拖拽到 Mumu 窗口 → 自动安装。或 `adb install xxx.apk`。
