package com.tongzhouweiji.baichuan.core.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.tongzhouweiji.baichuan.core.db.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE agentId = :agentId ORDER BY pinned DESC, updatedAt DESC")
    fun observeByAgent(agentId: Long): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE agentId = :agentId ORDER BY pinned DESC, updatedAt DESC")
    suspend fun listByAgent(agentId: Long): List<MemoryEntity>

    @Insert
    suspend fun insert(memory: MemoryEntity): Long

    @Delete
    suspend fun delete(memory: MemoryEntity)

    @Query("UPDATE memories SET pinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, now: Long)

    @Query("SELECT * FROM `memories`")
    suspend fun listAll(): List<MemoryEntity>

    @Query("DELETE FROM `memories`")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(items: List<MemoryEntity>)
}
