package com.tongzhouweiji.baichuan.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tongzhouweiji.baichuan.core.db.entity.ConversationSummaryEntity

@Dao
interface SummaryDao {
    @Query("SELECT * FROM conversation_summaries WHERE conversationId = :conversationId ORDER BY id DESC LIMIT 1")
    suspend fun latest(conversationId: Long): ConversationSummaryEntity?

    @Insert
    suspend fun insert(summary: ConversationSummaryEntity): Long

    @Query("SELECT * FROM `conversation_summaries`")
    suspend fun listAll(): List<ConversationSummaryEntity>

    @Query("DELETE FROM `conversation_summaries`")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(items: List<ConversationSummaryEntity>)
}
