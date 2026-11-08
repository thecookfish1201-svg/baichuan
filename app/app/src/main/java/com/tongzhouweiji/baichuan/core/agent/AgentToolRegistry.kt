package com.tongzhouweiji.baichuan.core.agent

import com.tongzhouweiji.baichuan.core.db.BaichuanDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 受控工具注册表。Agent 只能调用这里注册的工具，不提供任意命令执行入口。 */
object AgentToolRegistry {

    data class AgentTool(
        val id: String,
        val title: String,
        val risk: String,
        val description: String,
        val parametersJson: String = "{\"type\":\"object\",\"properties\":{}}",
    )

    val tools = listOf(
        AgentTool("time_now", "获取当前时间", "low", "返回设备当前日期与时间"),
        AgentTool("data_count_agents", "统计智能体数量", "low", "返回本地智能体数量（只读）"),
        AgentTool("data_count_messages", "统计消息数量", "low", "返回本地消息数量（只读）"),
    )

    fun find(id: String): AgentTool? = tools.firstOrNull { it.id == id }

    /** 转换为模型可用的工具定义。 */
    fun definitions(): List<com.tongzhouweiji.baichuan.model.ToolDefinition> =
        tools.map { com.tongzhouweiji.baichuan.model.ToolDefinition(it.id, it.description, it.parametersJson) }

    suspend fun execute(id: String, db: BaichuanDatabase): String = when (id) {
        "time_now" -> SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
        "data_count_agents" -> "当前共有 ${db.agentDao().listAll().size} 个智能体"
        "data_count_messages" -> "当前共有 ${db.messageDao().listAll().size} 条消息"
        else -> throw IllegalArgumentException("未知工具：$id")
    }
}
