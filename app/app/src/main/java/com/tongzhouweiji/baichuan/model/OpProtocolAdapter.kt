package com.tongzhouweiji.baichuan.model

/**
 * OP 协议占位实现。协议定义待项目发起者确认前，不提供任何猜测性实现，
 * 调用时明确报「暂不可用」，避免把未定义协议伪装成可用。
 */
object OpProtocolAdapter : ModelAdapter {
    private fun unavailable(): Nothing =
        throw ModelException("op_unavailable", "OP 协议定义尚未确认，当前版本暂不可用")

    override suspend fun listModels(config: ProviderConfig): List<ModelInfo> = unavailable()

    override suspend fun testConnection(config: ProviderConfig): Boolean = false

    override suspend fun chat(config: ProviderConfig, request: ChatRequest): ChatResult = unavailable()

    override suspend fun streamChat(
        config: ProviderConfig,
        request: ChatRequest,
        onDelta: suspend (String) -> Unit,
        onReasoning: suspend (String) -> Unit,
    ): ChatResult = unavailable()
}
