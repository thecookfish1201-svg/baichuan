package com.tongzhouweiji.baichuan.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 插件权限授权记录。插件只能访问用户授权的资源。 */
@Entity(
    tableName = "plugin_permissions",
    indices = [Index(value = ["pluginId", "permission"], unique = true)],
)
@Serializable
data class PluginPermissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pluginId: String,
    val permission: String,
    val granted: Boolean = false,
    val grantedAt: Long? = null,
)
