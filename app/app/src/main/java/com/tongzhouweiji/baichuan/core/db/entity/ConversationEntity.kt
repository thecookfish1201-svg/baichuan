package com.tongzhouweiji.baichuan.core.db.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
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
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val agentId: Long,
    val title: String = "",
    val backgroundUri: String? = null,
    /** 当前选中分支的末端消息；消息树靠它确定展示路径。 */
    val activeLeafMessageId: Long? = null,
    val lastReadMessageId: Long? = null,
    val archived: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
