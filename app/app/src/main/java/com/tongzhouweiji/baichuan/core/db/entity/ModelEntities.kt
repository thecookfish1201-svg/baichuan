package com.tongzhouweiji.baichuan.core.db.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 服务商配置。API Key 只保存引用（keyRef），明文不落库、不写日志。
 */
@Entity(tableName = "providers")
@Serializable
data class ProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val protocol: String,
    val keyRef: String? = null,
    val extraJson: String = "{}",
    val createdAt: Long,
)

@Entity(
    tableName = "models",
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("providerId")],
)
@Serializable
data class ModelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val modelName: String,
    val displayName: String,
    val capabilitiesJson: String = "{}",
    val isDefault: Boolean = false,
)

/**
 * 辅助能力绑定：视觉识别 / TTS / 上下文压缩 / 网络搜索。
 */
@Entity(tableName = "aux_bindings")
@Serializable
data class AuxBindingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val capability: String,
    val providerId: Long? = null,
    val modelId: Long? = null,
    val paramsJson: String = "{}",
)
