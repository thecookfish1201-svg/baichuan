package com.tongzhouweiji.baichuan.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tongzhouweiji.baichuan.core.db.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE agentId = :agentId AND archived = 0 AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeByAgent(agentId: Long): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE agentId = :agentId AND deletedAt IS NULL ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestByAgent(agentId: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun findById(id: Long): ConversationEntity?

    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET activeLeafMessageId = :leafId, updatedAt = :now WHERE id = :id")
    suspend fun updateActiveLeaf(id: Long, leafId: Long?, now: Long)

    @Query("UPDATE conversations SET lastReadMessageId = :messageId WHERE id = :id")
    suspend fun updateLastRead(id: Long, messageId: Long?)

    @Query("UPDATE conversations SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("SELECT * FROM `conversations`")
    suspend fun listAll(): List<ConversationEntity>

    @Query("DELETE FROM `conversations`")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(items: List<ConversationEntity>)
}
