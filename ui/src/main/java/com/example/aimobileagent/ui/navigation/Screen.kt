package com.example.aimobileagent.ui.navigation

/**
 * 导航路由定义。
 */
sealed class Screen(val route: String) {
    data object Chat : Screen("chat")
    data object TaskProgress : Screen("task_progress/{taskId}") {
        fun createRoute(taskId: String) = "task_progress/$taskId"
    }
    data object History : Screen("history")
    data object AppManage : Screen("app_manage")
    data object Settings : Screen("settings")
}
