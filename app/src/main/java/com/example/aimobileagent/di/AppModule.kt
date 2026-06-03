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

    @Provides @Singleton
    fun provideEncryptedSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context, "agent_secure_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
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
