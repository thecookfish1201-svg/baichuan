package com.tongzhouweiji.baichuan.model

/**
 * 统一模型适配层。聊天核心只依赖本接口，不感知任何供应商的请求参数。
 * 不同供应商支持的参数差异，由具体实现转换。
 */
interface ModelAdapter {
    /** 拉取服务商模型列表。 */
    suspend fun listModels(config: ProviderConfig): List<ModelInfo>

    /** 连接测试：能成功拉取模型列表即视为连通。 */
    suspend fun testConnection(config: ProviderConfig): Boolean

    /** 非流式对话。 */
    suspend fun chat(config: ProviderConfig, request: ChatRequest): ChatResult

    /**
     * 流式对话。正文增量通过 [onDelta] 回调，推理链增量通过 [onReasoning] 回调。
     * 不支持推理链的供应商不会触发 [onReasoning]。
     */
    suspend fun streamChat(
        config: ProviderConfig,
        request: ChatRequest,
        onDelta: suspend (String) -> Unit,
        onReasoning: suspend (String) -> Unit = {},
    ): ChatResult
}
