package com.tongzhouweiji.baichuan.core.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tongzhouweiji.baichuan.core.db.entity.ModelEntity
import com.tongzhouweiji.baichuan.core.db.entity.ProviderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers")
    suspend fun listAll(): List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun findById(id: Long): ProviderEntity?

    @Insert
    suspend fun insert(provider: ProviderEntity): Long

    @Update
    suspend fun update(provider: ProviderEntity)

    @Delete
    suspend fun delete(provider: ProviderEntity)

    @Query("SELECT * FROM models WHERE providerId = :providerId ORDER BY displayName")
    fun observeModels(providerId: Long): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models ORDER BY providerId, displayName")
    fun observeAllModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE providerId = :providerId ORDER BY displayName")
    suspend fun modelsOf(providerId: Long): List<ModelEntity>

    @Query("DELETE FROM models WHERE providerId = :providerId")
    suspend fun clearModels(providerId: Long)

    @Insert
    suspend fun insertModels(models: List<ModelEntity>)

    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun findModelById(id: Long): ModelEntity?

    @Query("SELECT * FROM models")
    suspend fun modelsAll(): List<ModelEntity>

    @Query("DELETE FROM models")
    suspend fun clearModelsAll()

    @Query("SELECT * FROM models WHERE isDefault = 1 LIMIT 1")
    suspend fun defaultModel(): ModelEntity?

    @Query("DELETE FROM `providers`")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(items: List<ProviderEntity>)
}
