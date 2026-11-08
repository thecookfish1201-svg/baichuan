package com.tongzhouweiji.baichuan.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tongzhouweiji.baichuan.core.db.entity.PluginPermissionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginPermissionDao {
    @Query("SELECT * FROM plugin_permissions WHERE pluginId = :pluginId")
    fun observeByPlugin(pluginId: String): Flow<List<PluginPermissionEntity>>

    @Query("SELECT * FROM plugin_permissions WHERE pluginId = :pluginId")
    suspend fun listByPlugin(pluginId: String): List<PluginPermissionEntity>

    @Query("SELECT * FROM plugin_permissions WHERE pluginId = :pluginId AND permission = :permission")
    suspend fun find(pluginId: String, permission: String): PluginPermissionEntity?

    @Insert
    suspend fun insertAll(items: List<PluginPermissionEntity>)

    @Query("DELETE FROM plugin_permissions WHERE pluginId = :pluginId")
    suspend fun deleteByPlugin(pluginId: String)

    @Query("DELETE FROM plugin_permissions")
    suspend fun clearAll()
}
