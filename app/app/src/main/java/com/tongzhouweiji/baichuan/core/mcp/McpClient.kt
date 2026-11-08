package com.tongzhouweiji.baichuan.core.mcp

import com.tongzhouweiji.baichuan.core.net.HttpClientProvider
import com.tongzhouweiji.baichuan.model.ModelException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * MCP 客户端（Streamable HTTP，JSON-RPC 2.0）。
 * 第一版只做 initialize 与 tools/list：连接验证 + 工具清单缓存。
 * 工具调用在 Agent 阶段接入，参数与结果只记录摘要。
 */
object McpClient {

    data class ServerInfo(val name: String, val protocolVersion: String)

    data class Tool(
        val name: String,
        val description: String,
        val schemaJson: String,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mediaType = "application/json".toMediaType()

    suspend fun initialize(endpoint: String): ServerInfo = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "initialize")
            putJsonObject("params") {
                put("protocolVersion", "2024-11-05")
                putJsonObject("capabilities") {}
                putJsonObject("clientInfo") {
                    put("name", "baichuan")
                    put("version", "0.1.0")
                }
            }
        }
        val result = post(endpoint, body.toString())
        val serverInfo = result.jsonObject["serverInfo"]?.jsonObject
        val version = result.jsonObject["protocolVersion"]?.jsonPrimitive?.contentOrNull.orEmpty()
        ServerInfo(
            name = serverInfo?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty(),
            protocolVersion = version,
        )
    }

    suspend fun listTools(endpoint: String): List<Tool> = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 2)
            put("method", "tools/list")
            putJsonObject("params") {}
        }
        val result = post(endpoint, body.toString())
        val tools = result.jsonObject["tools"]?.jsonArray ?: return@withContext emptyList()
        tools.mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            Tool(
                name = name,
                description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                schemaJson = obj["inputSchema"]?.toString() ?: "{}",
            )
        }
    }

    private suspend fun post(endpoint: String, body: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json, text/event-stream")
            .post(body.toRequestBody(mediaType))
            .build()
        try {
            HttpClientProvider.client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ModelException("mcp_http_${response.code}", "MCP 连接失败（${response.code}）")
                }
                val root = json.parseToJsonElement(text).jsonObject
                root["error"]?.let { error ->
                    val message = error.jsonObject["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    throw ModelException("mcp_error", "MCP 返回错误：$message")
                }
                root["result"] ?: throw ModelException("mcp_empty", "MCP 未返回结果")
            }
        } catch (e: ModelException) {
            throw e
        } catch (e: Exception) {
            throw ModelException("mcp_failed", "MCP 连接失败，请检查地址与网络", e)
        }
    }
}
