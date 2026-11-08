package com.tongzhouweiji.baichuan.model

/** 统一的消息结构，不绑定任何供应商。images 为 base64（不含 data: 前缀）。 */
data class ChatMessage(
    val role: String,
    val content: String,
    val images: List<String> = emptyList(),
    /** assistant 消息携带的工具调用请求 */
    val toolCalls: List<ToolCallRequest> = emptyList(),
    /** role=tool 时对应的调用 id */
    val toolCallId: String? = null,
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val stream: Boolean = true,
    val topP: Double? = null,
    val maxTokens: Int? = null,
    /** 推理强度，由适配层按供应商能力转换。 */
    val reasoningEffort: ReasoningEffort = ReasoningEffort.AUTO,
    /** 可用工具；为空表示不启用工具调用。 */
    val tools: List<ToolDefinition> = emptyList(),
)

data class ChatResult(
    val content: String,
    val reasoning: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val cachedTokens: Int? = null,
    /** 模型请求调用的工具；非空时 content 可能为空。 */
    val toolCalls: List<ToolCallRequest> = emptyList(),
)

data class ModelInfo(
    val id: String,
    val displayName: String,
)

/** 服务商运行配置。apiKey 只在内存中出现，落库时必须走 SecretStore。 */
data class ProviderConfig(
    val id: Long,
    val name: String,
    val baseUrl: String,
    val protocol: String,
    val apiKey: String,
)

class ModelException(
    val code: String,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
