package com.tongzhouweiji.baichuan.core.agent

import android.content.Context
import com.tongzhouweiji.baichuan.core.db.BaichuanDatabase
import com.tongzhouweiji.baichuan.core.db.entity.AgentConsentEntity
import com.tongzhouweiji.baichuan.core.security.SecretStore
import com.tongzhouweiji.baichuan.model.ChatMessage
import com.tongzhouweiji.baichuan.model.ChatRequest
import com.tongzhouweiji.baichuan.model.ModelAdapterRegistry
import com.tongzhouweiji.baichuan.model.ProviderConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 实验性 Agent 管理器：启用状态、同意记录、目标规划与受控工具执行。
 * 默认关闭；规划由模型生成 JSON 计划，执行前必须逐条确认。
 */
class AgentManager(
    private val context: Context,
    private val db: BaichuanDatabase,
) {

    data class ToolCall(val tool: String, val args: String)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        const val DOC_VERSION = "2026-11-03"
    }

    /** 该智能体是否被授予 Agent 能力。 */
    suspend fun isAuthorized(agentId: Long): Boolean = db.agentRunDao().findConsent(agentId) != null

    /** 该智能体的授权等级；null 表示未授权。 */
    suspend fun levelOf(agentId: Long): String? = db.agentRunDao().findConsent(agentId)?.permissionLevel

    /** 授权：写入同意记录（绑定智能体）。 */
    suspend fun authorize(agentId: Long, permissionLevel: String) {
        db.agentRunDao().deleteConsent(agentId)
        db.agentRunDao().insertConsent(
            AgentConsentEntity(
                agentId = agentId,
                docVersion = DOC_VERSION,
                permissionLevel = permissionLevel,
                acceptedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** 撤销：删除该智能体的授权记录。 */
    suspend fun revoke(agentId: Long) {
        db.agentRunDao().deleteConsent(agentId)
    }



    private suspend fun requireAuthorized(agentId: Long) {
        if (!isAuthorized(agentId)) {
            throw IllegalStateException("该智能体未获得 Agent 授权")
        }
    }
}
