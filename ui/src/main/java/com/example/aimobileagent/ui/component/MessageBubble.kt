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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aimobileagent.ui.screen.chat.ChatMessage
import kotlinx.coroutines.delay

@Composable
fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isFromUser) {
            Surface(modifier = Modifier.size(32.dp), shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                Box(contentAlignment = Alignment.Center) { Text("🤖", fontSize = 16.sp) }
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (message.isFromUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (message.isFromUser) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                tonalElevation = if (message.isFromUser) 2.dp else 0.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (message.isThinking) {
                        ThinkingDots()
                    } else {
                        val displayText = message.text.ifBlank { "..." }
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = if (message.isFromUser) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        if (message.isStreaming) {
                            Spacer(Modifier.height(4.dp))
                            StreamingCursor()
                        }
                    }
                }
            }
        }

        if (message.isFromUser) {
            Spacer(Modifier.width(8.dp))
            Surface(modifier = Modifier.size(32.dp), shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                Box(contentAlignment = Alignment.Center) { Text("👤", fontSize = 16.sp) }
            }
        }
    }
}

@Composable
fun ThinkingDots() {
    var dots by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) { delay(400); dots = if (dots >= 3) 1 else dots + 1 }
    }
    Text("thinking${".".repeat(dots)}",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontStyle = FontStyle.Italic),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
}

@Composable
fun StreamingCursor() {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) { delay(530); visible = !visible }
    }
    if (visible) Text("▌", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
}
