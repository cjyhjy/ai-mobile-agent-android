package com.example.aimobileagent.domain.model

/**
 * 步骤执行状态。
 * PENDING - 等待执行
 * RUNNING - 正在执行
 * SUCCESS - 执行成功
 * FAILED  - 执行失败
 * SKIPPED - 被跳过（前置步骤失败导致）
 */
enum class StepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED
}
