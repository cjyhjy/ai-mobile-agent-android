# 🤖 AI Mobile Agent (Android)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blueviolet?logo=kotlin)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-10%2B-green?logo=android)](https://developer.android.com/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-indigo?logo=jetpackcompose)](https://developer.android.com/compose)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

An on-device AI assistant that automates Android phone operations through natural language commands — powered by **Large Language Models** and **AccessibilityService**.

> 🇨🇳 中文用户请见下方 [功能说明](#-功能说明)

---

## 📱 What It Does

Tell your phone what to do in plain language, and the AI agent executes it step by step:

| Command | Result |
|---------|--------|
| "打开飞行模式" | Opens Settings → toggles airplane mode |
| "给张三发微信说我到了" | Launches WeChat → finds contact → sends message |
| "在美团搜附近的火锅店" | Opens Meituan → searches → shows results |

---

## 🧠 Architecture

```
┌──────────────────────────────────────────────────┐
│                    USER INPUT                      │
│              (Natural Language / Voice)            │
└────────────────────┬─────────────────────────────┘
                     │
          ┌──────────▼──────────┐
          │   ProcessCommand    │  ── LLM parses command →
          │     UseCase         │     structured Task (JSON)
          └──────────┬──────────┘
                     │
          ┌──────────▼──────────┐
          │   ExecuteTask       │  ── OTAV Agent Loop ──
          │     UseCase         │     Observe → Think → Act → Verify
          └──────────┬──────────┘
                     │
     ┌───────────────┼───────────────┐
     │               │               │
┌────▼────┐   ┌──────▼──────┐   ┌───▼────┐
│  Screen  │   │    Step     │   │ Safety │
│  Parser  │   │  Executors  │   │ Checker│
└─────────┘   └─────────────┘   └────────┘
     │               │
     │    AccessibilityService  │
     │    (UI Tree + Gestures)  │
     └───────────────┬──────────┘
                     │
              ┌──────▼──────┐
              │  Android OS  │
              └──────────────┘
```

**Clean Architecture** with 5 modules:

```
app ──► domain ◄── data
 │                  │
 ├──► execution     │
 └──► ui ◄──────────┘
```

| Module | Responsibility |
|--------|---------------|
| `domain` | Pure Kotlin — models, repository interfaces, use cases |
| `data` | Room database, Retrofit API, prompt templates, SSE streaming |
| `execution` | AccessibilityService, step executors, screen parsing, safety |
| `ui` | Jetpack Compose screens — chat, task progress, history, settings |
| `app` | DI wiring (Hilt), Application, Manifest |

---

## ✨ Features

- 🗣️ **Natural Language to Action** — Type or speak a command, AI breaks it down and executes
- 🔄 **OTAV Agent Loop** — Observe → Think → Act → Verify with retry and fallback
- 🛡️ **Safety Protection** — Auto-detects payment/password screens, warns before sensitive actions
- 💬 **Chat Mode** — General-purpose AI chatbot with SSE streaming, multi-turn conversation
- 🎨 **Dark Theme** — Slate + Indigo color palette, Material 3 Design
- 📝 **Task History** — All tasks persisted locally, viewable and replayable
- 🧠 **Multi-Model** — DeepSeek / OpenAI / Anthropic / Google, independent API keys per model
- 🎤 **Voice Input** — Speech recognition via Android `SpeechRecognizer`
- 📱 **App Management** — Register which apps the agent can control
- 🧩 **Memory System** — Learns from successful executions to improve future prompts

---

## 🛠 Tech Stack

| Category | Technology |
|----------|-----------|
| **Language** | Kotlin 2.0.21 |
| **UI** | Jetpack Compose + Material 3 |
| **DI** | Dagger Hilt 2.51.1 |
| **Database** | Room 2.6.1 |
| **Network** | Retrofit 2.11 + OkHttp 4.12 |
| **Serialization** | kotlinx-serialization-json 1.7.3 |
| **Async** | kotlinx-coroutines 1.9.0 |
| **LLM API** | Direct DeepSeek/OpenAI/Anthropic/Google API (no proxy) |
| **Streaming** | OkHttp SSE (Server-Sent Events) |
| **Min SDK** | Android 10 (API 29) |
| **Target SDK** | Android 15 (API 35) |

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog (2024.1+) or newer
- JDK 17
- A physical Android device (AccessibilityService requires real device)
- An API key from [DeepSeek](https://platform.deepseek.com/) (or OpenAI / Anthropic / Google)

### Build & Run

```bash
# 1. Clone the repository
git clone https://github.com/cjyhjy/ai-mobile-agent-android.git
cd ai-mobile-agent-android

# 2. Open in Android Studio, let Gradle sync

# 3. Build APK
./gradlew assembleDebug

# 4. Install to device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Setup After Install

1. **Enable Accessibility Service** — Settings → Accessibility → "AI Mobile Agent" → toggle ON
2. **Configure API Key** — Open the app → top-right model picker → gear icon → enter your API key
3. **Register Apps** — Navigate to "App Manage" tab → add apps you want the agent to control
4. **Start Using** — Type a command in the chat and hit send!

---

## 📂 Project Structure

```
ai-mobile-agent-android/
├── app/                        # Entry point (Application, MainActivity, DI)
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/.../agentapp/
├── domain/                     # Pure Kotlin — models, interfaces, use cases
│   └── src/main/java/.../domain/
│       ├── model/              # Task, Step, ExecutionResult, enums
│       ├── repository/         # LLMRepository, TaskRepository interfaces
│       └── usecase/            # ProcessCommand, ExecuteTask, GetTaskHistory
├── data/                       # Room DB, Retrofit API, PromptEngine
│   └── src/main/java/.../data/
│       ├── local/              # Room entities, DAOs, UserPreferences
│       ├── remote/             # API service, DTOs, PromptTemplate, Streaming
│       └── repository/         # Repository implementations
├── execution/                  # AccessibilityService + Step Executors
│   └── src/main/java/.../execution/
│       ├── AgentAccessibilityService.kt
│       ├── executor/           # Tap, Type, Swipe, OpenApp, Search, ShareTo
│       └── screen/             # ScreenParser, ElementLocator, Safety, Vision
├── ui/                         # Jetpack Compose UI
│   └── src/main/java/.../ui/
│       ├── theme/              # Color, Typography, Theme
│       ├── navigation/         # Routes, NavGraph
│       ├── screen/             # Chat, TaskProgress, History, Settings, AppManage
│       └── component/          # MessageBubble, VoiceInputButton
├── NOTES.md                    # Development notes (Chinese)
├── test_agent.py               # ADB automated test script
└── gradle/                     # Gradle wrapper
```

---

## 🧪 Testing

The project includes an ADB-based test script:

```bash
# Run the automated test suite
python test_agent.py
```

Tests cover: chat mode, task mode, settings page, and API connectivity.

---

## 🔮 Roadmap

- [x] Multi-model support (DeepSeek, OpenAI, Anthropic, Google)
- [x] Voice input via SpeechRecognizer
- [x] Memory / personalization system
- [ ] Screenshot-based vision analysis (Android 14+ multimodal)
- [ ] Physical device end-to-end testing
- [ ] HarmonyOS port (in progress at [ai-mobile-agent-harmonyos](https://github.com/cjyhjy/ai-mobile-agent-harmonyos))

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

*Built with ❤️ by [cjyhjy](https://github.com/cjyhjy)*
