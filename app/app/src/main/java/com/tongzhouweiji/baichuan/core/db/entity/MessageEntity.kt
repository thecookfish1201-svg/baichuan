package com.tongzhouweiji.baichuan.core.db.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 消息。parentId 构成消息树，用于编辑/重新生成的历史分支，不覆盖原有后续对话；
 * siblingSeq 保证同一父节点下兄弟消息的稳定顺序；deletedAt 支持软删除与恢复。
 * 时间只作为元数据保存，不参与提示词前缀，避免破坏上下文缓存。
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("conversationId", "createdAt"),
        Index("parentId"),
    ],
)
@Serializable
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val contentType: String = "text",
    val parentId: Long? = null,
    val siblingSeq: Int = 0,
    val branchRootId: Long? = null,
    /** streaming / done / failed / cancelled */
    val status: String = "done",
    val errorId: Long? = null,
    /** 失败摘要（脱敏）。阶段三迁移到独立错误事件表。 */
    val errorSummary: String? = null,
    /** 推理链内容（DeepSeek reasoner 等返回的 reasoning_content）。 */
    val reasoningContent: String? = null,
    /** 思考耗时（毫秒），用于「已思考（用时 X 秒）」。 */
    val reasoningMs: Long? = null,
    /** 搜索结果来源（JSON），保留网址与必要来源信息。 */
    val sourcesJson: String? = null,
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
