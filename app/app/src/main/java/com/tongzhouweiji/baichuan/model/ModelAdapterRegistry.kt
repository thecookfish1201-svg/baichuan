package com.tongzhouweiji.baichuan.model

object ModelAdapterRegistry {
    fun adapterFor(protocol: String): ModelAdapter = when (protocol) {
        ProviderProtocol.OP -> OpProtocolAdapter
        else -> OpenAiCompatibleAdapter
    }
}
