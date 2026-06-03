package com.example.aimobileagent.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.aimobileagent.ui.screen.appmanage.AppManageScreen
import com.example.aimobileagent.ui.screen.chat.ChatScreen
import com.example.aimobileagent.ui.screen.history.HistoryScreen
import com.example.aimobileagent.ui.screen.progress.TaskProgressScreen
import com.example.aimobileagent.ui.screen.settings.SettingsScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route
    ) {
        composable(Screen.Chat.route) {
            ChatScreen(
                onNavigateToProgress = { taskId ->
                    navController.navigate(Screen.TaskProgress.createRoute(taskId))
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.TaskProgress.route,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType })
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId") ?: return@composable
            TaskProgressScreen(
                taskId = taskId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onTaskClick = { taskId ->
                    navController.navigate(Screen.TaskProgress.createRoute(taskId))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToAppManage = {
                    navController.navigate(Screen.AppManage.route)
                }
            )
        }

        composable(Screen.AppManage.route) {
            AppManageScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
