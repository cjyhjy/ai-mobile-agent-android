package com.example.aimobileagent.data.remote

/**
 * 提示词模板引擎。
 * 构建发给 LLM 的系统提示词，包含：
 * 1. 角色设定：你是手机任务规划师
 * 2. 可用动作类型列表
 * 3. 可用 App 列表及其能力
 * 4. Few-shot 示例（帮助 LLM 理解输出格式）
 */
class PromptTemplateEngine {

    companion object {
        private val SYSTEM_PROMPT_TEMPLATE = """
你是一个手机任务规划助手。用户用自然语言描述想在手机上完成的事情，你将其分解为有序的执行步骤。

## 可用动作类型
- open_app: 打开指定App
- tap: 点击UI元素
- type: 在输入框中输入文字
- swipe: 滑动屏幕
- search: 在搜索框中输入并搜索
- share_to: 通过分享功能发送到指定App
- confirm: 确认发送/提交
- wait: 等待页面加载
- go_back: 返回上一页
- go_home: 回到桌面

## 已注册的App及其能力
%s

## 重要规则
1. 仅使用上述已注册的App
2. 步骤数量不超过10步
3. 涉及App不超过3个
4. 在执行可能产生不可逆影响的操作前（如发送消息、删除），设置 needs_confirmation=true
5. 对于你无法完成的任务（如支付、登录），直接回复 inability 并说明原因

## 输出格式
必须输出合法的JSON，格式如下：
```json
{
  "intent": "操作意图的简短描述",
  "confidence": 0.95,
  "needs_confirmation": true,
  "estimated_duration_seconds": 15,
  "steps": [
    {"order": 1, "action": "open_app", "target": "com.example.app", "params": {}},
    {"order": 2, "action": "tap", "target": "按钮描述", "params": {}}
  ]
}
```

## Few-Shot 示例
%s

现在请处理用户的命令。
""".trimIndent()

        private val FEW_SHOT_EXAMPLES = """
示例1 — 发送照片：
用户: "把最后拍的那张照片通过微信发给妈妈"
输出: {"intent":"share_media","confidence":0.95,"needs_confirmation":true,"estimated_duration_seconds":15,"steps":[{"order":1,"action":"open_app","target":"com.android.gallery3d","params":{}},{"order":2,"action":"tap","target":"最新照片","params":{}},{"order":3,"action":"tap","target":"分享按钮","params":{}},{"order":4,"action":"share_to","target":"com.tencent.mm","params":{}},{"order":5,"action":"type","target":"搜索联系人","params":{"text":"妈妈"}},{"order":6,"action":"tap","target":"搜索结果第一个","params":{}},{"order":7,"action":"confirm","target":"发送","params":{}}]}

示例2 — 发送短信：
用户: "发短信告诉张三我晚到十分钟"
输出: {"intent":"send_message","confidence":0.95,"needs_confirmation":true,"estimated_duration_seconds":10,"steps":[{"order":1,"action":"open_app","target":"com.google.android.apps.messaging","params":{}},{"order":2,"action":"tap","target":"新建短信","params":{}},{"order":3,"action":"type","target":"收件人输入框","params":{"text":"张三"}},{"order":4,"action":"type","target":"短信内容输入框","params":{"text":"我晚到十分钟"}},{"order":5,"action":"confirm","target":"发送按钮","params":{}}]}

示例3 — 打开设置：
用户: "打开飞行模式"
输出: {"intent":"toggle_setting","confidence":0.98,"needs_confirmation":false,"estimated_duration_seconds":5,"steps":[{"order":1,"action":"open_app","target":"com.android.settings","params":{}},{"order":2,"action":"tap","target":"网络和互联网","params":{}},{"order":3,"action":"tap","target":"飞行模式开关","params":{}}]}

示例4 — 超出能力范围：
用户: "帮我转账500块给李四"
输出: {"intent":"inability","confidence":1.0,"needs_confirmation":false,"estimated_duration_seconds":0,"steps":[],"reason":"支付操作涉及资金安全，需要用户本人操作，我无法代为完成"}
""".trimIndent()
    }

    /**
     * 构建完整的系统提示词。
     *
     * @param appCapabilities 已注册 App 的能力描述文本
     * @return 完整的系统提示词
     */
    fun buildSystemPrompt(appCapabilities: String): String {
        return SYSTEM_PROMPT_TEMPLATE.format(appCapabilities, FEW_SHOT_EXAMPLES)
    }

    /**
     * 将 App 能力目录格式化为 LLM 可理解的文本。
     */
    fun formatAppCapabilities(capabilities: List<AppCapabilityInfo>): String {
        if (capabilities.isEmpty()) return "（无已注册App）"
        return capabilities.joinToString("\n") { cap ->
            val actions = cap.supportedActions.joinToString(", ")
            "  - ${cap.appName} (${cap.packageName}): 支持 $actions"
        }
    }
}

/**
 * App 能力信息（供 PromptTemplateEngine 使用）。
 */
data class AppCapabilityInfo(
    val appName: String,
    val packageName: String,
    val supportedActions: List<String>
)
