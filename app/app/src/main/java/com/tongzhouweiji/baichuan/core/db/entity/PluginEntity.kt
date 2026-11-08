package com.tongzhouweiji.baichuan.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 未济接口插件。state：installed / enabled / disabled / failed。
 * 插件数据与核心库隔离，插件异常不影响主聊天。
 */
@Entity(tableName = "plugins", indices = [Index(value = ["pluginId"], unique = true)])
@Serializable
data class PluginEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pluginId: String,
    val name: String,
    val author: String,
    val version: String,
    val apiVersion: String,
    val type: String,
    val permissionsJson: String = "[]",
    val state: String = "installed",
    val installPath: String,
    val previousVersion: String? = null,
    val installedAt: Long,
    val updatedAt: Long,
)
