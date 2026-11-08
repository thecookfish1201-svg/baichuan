package com.tongzhouweiji.baichuan.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tongzhouweiji.baichuan.core.db.entity.AgentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentDao {
    @Query("SELECT * FROM agents WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: Long): AgentEntity?

    @Query("SELECT COUNT(*) FROM agents WHERE deletedAt IS NULL")
    suspend fun count(): Int

    @Insert
    suspend fun insert(agent: AgentEntity): Long

    @Update
    suspend fun update(agent: AgentEntity)

    @Query("SELECT * FROM `agents`")
    suspend fun listAll(): List<AgentEntity>

    @Query("DELETE FROM `agents`")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(items: List<AgentEntity>)
}
