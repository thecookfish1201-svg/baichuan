package com.tongzhouweiji.baichuan.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Agent 授权记录：按智能体授权，撤销即删除记录。 */
@Entity(tableName = "agent_consents", indices = [Index("agentId")])
@Serializable
data class AgentConsentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0") val agentId: Long = 0,
    val docVersion: String,
    val permissionLevel: String,
    val acceptedAt: Long,
)

/** Agent 运行记录。 */
@Entity(tableName = "agent_runs", indices = [Index("startedAt")])
@Serializable
data class AgentRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val agentId: Long? = null,
    val goal: String,
    val permissionLevel: String = "A",
    val state: String = "running",
    val startedAt: Long,
    val endedAt: Long? = null,
    val cancelReason: String? = null,
)

/** Agent 工具调用记录（参数与结果只存脱敏摘要）。 */
@Entity(tableName = "agent_actions", indices = [Index("runId")])
@Serializable
data class AgentActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val toolName: String,
    val argsSummary: String,
    val confirmed: Boolean,
    val resultSummary: String? = null,
    val createdAt: Long,
)
