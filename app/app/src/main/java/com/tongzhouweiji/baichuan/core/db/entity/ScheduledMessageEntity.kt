package com.tongzhouweiji.baichuan.core.db.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 智能体主动消息。mode: generated（调用模型生成） / static（定时发送已生成内容）。 */
@Entity(
    tableName = "scheduled_messages",
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
data class ScheduledMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val agentId: Long,
    val mode: String = "generated",
    val content: String = "",
    val windowStart: String = "08:00",
    val windowEnd: String = "23:00",
    val intervalMin: Int = 60,
    val intervalMax: Int = 180,
    val enabled: Boolean = true,
    val nextRunAt: Long? = null,
    val lastRunAt: Long? = null,
    val failureCount: Int = 0,
)
