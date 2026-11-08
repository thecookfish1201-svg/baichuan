package com.tongzhouweiji.baichuan.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tongzhouweiji.baichuan.core.db.entity.ErrorEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ErrorEventDao {
    @Query("SELECT * FROM error_events WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<ErrorEventEntity>>

    @Query("SELECT * FROM error_events ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ErrorEventEntity>>

    @Insert
    suspend fun insert(event: ErrorEventEntity): Long

    @Query("UPDATE error_events SET deletedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE error_events SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("SELECT * FROM `error_events`")
    suspend fun listAll(): List<ErrorEventEntity>

    @Query("DELETE FROM `error_events`")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(items: List<ErrorEventEntity>)
}
