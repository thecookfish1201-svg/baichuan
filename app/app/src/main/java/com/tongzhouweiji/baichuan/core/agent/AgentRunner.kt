package com.tongzhouweiji.baichuan.core.agent

import com.tongzhouweiji.baichuan.core.db.BaichuanDatabase
import com.tongzhouweiji.baichuan.core.db.entity.AgentActionEntity
import com.tongzhouweiji.baichuan.core.db.entity.AgentRunEntity
import com.tongzhouweiji.baichuan.model.ChatMessage
import com.tongzhouweiji.baichuan.model.ChatRequest
import com.tongzhouweiji.baichuan.model.ModelAdapterRegistry
import com.tongzhouweiji.baichuan.model.ProviderConfig

/**
 * 真正的 Tool Calling 闭环：
 * 模型看到工具定义 → 自主返回结构化 tool_calls → 百川校验权限并真实执行
 * → 执行结果作为 tool 消息回传模型 → 模型继续推理，直到给出最终答复。
 * 不使用聊天 UI 或模型文字模拟调用。
 */
class AgentRunner(private val db: BaichuanDatabase) {

    data class RunResult(
        val text: String,
        val runId: Long,
        val steps: Int,
    )


    suspend fun run(
        agentId: Long,
        goal: String,
        systemPrompt: String,
        config: ProviderConfig,
        modelName: String,
    ): RunResult {
        val consent = db.agentRunDao().findConsent(agentId)
            ?: throw IllegalStateException("该智能体未获得 Agent 授权")
        val tools = AgentToolRegistry.definitions()
        val messages = mutableListOf<ChatMessage>()
        if (systemPrompt.isNotBlank()) messages.add(ChatMessage(role = "system", content = systemPrompt))
        messages.add(ChatMessage(role = "user", content = goal))

        val runId = db.agentRunDao().insertRun(
            AgentRunEntity(
                agentId = agentId,
                goal = goal,
                permissionLevel = consent.permissionLevel,
                state = "running",
                startedAt = System.currentTimeMillis(),
            ),
        )
        val adapter = ModelAdapterRegistry.adapterFor(config.protocol)
        var steps = 0

        repeat(MAX_ROUNDS) {
            val result = adapter.chat(
                config = config,
                request = ChatRequest(
                    model = modelName,
                    messages = messages.toList(),
                    stream = false,
                    tools = tools,
                ),
            )
            if (result.toolCalls.isEmpty()) {
                db.agentRunDao().finishRun(runId, "done", System.currentTimeMillis(), null)
                return RunResult(result.content, runId, steps)
            }
            // 记录模型返回的工具调用请求，供下一轮上下文使用。
            messages.add(
                ChatMessage(
                    role = "assistant",
                    content = result.content,
                    toolCalls = result.toolCalls,
                ),
            )
            result.toolCalls.forEach { call ->
                steps += 1
                val output = executeTool(agentId, runId, call)
                messages.add(
                    ChatMessage(
                        role = "tool",
                        content = output,
                        toolCallId = call.id,
                    ),
                )
            }
        }
        db.agentRunDao().finishRun(runId, "failed", System.currentTimeMillis(), "超过最大工具调用轮数")
        return RunResult("工具调用次数超过上限，已停止。", runId, steps)
    }

    private suspend fun executeTool(
        agentId: Long,
        runId: Long,
        call: com.tongzhouweiji.baichuan.model.ToolCallRequest,
    ): String {
        val tool = AgentToolRegistry.find(call.name)
            ?: return "工具未注册：${call.name}".also {
                db.agentRunDao().insertAction(
                    AgentActionEntity(
                        runId = runId,
                        toolName = call.name,
                        argsSummary = call.arguments.take(200),
                        confirmed = false,
                        resultSummary = it.take(300),
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        val output = runCatching {
            AgentToolRegistry.execute(tool.id, db)
        }.getOrElse { "工具执行失败：${it.message}" }
        db.agentRunDao().insertAction(
            AgentActionEntity(
                runId = runId,
                toolName = call.name,
                argsSummary = call.arguments.take(200),
                confirmed = true,
                resultSummary = output.take(300),
                createdAt = System.currentTimeMillis(),
            ),
        )
        return output
    }

    companion object {
        private const val MAX_ROUNDS = 6
    }
}
