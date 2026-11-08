package com.tongzhouweiji.baichuan.core.db.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 长期记忆。与压缩摘要分开：记忆跨会话保留，摘要只替代一次上下文。 */
@Entity(
    tableName = "memories",
    foreignKeys = [
        ForeignKey(
            entity = AgentEntity::class,
            parentColumns = ["id"],
            childColumns = ["agentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("agentId")],
)
@Serializable
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val agentId: Long,
    val content: String,
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
