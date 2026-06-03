package com.example.aimobileagent

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 自定义 Application，启用 Hilt 依赖注入。
 */
@HiltAndroidApp
class AgentApplication : Application()
