package com.tongzhouweiji.baichuan.core.search

import com.tongzhouweiji.baichuan.core.net.HttpClientProvider
import com.tongzhouweiji.baichuan.model.ModelException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 网络搜索管理器。支持两种服务：
 * 1. DeepSeek 官方 Search（POST /v1/tools/search，复用已有 DeepSeek API Key，按次计费）；
 * 2. 自定义 Tavily 风格接口（用户自备 Key）。
 * 未配置时搜索开关不生效并提示。
 */
object SearchManager {

    enum class Mode { DEEPSEEK, TAVILY }

    data class SearchConfig(
        val mode: Mode,
        val endpoint: String,
        val apiKey: String,
    )

    data class SearchResult(
        val title: String,
        val url: String,
        val content: String,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mediaType = "application/json".toMediaType()

    const val DEEPSEEK_SEARCH_ENDPOINT = "https://api.deepseek.com/v1/tools/search"

    suspend fun search(config: SearchConfig, query: String, maxResults: Int = 5): List<SearchResult> =
        withContext(Dispatchers.IO) {
            if (config.apiKey.isBlank()) return@withContext emptyList()
            when (config.mode) {
                Mode.DEEPSEEK -> deepSeekSearch(config, query, maxResults)
                Mode.TAVILY -> tavilySearch(config, query, maxResults)
            }
        }

    private fun deepSeekSearch(
        config: SearchConfig,
        query: String,
        maxResults: Int,
    ): List<SearchResult> {
        val body = buildJsonObject {
            put("query", query.take(128))
            put("count", maxResults.coerceIn(1, 50))
            put("need_content", false)
        }
        val request = Request.Builder()
            .url(DEEPSEEK_SEARCH_ENDPOINT)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), body).toRequestBody(mediaType))
            .build()
        return try {
            HttpClientProvider.client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ModelException(
                        "search_http_${response.code}",
                        when (response.code) {
                            401 -> "搜索认证失败：DeepSeek API Key 无效"
                            402 -> "搜索失败：账号余额不足"
                            403 -> "搜索被安全策略拒绝"
                            429 -> "搜索请求过于频繁，请稍后再试"
                            else -> "搜索失败（${response.code}）"
                        },
                    )
                }
                val root = json.parseToJsonElement(text).jsonObject
                val results = root["results"]?.jsonArray ?: return emptyList()
                results.mapNotNull { element ->
                    val obj = element.jsonObject
                    val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    SearchResult(
                        title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        url = url,
                        content = (obj["summary"]?.jsonPrimitive?.contentOrNull
                            ?: obj["snippet"]?.jsonPrimitive?.contentOrNull
                            ?: obj["content"]?.jsonPrimitive?.contentOrNull
                            ?: "").take(800),
                    )
                }
            }
        } catch (e: ModelException) {
            throw e
        } catch (e: Exception) {
            throw ModelException("search_failed", "搜索失败，请检查网络", e)
        }
    }

    private fun tavilySearch(
        config: SearchConfig,
        query: String,
        maxResults: Int,
    ): List<SearchResult> {
        val body = buildJsonObject {
            put("api_key", config.apiKey)
            put("query", query)
            put("max_results", maxResults)
            put("search_depth", "basic")
            put("include_answer", false)
        }
        val request = Request.Builder()
            .url(config.endpoint)
            .addHeader("Content-Type", "application/json")
            .post(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), body).toRequestBody(mediaType))
            .build()
        return try {
            HttpClientProvider.client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ModelException("search_http_${response.code}", "搜索服务请求失败（${response.code}）")
                }
                val root = json.parseToJsonElement(text).jsonObject
                val results = root["results"]?.jsonArray ?: return emptyList()
                results.mapNotNull { element ->
                    val obj = element.jsonObject
                    val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    SearchResult(
                        title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        url = url,
                        content = obj["content"]?.jsonPrimitive?.contentOrNull.orEmpty().take(800),
                    )
                }
            }
        } catch (e: ModelException) {
            throw e
        } catch (e: Exception) {
            throw ModelException("search_failed", "搜索失败，请检查网络与搜索配置", e)
        }
    }

    /** 把搜索结果整理成可注入提示词的文本。 */
    fun formatForPrompt(results: List<SearchResult>): String = buildString {
        append("以下是本次联网搜索到的参考资料，请结合它们回答，并在末尾列出参考来源网址：\n")
        results.forEachIndexed { index, result ->
            append("${index + 1}. ${result.title}\n")
            append("   网址：${result.url}\n")
            if (result.content.isNotBlank()) append("   摘要：${result.content}\n")
        }
    }

    /** 来源保存为 JSON（标题 + 网址）。 */
    fun sourcesJson(results: List<SearchResult>): String = buildJsonArray {
        results.forEach { result ->
            addJsonObject {
                put("title", result.title)
                put("url", result.url)
            }
        }
    }.toString()
}
