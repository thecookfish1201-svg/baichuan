package com.tongzhouweiji.baichuan.core.db.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 独立系统错误事件。聊天气泡只显示红色感叹号与简要提示，
 * 完整详情（已脱敏）在这里查看，支持按时间分类、删除与恢复。
 */
@Entity(tableName = "error_events", indices = [Index("agentId", "createdAt"), Index("createdAt")])
@Serializable
data class ErrorEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val agentId: Long? = null,
    val conversationId: Long? = null,
    val messageId: Long? = null,
    val code: String,
    val summary: String,
    val detail: String = "",
    val createdAt: Long,
    val deletedAt: Long? = null,
)
