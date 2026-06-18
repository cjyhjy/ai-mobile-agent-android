# AI Mobile Agent — 开发记录

## 项目信息
- 选题：AI 手机智能体
- 平台：Android (Kotlin) + HarmonyOS (ArkTS)
- 架构：Clean Architecture (Domain / Data / Execution / UI)
- 起始日期：2026-06-02

## 技术决策记录

### TD-001: 采用 Clean Architecture 多模块
- 日期：2026-06-02
- 决策：Gradle 5 模块拆分 (app / domain / data / execution / ui)
- 理由：课程要求 + 依赖反转原则 + 便于双平台移植
- 参考：课程 PPT "Google标准App架构.pdf"、"Clean Architecture概述"

### TD-002: LLM 直连方案
- 日期：2026-06-02
- 决策：App 直连 DeepSeek API，不架设中间后端
- 理由：简化架构，减少工作量（5周限制），API Key 加密存储即可

### TD-003: Accessibility Service 执行引擎
- 日期：2026-06-02
- 决策：使用 AccessibilityService 遍历 UI 树 + dispatchGesture 模拟操作
- 理由：无需 root，所有 App 通用，是最可行的自动化方案
- 难点：需要处理不同 App 的 UI 元素定位差异

---

## 开发进度

| 日期 | 完成内容 | 备注 |
|------|---------|------|
| 0602 | 项目结构 + Gradle 配置 | 5模块划分，3198行Kotlin |
| 0602 | Domain 层 | 5 Models + 3 UseCases + 2 Repository接口 |
| 0602 | Data 层 | Room(3实体+2DAO+DB) + Retrofit + DTOs + PromptEngine + ResponseParser + 2 RepositoryImpl + Hilt DataModule |
| 0602 | Execution 层 | AccessibilityService + 6执行器 + Factory + ScreenParser + ElementLocator + Hilt ExecutionModule |
| 0602 | UI 层 | 4 Screens + 4 ViewModels + 导航 + 暗色主题 |
| 0602 | App 入口 | AgentApplication + MainActivity + AndroidManifest + 无障碍配置 |

### TD-004: 手动 JSON 参数解析
- 日期：2026-06-02
- 决策：StepEntity 中的 params 字段用简单字符串拼接/解析，不用 kotlinx.serialization
- 理由：避免 Room TypeConverter 的复杂性，params 结构简单（key-value 对）

### TD-005: 暗色主题
- 日期：2026-06-02
- 决策：UI 采用暗色主题（Slate色系 + Indigo主色调）
- 理由：科技感 + AI 产品常见风格，Material 3 暗色支持成熟

## 项目当前状态（v2.4.0，2026-06-17）

### Android 端
- **版本**：v2.4.0
- **代码量**：约 4500+ 行 Kotlin（60+ 文件）
- **APK**：`app/build/outputs/apk/debug/app-debug.apk`
- **编译**：✅ BUILD SUCCESSFUL (JDK 21)
- **路径**：`D:\ai-mobile-agent-android\`
- **已验证**：聊天模式、任务规划、任务执行（打开 App）
- **质量**：GLM + Qwen 双模型独立审查，19 个问题全部修复

### HarmonyOS 端
- **代码量**：约 600 行 ArkTS + 配置文件
- **路径**：`D:\ai-mobile-agent-harmonyos\`
- **架构**：镜像 Android 端 Clean Architecture
- **状态**：基础聊天功能完成，待接入 AccessibilityAbility + 流式 SSE

### 5 项 GitHub 参考改进
| # | 改进 | 来源 |
|---|------|------|
| 1 | 安全检测（支付/密码页面自动停止）| Roubao |
| 2 | Agent OTAV 循环 | ApkClaw / Droidrun |
| 3 | 语音输入（SpeechRecognizer）| Panda |
| 4 | 屏幕视觉（截图+多模态 LLM）| Droidrun |
| 5 | 记忆系统（用户偏好）| Panda |

---

## v2.x 版本记录

### v2.3.0 (06-17) — Task 路由修复
- 修复：LLM 返回 task JSON 时只显示文本，不触发任务执行
- 新增：ChatViewModel 检测 `mode=="task"` 自动创建 Task + TaskPlanCard
- 改进：App 列表格式从"包名"改为"应用名(包名)"，帮助 LLM 匹配
- 修复：无障碍服务在 Mumu 上用 `content insert` 方式绑定

### v2.4.0 (06-17) — 双模型审查全面修复
- GLM 独立审查发现 9 个问题，Qwen 独立审查发现 10 个
- 合并修复 6 个 GLM 独有 + 3 个 Qwen 未修 = 共 19 个问题全部修复
- 关键：动态 endpoint、步骤状态覆盖、extractContent 转义、parseParams、LazyColumn key
- 构建：KSP 2.0.21-1.0.28 + incremental=false 解决 Windows 编译问题

### v2.2.2 (06-16) — JSON 转义修复
- 修复：手动 String.replace() 遗漏 `\r` 导致 API 400
- 迁移：StreamingLLMClient 用 `org.json.JSONObject` 构建请求体
- 新增：`ParsedResponse.stepsJson` 字段

### v2.2.1 (06-15) — App 列表注入
- 新增：流式提示词包含已注册 App 列表
- 修复：CLI 路径 taskId 自动修正

### v2.2 (06-15) — 7 模型 + API Key
- 新增：7 个模型（DeepSeek×2, OpenAI×2, Anthropic×2, Google×1）
- 新增：每个模型独立 API Key 存储
- 新增：文件上传功能

### v2.1 (06-10) — 流式修复
- 修复：callbackFlow 默认缓冲区溢出导致流式显示卡住
- 新增：50ms 节流 + send() 替代 trySend()
- 新增：中止对话按钮
- 新增：模型选择器 Bottom Sheet

### v2.0 (06-08) — 流式响应
- 新增：OkHttp SSE 流式读取
- 新增：多轮对话上下文（conversationHistory）
- 修复：JSON 提取 4 级回退策略

### v1.x (06-02~07) — 项目搭建
- 5 模块 Clean Architecture 搭建
- Room 数据库（Task/Step/AppCapability）
- Retrofit + DeepSeek API 集成
- AccessibilityService + 6 执行器
- OTAV Agent 循环
- Chat/TaskProgress/History/Settings 页面
- JSON 解析容错

---

### 编译过程中修复的问题
1. AndroidManifest.xml 缺少 `xmlns:tools` 命名空间
2. Domain 层引用 Data 层 `AppCapabilityEntity`（违反 Clean Architecture）
3. `getBoundsInScreen()` 返回 void 不是 Rect
4. 多模块 KSP/Hilt 处理冲突（将 DI 集中到 app 模块）
5. `Icons.Default` 部分图标 API 不存在
6. Dagger 缺少 UseCase 的 `@Provides` 绑定
7. EncryptedSharedPreferences SIGBUS → 普通 SharedPreferences
8. `response_format: json_object` → null（DeepSeek 不兼容）
9. 手动字符串 JSON 拼接有无控制字符转义问题 → org.json

### 待完成
- [ ] 多步骤复杂任务（跨 App 协同，如"发微信给xxx"）
- [ ] 鸿蒙版本功能同步（流式 SSE + AccessibilityAbility）
- [ ] 真机测试（无障碍服务 + 第三方 App 兼容）
- [ ] 截图视觉分析

## 遇到的坑

### 坑1: 权限弹窗配置
- 问题：Bash(rm *) 和 Bash(* -rf *) 触发删除确认弹窗
- 解决：保留安全规则，开发过程中不删临时文件，最后统一清理
