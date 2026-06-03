package com.example.aimobileagent.domain.model

/**
 * 任务状态的枚举。
 * PLANNING  - LLM 正在生成任务计划
 * READY     - 计划已生成，等待用户确认
 * RUNNING   - 正在执行
 * COMPLETED - 所有步骤执行成功
 * FAILED    - 执行失败（某个步骤无法恢复）
 */
enum class TaskStatus {
    PLANNING,
    READY,
    RUNNING,
    COMPLETED,
    FAILED
}
