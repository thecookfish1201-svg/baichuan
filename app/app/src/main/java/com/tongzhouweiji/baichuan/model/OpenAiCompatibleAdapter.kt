package com.tongzhouweiji.baichuan.model

import com.tongzhouweiji.baichuan.core.log.Logger
import com.tongzhouweiji.baichuan.core.net.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * OpenAI 兼容协议适配器。DeepSeek、OpenAI 及多数第三方中转都走这套协议。
 * 所有供应商差异（地址、模型名、参数）在这里转换，聊天核心不感知。
 */
object OpenAiCompatibleAdapter : ModelAdapter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mediaType = "application/json".toMediaType()

    private fun normalizeBase(baseUrl: String): String {
        var base = baseUrl.trim().trimEnd('/')
        if (base.endsWith("/chat/completions")) {
            base = base.removeSuffix("/chat/completions")
        }
        return base
    }

    private fun chatUrl(config: ProviderConfig): String = normalizeBase(config.baseUrl) + "/chat/completions"

    private fun modelsUrl(config: ProviderConfig): String = normalizeBase(config.baseUrl) + "/models"

    override suspend fun listModels(config: ProviderConfig): List<ModelInfo> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(modelsUrl(config))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .get()
            .build()
        try {
            HttpClientProvider.client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw httpError(response.code, body, requestIdOf(response))
                }
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonArray ?: return@withContext emptyList()
                data.mapNotNull { element ->
                    val id = element.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    ModelInfo(id = id, displayName = id)
                }
            }
        } catch (e: ModelException) {
            throw e
        } catch (e: IOException) {
            throw ModelException("network", "网络连接失败，请检查网络与接口地址", e)
        } catch (e: Exception) {
            throw ModelException("parse", "模型列表解析失败", e)
        }
    }

    override suspend fun testConnection(config: ProviderConfig): Boolean =
        runCatching { listModels(config) }.isSuccess

    override suspend fun chat(config: ProviderConfig, request: ChatRequest): ChatResult =
        withContext(Dispatchers.IO) {
            val body = buildBody(config, request, stream = false)
            val httpRequest = Request.Builder()
                .url(chatUrl(config))
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), body).toRequestBody(mediaType))
                .build()
            try {
                HttpClientProvider.client.newCall(httpRequest).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    Logger.d("chat response code=${response.code} len=${text.length}")
                    if (!response.isSuccessful) {
                        throw httpError(response.code, text, requestIdOf(response))
                    }
                    parseFullResponse(text)
                }
            } catch (e: ModelException) {
                throw e
            } catch (e: IOException) {
                throw ModelException("network", "网络连接失败，请检查网络与接口地址", e)
            }
        }

    override suspend fun streamChat(
        config: ProviderConfig,
        request: ChatRequest,
        onDelta: suspend (String) -> Unit,
        onReasoning: suspend (String) -> Unit,
    ): ChatResult = withContext(Dispatchers.IO) {
        val body = buildBody(config, request, stream = true)
        val httpRequest = Request.Builder()
            .url(chatUrl(config))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), body).toRequestBody(mediaType))
            .build()
        try {
            HttpClientProvider.client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    throw httpError(response.code, errBody, requestIdOf(response))
                }
                val source = response.body?.source() ?: throw ModelException("empty", "服务端未返回内容")
                val content = StringBuilder()
                val reasoning = StringBuilder()
                var promptTokens: Int? = null
                var completionTokens: Int? = null
                // 按 SSE 规范分帧：空行结束一个事件，同一事件的多个 data 行用换行拼接。
                val eventData = StringBuilder()
                var finished = false
                suspend fun flushEvent() {
                    if (eventData.isEmpty()) return
                    val data = eventData.toString()
                    eventData.clear()
                    if (data.equals("[DONE]", ignoreCase = true)) {
                        finished = true
                        return
                    }
                    // 服务端可能在流中下发 error 事件：不能静默忽略，否则会显示「空回复但成功」。
                    val eventRoot = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
                    eventRoot?.get("error")?.let { element ->
                        val raw = runCatching {
                            element.jsonObject["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        }.getOrDefault("")
                        throw ModelException(
                            "stream_error",
                            streamErrorMessage(raw),
                            diagnostic = "SSE error event: " + Logger.redact(data.take(2000)),
                        )
                    }
                    // 单个畸形事件不应中断整个流。
                    val parsed = runCatching { parseStreamChunk(data, content, reasoning) }
                        .onFailure { com.tongzhouweiji.baichuan.core.log.Logger.w("跳过异常 SSE 事件", it) }
                        .getOrNull() ?: return
                    val (delta, think) = parsed
                    if (think.isNotEmpty()) onReasoning(think)
                    if (delta.isNotEmpty()) onDelta(delta)
                    parseUsage(data)?.let { usage ->
                        promptTokens = usage.first
                        completionTokens = usage.second
                    }
                }
                while (!finished) {
                    val line = source.readUtf8Line() ?: break
                    val normalized = line.trimEnd('\r')
                    if (normalized.isEmpty()) {
                        flushEvent()
                        continue
                    }
                    if (!normalized.startsWith("data:")) continue
                    val payload = normalized.removePrefix("data:").removePrefix(" ")
                    if (eventData.isNotEmpty()) eventData.append('\n')
                    eventData.append(payload)
                }
                flushEvent()
                ChatResult(
                    content = content.toString(),
                    reasoning = reasoning.toString().ifBlank { null },
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                )
            }
        } catch (e: ModelException) {
            throw e
        } catch (e: IOException) {
            throw ModelException("network", "网络连接失败，请检查网络与接口地址", e)
        }
    }

    private fun buildBody(config: ProviderConfig, request: ChatRequest, stream: Boolean) = buildJsonObject {
        put("model", request.model)
        putJsonArray("messages") {
            request.messages.forEach { message ->
                addJsonObject {
                    put("role", message.role)
                    when {
                        message.role == "tool" -> {
                            put("content", message.content)
                            message.toolCallId?.let { put("tool_call_id", it) }
                        }
                        message.toolCalls.isNotEmpty() -> {
                            put("content", message.content)
                            putJsonArray("tool_calls") {
                                message.toolCalls.forEach { call ->
                                    addJsonObject {
                                        put("id", call.id)
                                        put("type", "function")
                                        putJsonObject("function") {
                                            put("name", call.name)
                                            put("arguments", call.arguments)
                                        }
                                    }
                                }
                            }
                        }
                        message.images.isEmpty() -> put("content", message.content)
                        else -> putJsonArray("content") {
                            addJsonObject {
                                put("type", "text")
                                put("text", message.content)
                            }
                            message.images.forEach { base64 ->
                                addJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") {
                                        put("url", "data:image/jpeg;base64,$base64")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (request.serverTools.isNotEmpty()) {
            // DeepSeek 服务端工具：搜索与阅读链接由服务端完成，不能与 function tools 混用。
            putJsonArray("tools") {
                request.serverTools.forEach { name ->
                    addJsonObject {
                        put("type", name)
                        put("name", name)
                    }
                }
            }
        } else if (request.tools.isNotEmpty()) {
            putJsonArray("tools") {
                request.tools.forEach { tool ->
                    addJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", json.parseToJsonElement(tool.parametersJson))
                        }
                    }
                }
            }
        }
        request.safetyLevel?.let { level ->
            putJsonObject("safety_check") { put("level", level) }
        }
        if (request.titleEnabled) {
            putJsonObject("title") { put("enabled", true) }
        }
        put("stream", stream)
        val isDeepSeek = config.baseUrl.contains("deepseek", ignoreCase = true)
        // 思考模式与温度互斥：开启思考时不发 temperature，避免「传了但被忽略」的假象。
        if (!request.reasoningEffort.isThinking) {
            put("temperature", request.temperature)
            // 关闭思考在 DeepSeek 侧用 thinking 字段表达；其他供应商不发送未知字段。
            if (request.reasoningEffort == ReasoningEffort.OFF && isDeepSeek) {
                put("thinking", buildJsonObject { put("type", "disabled") })
            }
        } else {
            put(
                "reasoning_effort",
                when (request.reasoningEffort) {
                    ReasoningEffort.LOW -> "low"
                    ReasoningEffort.MAX -> "max"
                    else -> "high"
                },
            )
        }
        request.topP?.let { put("top_p", it) }
        request.maxTokens?.let { put("max_tokens", it) }
    }

    private fun parseFullResponse(body: String): ChatResult {
        val root = json.parseToJsonElement(body).jsonObject
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw ModelException("empty", "服务端未返回有效回复")
        val message = choice["message"]?.jsonObject
        val content = message?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
        val reasoning = message?.get("reasoning_content")?.jsonPrimitive?.contentOrNull
            ?: message?.get("reasoning")?.jsonPrimitive?.contentOrNull
        val toolCalls = message?.get("tool_calls")?.jsonArray?.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val function = obj["function"]?.jsonObject ?: return@mapNotNull null
            val name = function["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            com.tongzhouweiji.baichuan.model.ToolCallRequest(
                id = id,
                name = name,
                arguments = function["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}",
            )
        }.orEmpty()
        val usage = root["usage"]?.jsonObject
        return ChatResult(
            content = content,
            reasoning = reasoning,
            promptTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull,
            completionTokens = usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull,
            toolCalls = toolCalls,
        )
    }

    /** 返回 (正文增量, 思考增量)。DeepSeek 用 reasoning_content，部分兼容服务用 reasoning。 */
    private fun parseStreamChunk(
        data: String,
        content: StringBuilder,
        reasoning: StringBuilder,
    ): Pair<String, String> {
        return try {
            val root = json.parseToJsonElement(data).jsonObject
            val choices = root["choices"]?.jsonArray ?: return "" to ""
            val delta = choices.firstOrNull()?.jsonObject?.get("delta")?.jsonObject ?: return "" to ""
            val piece = delta["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val think = delta["reasoning_content"]?.jsonPrimitive?.contentOrNull
                ?: delta["reasoning"]?.jsonPrimitive?.contentOrNull
                ?: ""
            if (piece.isNotEmpty()) content.append(piece)
            if (think.isNotEmpty()) reasoning.append(think)
            piece to think
        } catch (e: Exception) {
            "" to ""
        }
    }

    private fun parseUsage(data: String): Pair<Int?, Int?>? {
        return try {
            val root = json.parseToJsonElement(data).jsonObject
            val usage = root["usage"]?.jsonObject ?: return null
            val prompt = usage["prompt_tokens"]?.jsonPrimitive?.intOrNull
            val completion = usage["completion_tokens"]?.jsonPrimitive?.intOrNull
            if (prompt == null && completion == null) null else prompt to completion
        } catch (e: Exception) {
            null
        }
    }

    private fun requestIdOf(response: okhttp3.Response): String? =
        response.header("x-request-id")
            ?: response.header("request-id")
            ?: response.header("x-ds-request-id")

    /**
     * HTTP 错误映射：用户只看到可行动的文案；原始响应体、request_id 与供应商错误只进诊断。
     */
    internal fun httpError(code: Int, body: String, requestId: String? = null): ModelException {
        val serverMessage = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")
                ?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        val diagnostic = buildString {
            append("HTTP ").append(code)
            if (!requestId.isNullOrBlank()) append(" request_id=").append(requestId)
            if (body.isNotBlank()) append("\n").append(Logger.redact(body.take(2000)))
            if (!serverMessage.isNullOrBlank()) append("\nserver: ").append(Logger.redact(serverMessage.take(500)))
        }
        val message = when (code) {
            400 -> if (body.contains("must follow a user message", ignoreCase = true)) {
                "当前对话分支不完整，无法继续发送。请切换到其他版本，或新建对话后重试。"
            } else {
                "请求被服务端拒绝：对话内容或参数不符合要求。请检查模型配置，或新建对话后重试。"
            }
            401, 403 -> "认证失败：请检查服务商的 API Key 是否正确、是否有该模型权限。"
            404 -> "接口地址或模型名称不正确，请检查服务商配置。"
            413 -> "当前对话内容过长，请新建对话后重试。"
            422 -> "请求参数不被服务端接受，请检查模型与生成参数。"
            429 -> "请求过于频繁或额度不足，请稍后重试。"
            in 500..599 -> "服务暂时不可用（$code），请稍后重试。"
            else -> "请求失败（$code），请稍后重试。"
        }
        return ModelException("http_$code", message, diagnostic = diagnostic)
    }

    /** 流内错误文案映射，避免把供应商英文协议错误直接展示给用户。 */
    internal fun streamErrorMessage(raw: String): String = when {
        raw.contains("must follow a user message", ignoreCase = true) ->
            "当前对话分支不完整，无法继续发送。请切换到其他版本，或新建对话后重试。"
        raw.contains("rate limit", ignoreCase = true) || raw.contains("too many", ignoreCase = true) ->
            "请求过于频繁，请稍后重试。"
        raw.contains("too long", ignoreCase = true) || raw.contains("context length", ignoreCase = true) ->
            "当前对话内容过长，请新建对话后重试。"
        raw.contains("invalid api key", ignoreCase = true) || raw.contains("auth", ignoreCase = true) ->
            "认证失败：请检查服务商的 API Key。"
        raw.contains("content", ignoreCase = true) && raw.contains("risk", ignoreCase = true) ->
            "内容未通过服务端安全审核，请调整后重试。"
        else -> "生成中断：服务端返回错误，请稍后重试。"
    }
}
