package com.tongzhouweiji.baichuan.core.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tongzhouweiji.baichuan.core.db.entity.PluginEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginDao {
    @Query("SELECT * FROM plugins ORDER BY installedAt DESC")
    fun observeAll(): Flow<List<PluginEntity>>

    @Query("SELECT * FROM plugins WHERE state = 'enabled'")
    suspend fun listEnabled(): List<PluginEntity>

    @Query("SELECT * FROM plugins WHERE pluginId = :pluginId")
    suspend fun findByPluginId(pluginId: String): PluginEntity?

    @Insert
    suspend fun insert(plugin: PluginEntity): Long

    @Update
    suspend fun update(plugin: PluginEntity)

    @Delete
    suspend fun delete(plugin: PluginEntity)

    @Query("UPDATE plugins SET state = :state, updatedAt = :now WHERE pluginId = :pluginId")
    suspend fun updateState(pluginId: String, state: String, now: Long)
}
