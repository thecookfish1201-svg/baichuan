package com.tongzhouweiji.baichuan.model

object ProviderProtocol {
    const val OPENAI_COMPATIBLE = "openai_compatible"
    const val OP = "op"

    val all = listOf(
        OPENAI_COMPATIBLE to "OpenAI 兼容",
        OP to "OP（协议待确认）",
    )
}
