package com.example.aimobileagent.ui.component

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aimobileagent.ui.screen.chat.ChatMessage
import kotlinx.coroutines.delay

/**
 * 聊天气泡 — Claude Code 风格。
 */
@Composable
fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isFromUser) {
            // AI 头像
            Surface(
                modifier = Modifier.size(32.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) { Text("🤖", fontSize = 16.sp) }
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (message.isFromUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (message.isFromUser) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                tonalElevation = if (message.isFromUser) 2.dp else 0.dp
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 22.sp,
                        fontFamily = FontFamily.Default
                    ),
                    color = if (message.isFromUser) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                )
            }
            if (message.isThinking) {
                Spacer(Modifier.height(4.dp))
                ThinkingDots()
            }
        }

        if (message.isFromUser) {
            Spacer(Modifier.width(8.dp))
            Surface(
                modifier = Modifier.size(32.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) { Text("👤", fontSize = 16.sp) }
            }
        }
    }
}

/**
 * 动态思考动画 "Thinking..." → "Thinking.." → "Thinking."
 */
@Composable
fun ThinkingDots() {
    var dots by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dots = if (dots >= 3) 1 else dots + 1
        }
    }
    Text(
        text = "thinking${".".repeat(dots)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        fontFamily = FontFamily.Monospace
    )
}
