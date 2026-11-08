package com.tongzhouweiji.baichuan.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tongzhouweiji.baichuan.core.db.entity.AttachmentEntity

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE messageId = :messageId")
    suspend fun listByMessage(messageId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE messageId IN (:messageIds)")
    suspend fun listByMessages(messageIds: List<Long>): List<AttachmentEntity>

    @Insert
    suspend fun insertAll(items: List<AttachmentEntity>)

    @Query("SELECT * FROM `attachments`")
    suspend fun listAll(): List<AttachmentEntity>

    @Query("DELETE FROM `attachments`")
    suspend fun clearAll()
}
