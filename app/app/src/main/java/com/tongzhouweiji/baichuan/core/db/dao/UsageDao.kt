package com.tongzhouweiji.baichuan.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tongzhouweiji.baichuan.core.db.entity.ApiUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
    @Insert
    suspend fun insert(usage: ApiUsageEntity): Long

    @Query("SELECT * FROM api_usage ORDER BY createdAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<ApiUsageEntity>>

    @Query("SELECT * FROM `api_usage`")
    suspend fun listAll(): List<ApiUsageEntity>

    @Query("DELETE FROM `api_usage`")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(items: List<ApiUsageEntity>)
}
