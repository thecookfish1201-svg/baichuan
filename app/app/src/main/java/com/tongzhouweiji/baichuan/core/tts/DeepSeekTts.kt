package com.tongzhouweiji.baichuan.core.tts

import com.tongzhouweiji.baichuan.core.log.Logger
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
 * 音色：stella（女声，默认）、tide（男声）。
 *
 * 计费字符：汉字按 2 个字符计算，其余（字母、数字、空格、标点）按 1 个计算，
 * 单次请求上限 10,000 计费字符。不能简单按 Java 字符数截断，否则中文长文本
 * 会被服务端拒绝。
 */
object DeepSeekTts {

    private const val ENDPOINT = "https://api.deepseek.com/v1/tools/tts"
    private const val MAX_BILLING_CHARS = 10000
    private val mediaType = "application/json".toMediaType()

    suspend fun synthesize(
        apiKey: String,
        text: String,
        voiceId: String = "stella",
    ): ByteArray = withContext(Dispatchers.IO) {
        val clipped = clipForBilling(text)
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
                    val error = response.body?.string().orEmpty()
                    throw ModelException(
                        "tts_${response.code}",
                        userMessageFor(response.code),
                        diagnostic = "HTTP ${response.code}\n" + Logger.redact(error.take(2000)),
                    )
                }
                val bytes = response.body?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    throw ModelException("tts_empty", "语音合成没有返回音频，请稍后重试。")
                }
                bytes
            }
        } catch (e: ModelException) {
            throw e
        } catch (e: Exception) {
            throw ModelException(
                "tts_failed",
                "语音合成失败，请检查网络连接后重试。",
                cause = e,
            )
        }
    }

    private fun userMessageFor(code: Int): String = when (code) {
        400 -> "这段文字无法朗读（可能过长或未通过内容审核）。"
        401, 403 -> "语音合成认证失败：请检查 DeepSeek API Key 是否有效。"
        429 -> "语音合成请求过于频繁，请稍后重试。"
        503, 504 -> "语音合成服务繁忙，请稍后重试。"
        in 500..599 -> "语音合成服务暂时不可用，请稍后重试。"
        else -> "语音合成失败，请稍后重试。"
    }

    /** 按计费字符截断：汉字 2，其他 1。 */
    internal fun clipForBilling(text: String, max: Int = MAX_BILLING_CHARS): String {
        val builder = StringBuilder()
        var billing = 0
        for (char in text) {
            val cost = if (isHan(char)) 2 else 1
            if (billing + cost > max) break
            billing += cost
            builder.append(char)
        }
        return builder.toString()
    }

    private fun isHan(char: Char): Boolean = when (Character.UnicodeScript.of(char.code)) {
        Character.UnicodeScript.HAN -> true
        else -> false
    }

    /**
     * 修正流式 WAV 的头部长度字段。
     *
     * DeepSeek TTS 边合成边返回，部分情况下 RIFF/data 的 size 字段仍是占位值
     * （0 或 0xFFFFFFFF），Android MediaPlayer 会直接判定文件损坏。这里按实际
     * 字节数重写；不是 RIFF 或结构异常时原样返回。
     */
    internal fun fixWavHeader(raw: ByteArray): ByteArray {
        if (raw.size < 44) return raw
        if (raw[0] != 'R'.code.toByte() || raw[1] != 'I'.code.toByte() ||
            raw[2] != 'F'.code.toByte() || raw[3] != 'F'.code.toByte()
        ) {
            return raw
        }
        if (raw[8] != 'W'.code.toByte() || raw[9] != 'A'.code.toByte() ||
            raw[10] != 'V'.code.toByte() || raw[11] != 'E'.code.toByte()
        ) {
            return raw
        }
        var index = 12
        while (index + 8 <= raw.size) {
            val chunkId = String(raw, index, 4, Charsets.US_ASCII)
            val chunkSize = readLe32(raw, index + 4)
            if (chunkId == "data") {
                val actual = raw.size - (index + 8)
                if (chunkSize <= 0 || chunkSize > actual || chunkSize != actual) {
                    writeLe32(raw, index + 4, actual)
                    writeLe32(raw, 4, raw.size - 8)
                }
                return raw
            }
            if (chunkSize < 0 || index + 8 + chunkSize > raw.size) return raw
            index += 8 + chunkSize + (chunkSize and 1)
        }
        return raw
    }

    private fun readLe32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun writeLe32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}
