package com.tongzhouweiji.baichuan.core.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** MCP 服务器连接配置。凭据加密保存引用。 */
@Entity(tableName = "mcp_servers", indices = [Index(value = ["name"], unique = true)])
@Serializable
data class McpServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val transport: String,
    val endpoint: String,
    val headersRef: String? = null,
    val enabled: Boolean = true,
    val lastStatus: String = "unknown",
    val lastConnectedAt: Long? = null,
    val createdAt: Long,
)

/** MCP 工具清单缓存。 */
@Entity(
    tableName = "mcp_tools",
    foreignKeys = [
        ForeignKey(
            entity = McpServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("serverId")],
)
@Serializable
data class McpToolEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val name: String,
    val description: String = "",
    val schemaJson: String = "{}",
    val enabled: Boolean = true,
)

/** MCP 调用记录（参数与结果只存脱敏摘要）。 */
@Entity(tableName = "mcp_call_logs", indices = [Index("createdAt")])
@Serializable
data class McpCallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val toolName: String,
    val argumentsDigest: String = "",
    val resultDigest: String = "",
    val success: Boolean = true,
    val errorCode: String? = null,
    val durationMs: Long? = null,
    val createdAt: Long,
)
