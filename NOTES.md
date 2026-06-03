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

## 项目当前状态

### Android 端
- **代码量**：约 4000+ 行 Kotlin（60+ 文件）
- **APK**：`app/build/outputs/apk/debug/app-debug.apk` (59MB)
- **编译**：✅ BUILD SUCCESSFUL
- **路径**：`D:\ai-mobile-agent-android\`

### HarmonyOS 端
- **代码量**：约 600 行 ArkTS + 配置文件
- **路径**：`D:\ai-mobile-agent-harmonyos\`
- **架构**：镜像 Android 端 Clean Architecture（Models / UseCases / Data / Execution / Pages）

### 5 项 GitHub 参考改进
| # | 改进 | 来源 |
|---|------|------|
| 1 | 安全检测（支付/密码页面自动停止） | Roubao |
| 2 | Agent OTAV 循环 | ApkClaw / Droidrun |
| 3 | 语音输入（SpeechRecognizer） | Panda |
| 4 | 屏幕视觉（截图+多模态 LLM） | Droidrun |
| 5 | 记忆系统（用户偏好） | Panda |

### 编译过程中修复的问题
1. AndroidManifest.xml 缺少 `xmlns:tools` 命名空间
2. Domain 层引用 Data 层 `AppCapabilityEntity`（违反 Clean Architecture）
3. `getBoundsInScreen()` 返回 void 不是 Rect
4. 多模块 KSP/Hilt 处理冲突（将 DI 集中到 app 模块）
5. `Icons.Default` 部分图标 API 不存在
6. Dagger 缺少 UseCase 的 `@Provides` 绑定

### 待完成
- 真机/模拟器部署测试
- 鸿蒙端 DevEco Studio 编译验证
- App 能力目录按真机 resourceId 调整
- API Key 配置 (DeepSeek)

## 遇到的坑

### 坑1: 权限弹窗配置
- 问题：Bash(rm *) 和 Bash(* -rf *) 触发删除确认弹窗
- 解决：保留安全规则，开发过程中不删临时文件，最后统一清理
