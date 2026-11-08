package com.tongzhouweiji.baichuan.core.db.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.PrimaryKey

/**
 * 智能体。删除采用软删除（deletedAt 非空），与「删除聊天记录」「删除插件数据」是独立操作。
 * enabled 表示停用（不再响应、不再主动发消息），blocked 表示拉黑，两者语义不同。
 */
@Entity(tableName = "agents")
@Serializable
data class AgentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatarUri: String? = null,
    val persona: String = "",
    val systemPrompt: String = "",
    val backgroundUri: String? = null,
    val modelConfigId: Long? = null,
    val auxConfigId: Long? = null,
    val memoryEnabled: Boolean = true,
    val enabled: Boolean = true,
    val contextPolicy: String = "default",
    /** 生成参数：温度、推理深度、流式开关等，JSON 存储，不塞进提示词。 */
    val generationJson: String = "{}",
    val blocked: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
