package com.example.aimobileagent.data.local

import androidx.room.*
import com.example.aimobileagent.data.local.entity.AppCapabilityEntity

/**
 * Room DAO：App 能力目录数据访问。
 */
@Dao
interface AppCapabilityDao {
    @Query("SELECT * FROM app_capabilities")
    suspend fun getAllCapabilities(): List<AppCapabilityEntity>

    @Query("SELECT * FROM app_capabilities WHERE package_name = :packageName")
    suspend fun getCapability(packageName: String): AppCapabilityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(capabilities: List<AppCapabilityEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(capability: AppCapabilityEntity)

    @Delete
    suspend fun delete(capability: AppCapabilityEntity)

    @Query("SELECT package_name FROM app_capabilities")
    suspend fun getAllPackageNames(): List<String>

    @Query("SELECT * FROM app_capabilities ORDER BY id ASC")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<AppCapabilityEntity>>
}
