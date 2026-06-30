package com.example.aimobileagent.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.aimobileagent.domain.repository.LLMRepository
import com.example.aimobileagent.domain.repository.TaskRepository
import com.example.aimobileagent.domain.usecase.*
import com.example.aimobileagent.execution.StepExecutorFactory
import com.example.aimobileagent.execution.executor.*
import com.example.aimobileagent.execution.screen.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val PREFS_NAME = "agent_prefs"
    private val SENSITIVE_PREF_KEYS = setOf(
        "api_key",
        "api_endpoint",
        "model_name",
        "api_key_deepseek-chat",
        "api_key_deepseek-reasoner",
        "api_key_gpt-4o-mini",
        "api_key_gpt-4o",
        "api_key_claude-3.5-haiku",
        "api_key_claude-sonnet-4-6",
        "api_key_gemini-2.0-flash"
    )

    @Provides @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        val legacyPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val encryptedPrefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        migrateLegacyPrefs(legacyPrefs, encryptedPrefs)
        return encryptedPrefs
    }

    private fun migrateLegacyPrefs(
        legacyPrefs: SharedPreferences,
        encryptedPrefs: SharedPreferences
    ) {
        val updates = legacyPrefs.all
            .filterKeys { it in SENSITIVE_PREF_KEYS }
            .filter { (key, value) ->
                value is String && encryptedPrefs.getString(key, null).isNullOrBlank()
            }

        if (updates.isEmpty()) return

        encryptedPrefs.edit().apply {
            updates.forEach { (key, value) -> putString(key, value as String) }
        }.apply()
        legacyPrefs.edit().apply {
            updates.keys.forEach { remove(it) }
        }.apply()
    }

    // ===== Execution 层 DI =====
    @Provides @Singleton fun provideOpenAppExecutor(): OpenAppExecutor = OpenAppExecutor()
    @Provides @Singleton fun provideTapElementExecutor(): TapElementExecutor = TapElementExecutor()
    @Provides @Singleton fun provideTypeTextExecutor(): TypeTextExecutor = TypeTextExecutor()
    @Provides @Singleton fun provideSwipeExecutor(): SwipeExecutor = SwipeExecutor()
    @Provides @Singleton fun provideSearchExecutor(): SearchExecutor = SearchExecutor()
    @Provides @Singleton fun provideShareToExecutor(): ShareToExecutor = ShareToExecutor()
    @Provides @Singleton fun provideStepExecutorFactory(factory: StepExecutorFactory): StepExecutor = factory
    @Provides @Singleton fun provideScreenParser(): ScreenParser = ScreenParser()
    @Provides @Singleton fun provideElementLocator(): ElementLocator = ElementLocator()
    @Provides @Singleton fun provideSafetyChecker(): SafetyChecker = SafetyChecker()
    @Provides @Singleton fun provideVisionAnalyzer(): VisionAnalyzer = VisionAnalyzer()
    @Provides @Singleton fun provideScreenStateObserver(impl: ScreenStateObserverImpl): ScreenStateObserver = impl

    // ===== UseCases =====
    @Provides @Singleton
    fun provideProcessCommandUseCase(llm: LLMRepository, task: TaskRepository): ProcessCommandUseCase =
        ProcessCommandUseCase(llm, task)

    @Provides @Singleton
    fun provideExecuteTaskUseCase(task: TaskRepository): ExecuteTaskUseCase =
        ExecuteTaskUseCase(task)

    @Provides @Singleton
    fun provideGetTaskHistoryUseCase(task: TaskRepository): GetTaskHistoryUseCase =
        GetTaskHistoryUseCase(task)
}
