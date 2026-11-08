package com.tongzhouweiji.baichuan.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tongzhouweiji.baichuan.core.db.entity.ScheduledMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledMessageDao {
    @Query("SELECT * FROM scheduled_messages ORDER BY id")
    fun observeAll(): Flow<List<ScheduledMessageEntity>>

    @Query("SELECT * FROM scheduled_messages WHERE enabled = 1")
    suspend fun listEnabled(): List<ScheduledMessageEntity>

    @Query("SELECT * FROM scheduled_messages WHERE id = :id")
    suspend fun findById(id: Long): ScheduledMessageEntity?

    @Query("SELECT * FROM scheduled_messages WHERE agentId = :agentId")
    suspend fun listByAgent(agentId: Long): List<ScheduledMessageEntity>

    @Insert
    suspend fun insert(item: ScheduledMessageEntity): Long

    @Update
    suspend fun update(item: ScheduledMessageEntity)

    @Query("UPDATE scheduled_messages SET lastRunAt = :now, nextRunAt = :next WHERE id = :id")
    suspend fun markRun(id: Long, now: Long, next: Long?)

    @Query("UPDATE scheduled_messages SET failureCount = failureCount + 1 WHERE id = :id")
    suspend fun increaseFailure(id: Long)

    @Query("SELECT * FROM `scheduled_messages`")
    suspend fun listAll(): List<ScheduledMessageEntity>

    @Query("DELETE FROM `scheduled_messages`")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(items: List<ScheduledMessageEntity>)
}
