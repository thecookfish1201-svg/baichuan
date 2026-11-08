package com.tongzhouweiji.baichuan.model

/** 暴露给模型的工具定义（OpenAI function calling 格式）。 */
data class ToolDefinition(
    val name: String,
    val description: String,
    /** JSON Schema 字符串，如 {"type":"object","properties":{}} */
    val parametersJson: String = "{\"type\":\"object\",\"properties\":{}}",
)

/** 模型返回的结构化工具调用请求。 */
data class ToolCallRequest(
    val id: String,
    val name: String,
    /** 模型给出的参数 JSON 字符串 */
    val arguments: String = "{}",
)
