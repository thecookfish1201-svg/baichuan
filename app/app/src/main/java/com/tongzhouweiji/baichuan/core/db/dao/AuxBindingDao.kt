package com.tongzhouweiji.baichuan.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tongzhouweiji.baichuan.core.db.entity.AuxBindingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuxBindingDao {
    @Query("SELECT * FROM aux_bindings")
    fun observeAll(): Flow<List<AuxBindingEntity>>

    @Query("SELECT * FROM aux_bindings WHERE capability = :capability LIMIT 1")
    suspend fun find(capability: String): AuxBindingEntity?

    @Insert
    suspend fun insert(binding: AuxBindingEntity): Long

    @Query("DELETE FROM aux_bindings WHERE capability = :capability")
    suspend fun clear(capability: String)

    @Query("SELECT * FROM `aux_bindings`")
    suspend fun listAll(): List<AuxBindingEntity>

    @Query("DELETE FROM `aux_bindings`")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(items: List<AuxBindingEntity>)
}
