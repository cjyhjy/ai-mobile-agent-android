package com.example.aimobileagent.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.aimobileagent.data.local.AppCapabilityDao
import com.example.aimobileagent.data.local.entity.AppCapabilityEntity
import com.example.aimobileagent.domain.model.AppCapability
import com.example.aimobileagent.domain.repository.AppCapabilityRepository
import com.example.aimobileagent.domain.repository.InstalledAppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppCapabilityRepositoryImpl @Inject constructor(
    private val dao: AppCapabilityDao,
    @ApplicationContext private val context: Context
) : AppCapabilityRepository {

    override fun observeAll(): Flow<List<AppCapability>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAll(): List<AppCapability> =
        dao.getAllCapabilities().map { it.toDomain() }

    override suspend fun add(packageName: String, appName: String, supportedActions: List<String>) {
        val existing = dao.getCapability(packageName)
        if (existing != null) return

        val actions = supportedActions.joinToString(",") { "\"$it\"" }
        dao.insert(
            AppCapabilityEntity(
                packageName = packageName,
                appName = appName,
                supportedActions = "[$actions]",
                resourceIdMap = "{}"
            )
        )
    }

    override suspend fun remove(packageName: String) {
        val cap = dao.getCapability(packageName) ?: return
        dao.delete(cap)
    }

    override suspend fun getInstalledApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val registeredPkgs = dao.getAllPackageNames().toSet()

        return apps
            .filter { app ->
                val isLaunchable = pm.getLaunchIntentForPackage(app.packageName) != null
                val isSelf = app.packageName == context.packageName
                val notRegistered = app.packageName !in registeredPkgs
                isLaunchable && !isSelf && notRegistered
            }
            .map { app ->
                InstalledAppInfo(
                    packageName = app.packageName,
                    appName = pm.getApplicationLabel(app).toString(),
                    isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedBy { it.appName }
    }

    private fun AppCapabilityEntity.toDomain() = AppCapability(
        id = id,
        packageName = packageName,
        appName = appName,
        supportedActions = supportedActions
            .removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() },
        resourceIdMap = emptyMap()
    )
}
