package com.tongzhouweiji.baichuan.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tongzhouweiji.baichuan.core.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND deletedAt IS NULL ORDER BY createdAt ASC")
    fun observeByConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND deletedAt IS NULL ORDER BY createdAt ASC")
    suspend fun listByConversation(conversationId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun findById(id: Long): MessageEntity?

    @Query("SELECT COALESCE(MAX(siblingSeq), 0) FROM messages WHERE parentId IS :parentId")
    suspend fun maxSiblingSeq(parentId: Long?): Int

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("UPDATE messages SET content = :content, updatedAt = :now WHERE id = :id")
    suspend fun updateContent(id: Long, content: String, now: Long)

    @Query("UPDATE messages SET status = :status, content = :content, errorSummary = :error, reasoningContent = :reasoning, reasoningMs = :reasoningMs, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(
        id: Long,
        status: String,
        content: String,
        error: String?,
        reasoning: String?,
        reasoningMs: Long?,
        now: Long,
    )

    @Query("UPDATE messages SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE messages SET sourcesJson = :json, updatedAt = :now WHERE id = :id")
    suspend fun updateSources(id: Long, json: String, now: Long)

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND deletedAt IS NULL")
    suspend fun countByConversation(conversationId: Long): Int

    @Query("SELECT * FROM `messages`")
    suspend fun listAll(): List<MessageEntity>

    @Query("DELETE FROM `messages`")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(items: List<MessageEntity>)
}
