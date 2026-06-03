package com.example.aimobileagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.aimobileagent.ui.navigation.NavGraph
import com.example.aimobileagent.ui.theme.AIMobileAgentTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 单 Activity 入口。
 * 所有界面通过 Jetpack Compose Navigation 管理。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIMobileAgentTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}
