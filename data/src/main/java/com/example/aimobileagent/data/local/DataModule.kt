package com.example.aimobileagent.data.local

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.example.aimobileagent.data.remote.DeepSeekApiService
import com.example.aimobileagent.data.remote.LLMResponseParser
import com.example.aimobileagent.data.remote.PromptTemplateEngine
import com.example.aimobileagent.data.remote.StreamingLLMClient
import com.example.aimobileagent.data.repository.LLMRepositoryImpl
import com.example.aimobileagent.data.repository.TaskRepositoryImpl
import com.example.aimobileagent.domain.repository.LLMRepository
import com.example.aimobileagent.domain.repository.TaskRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    // ===== Room =====

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "agent_database.db"
        ).build()
    }

    @Provides
    fun provideTaskDao(database: AppDatabase) = database.taskDao()

    @Provides
    fun provideAppCapabilityDao(database: AppDatabase) = database.appCapabilityDao()

    // ===== Repository 绑定 =====

    @Provides
    @Singleton
    fun provideTaskRepository(impl: TaskRepositoryImpl): TaskRepository = impl

    @Provides
    @Singleton
    fun provideLLMRepository(impl: LLMRepositoryImpl): LLMRepository = impl

    @Provides
    @Singleton
    fun provideAppCapabilityRepository(
        impl: com.example.aimobileagent.data.repository.AppCapabilityRepositoryImpl
    ): com.example.aimobileagent.domain.repository.AppCapabilityRepository = impl

    // ===== Retrofit =====

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)  // 流式需要长超时
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val json = Json { ignoreUnknownKeys = true }
        return Retrofit.Builder()
            .baseUrl("https://api.deepseek.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideDeepSeekApiService(retrofit: Retrofit): DeepSeekApiService {
        return retrofit.create(DeepSeekApiService::class.java)
    }

    // ===== 工具类 =====

    @Provides
    @Singleton
    fun providePromptTemplateEngine(): PromptTemplateEngine = PromptTemplateEngine()

    @Provides
    @Singleton
    fun provideLLMResponseParser(): LLMResponseParser = LLMResponseParser()
}
