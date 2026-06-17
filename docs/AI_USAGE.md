# AI 工具使用说明

> 北京理工大学《Android 技术开发基础》结课设计要求：在项目文档中说明 AI 工具使用情况。

---

## 一、使用的 AI 工具

| 工具 | 用途 | 使用阶段 |
|------|------|----------|
| **Claude Code** (Anthropic) | 架构设计辅助、代码生成、调试修复、文档编写 | 全流程 |
| **DeepSeek Chat** (深度求索) | App 内置 LLM 服务 — 理解用户命令、生成任务计划 | App 运行时代码 |
| **GitHub Copilot** (可选) | IDE 内代码补全 | 编码阶段 |

---

## 二、人机分工原则

本项目采用 **"人为主，AI 为辅"** 的开发方式：

| 工作 | 人 | AI |
|------|:--:|:--:|
| 选题、功能定位 | ✅ | — |
| 架构设计（模块划分、依赖关系） | ✅ | 辅助建议 |
| 技术选型（Room/Hilt/Compose） | ✅ | 辅助建议 |
| 接口/数据模型设计 | ✅ | — |
| 代码实现 | 审核 | ✅ 生成 |
| 调试修复 | 排查根因 | ✅ 辅助修复 |
| 测试验证 | ✅ 手动测试 | — |
| 文档编写 | 审核修正 | ✅ 生成草稿 |
| Git 提交管理 | ✅ | — |

---

## 三、AI 生成代码的审核流程

1. AI 生成代码后，人工逐文件 Review
2. 检查 Clean Architecture 层次依赖是否正确
3. 验证类型安全、空安全、异常处理
4. 编译验证 + 模拟器/真机测试
5. 测试通过后方可提交

---

## 四、DeepSeek API 集成说明

App 核心功能依赖 DeepSeek API（OpenAI 兼容 `/v1/chat/completions`）：

- **调用方式**：App 直连 API，不经过中间后端
- **API Key 存储**：SharedPreferences（`agent_prefs`）
- **请求内容**：系统提示词 + 对话历史 + App 能力目录
- **响应格式**：SSE 流式 → `{"mode":"chat","reply":"..."}` 或 `{"mode":"task","intent":"...","steps":[...]}`
- **备用模型**：OpenAI GPT-4o / Anthropic Claude / Google Gemini

详见 [ARCHITECTURE.md §7](ARCHITECTURE.md#7-llm-集成)

---

## 五、AI 使用反思

### 优势
- 加速开发：60+ Kotlin 文件在 2 周内完成
- 降低门槛：AI 辅助 Debug 和异常处理
- 知识补充：提供 Clean Architecture、Hilt、AccessibilityService 等最佳实践参考

### 不足
- AI 生成的 Kotlin 代码偶有语法错误（如不存在的 Compose Icon API）
- JSON 处理需人工把关（转义、容错回退策略）
- 架构理解需人工验证（AI 可能违反 Clean Architecture 依赖方向）

### 总结
AI 工具在本项目中作为**高效协作者**而非替代者。核心架构决策由人工做出，AI 主要负责重复性代码生成和调试辅助，所有 AI 产出均经过人工审核。
