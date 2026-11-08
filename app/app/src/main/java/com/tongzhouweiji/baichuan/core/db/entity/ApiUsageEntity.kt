package com.tongzhouweiji.baichuan.core.db.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.Index
import androidx.room.PrimaryKey

/** 模型与搜索调用用量，用于缓存命中率、费用估算与排障。 */
@Entity(tableName = "api_usage", indices = [Index("createdAt"), Index("scope")])
@Serializable
data class ApiUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scope: String = "chat",
    val providerId: Long? = null,
    val modelId: Long? = null,
    val conversationId: Long? = null,
    val messageId: Long? = null,
    val requestKind: String = "chat",
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val cachedTokens: Int? = null,
    val reasoningTokens: Int? = null,
    val latencyMs: Long? = null,
    val success: Boolean = true,
    val createdAt: Long,
)
