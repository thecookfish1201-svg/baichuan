package com.tongzhouweiji.baichuan.core.tts

import com.tongzhouweiji.baichuan.core.net.HttpClientProvider
import com.tongzhouweiji.baichuan.model.ModelException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * DeepSeek 语音合成（/v1/tools/tts）：整段合成，返回 wav 音频。
 * 音色：stella（女声，默认）、tide（男声）。文本上限 10,000 计费字符。
 */
object DeepSeekTts {

    private const val ENDPOINT = "https://api.deepseek.com/v1/tools/tts"
    private const val MAX_CHARS = 10000
    private val mediaType = "application/json".toMediaType()

    suspend fun synthesize(
        apiKey: String,
        text: String,
        voiceId: String = "stella",
    ): ByteArray = withContext(Dispatchers.IO) {
        val clipped = text.take(MAX_CHARS)
        require(clipped.isNotBlank()) { "没有可朗读的文本" }
        val body = buildJsonObject {
            put("text", clipped)
            put("voice_id", voiceId)
            put("format", "wav")
            put("filter_markdown", true)
        }
        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(mediaType))
            .build()
        try {
            HttpClientProvider.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val error = response.body?.string().orEmpty().take(200)
                    throw ModelException("tts_${response.code}", "语音合成失败（${response.code}）：$error")
                }
                val bytes = response.body?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    throw ModelException("tts_empty", "语音合成没有返回音频")
                }
                bytes
            }
        } catch (e: ModelException) {
            throw e
        } catch (e: Exception) {
            throw ModelException("tts_failed", "语音合成失败，请检查网络与 API Key", e)
        }
    }
}
