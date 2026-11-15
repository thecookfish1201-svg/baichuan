package com.tongzhouweiji.baichuan.core.tts

import android.content.Context
import android.media.MediaPlayer
import com.tongzhouweiji.baichuan.core.db.BaichuanDatabase
import com.tongzhouweiji.baichuan.core.db.entity.ProviderEntity
import com.tongzhouweiji.baichuan.core.log.Logger
import com.tongzhouweiji.baichuan.core.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/** 云端朗读是否具备最小可用配置。 */
enum class TtsAvailability {
    /** 用户未开启云端朗读。 */
    Disabled,

    /** 没有可用的 DeepSeek 服务商。 */
    NoProvider,

    /** 服务商存在，但 API Key 缺失或无法解密。 */
    NoKey,

    /** 配置完整。 */
    Ready,
}

sealed interface TtsSpeakResult {
    /** 云端音频已开始播放。 */
    data object Played : TtsSpeakResult

    /** 未满足云端朗读条件；调用方应回退系统 TTS，并向用户说明原因。 */
    data class Unavailable(val reason: String) : TtsSpeakResult

    /** 云端合成或播放失败；调用方应回退系统 TTS。 */
    data class Failed(val userMessage: String) : TtsSpeakResult
}

/**
 * 朗读服务：优先使用 DeepSeek 云端 TTS，失败时由界面回退系统 TTS。
 *
 * 与旧实现的区别：
 * - 播放结果真实返回，播放失败不再假装成功；
 * - 播放异步 prepare，避免主线程阻塞；
 * - 未配置/未开启时返回明确原因，界面可提示用户；
 * - 自动修正流式 WAV 头（RIFF/data 长度占位为 0 的情况）。
 */
class TtsService(
    private val context: Context,
    private val db: BaichuanDatabase,
) {

    private var player: MediaPlayer? = null
    private var currentFile: File? = null

    suspend fun availability(): TtsAvailability = withContext(Dispatchers.IO) {
        if (db.settingDao().get("tts_provider") != "deepseek") {
            return@withContext TtsAvailability.Disabled
        }
        val provider = findDeepSeekProvider() ?: return@withContext TtsAvailability.NoProvider
        val key = SecretStore.decrypt(provider.keyRef.orEmpty())
        if (key.isBlank()) return@withContext TtsAvailability.NoKey
        TtsAvailability.Ready
    }

    suspend fun speak(text: String): TtsSpeakResult = withContext(Dispatchers.IO) {
        when (availability()) {
            TtsAvailability.Disabled -> return@withContext TtsSpeakResult.Unavailable("云端朗读未开启")
            TtsAvailability.NoProvider -> return@withContext TtsSpeakResult.Unavailable("未添加 DeepSeek 服务商")
            TtsAvailability.NoKey -> return@withContext TtsSpeakResult.Unavailable("DeepSeek API Key 未配置")
            TtsAvailability.Ready -> Unit
        }
        val provider = findDeepSeekProvider()
            ?: return@withContext TtsSpeakResult.Unavailable("未添加 DeepSeek 服务商")
        val apiKey = SecretStore.decrypt(provider.keyRef.orEmpty())
        if (apiKey.isBlank()) {
            return@withContext TtsSpeakResult.Unavailable("DeepSeek API Key 未配置")
        }
        val voice = db.settingDao().get("tts_voice")?.takeIf { it in setOf("stella", "tide") } ?: "stella"
        val bytes = runCatching { DeepSeekTts.synthesize(apiKey, text, voice) }
            .onFailure { Logger.w("云端 TTS 合成失败", it) }
            .getOrNull() ?: return@withContext TtsSpeakResult.Failed("云端朗读暂时不可用，已切换系统朗读")
        if (playWav(bytes)) {
            TtsSpeakResult.Played
        } else {
            TtsSpeakResult.Failed("云端音频播放失败，已切换系统朗读")
        }
    }

    private suspend fun findDeepSeekProvider(): ProviderEntity? = db.providerDao().listAll()
        .firstOrNull {
            it.baseUrl.contains("deepseek", ignoreCase = true) ||
                it.name.contains("deepseek", ignoreCase = true)
        }

    private suspend fun playWav(raw: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val bytes = DeepSeekTts.fixWavHeader(raw)
        stop()
        val file = File(context.cacheDir, "tts_${System.currentTimeMillis()}.wav")
        runCatching { file.writeBytes(bytes) }.onFailure {
            Logger.w("TTS 临时文件写入失败", it)
            return@withContext false
        }
        currentFile = file
        val played = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { continuation ->
                val mediaPlayer = MediaPlayer()
                player = mediaPlayer
                continuation.invokeOnCancellation {
                    runCatching { mediaPlayer.release() }
                    player = null
                }
                try {
                    mediaPlayer.setDataSource(file.absolutePath)
                    mediaPlayer.setOnPreparedListener { mp ->
                        runCatching { mp.start() }
                            .onSuccess { if (continuation.isActive) continuation.resume(true) }
                            .onFailure {
                                Logger.w("TTS 播放启动失败", it)
                                if (continuation.isActive) continuation.resume(false)
                            }
                    }
                    mediaPlayer.setOnErrorListener { _, what, extra ->
                        Logger.w("TTS 播放错误 what=$what extra=$extra")
                        if (continuation.isActive) continuation.resume(false)
                        true
                    }
                    mediaPlayer.setOnCompletionListener { stop() }
                    mediaPlayer.prepareAsync()
                } catch (e: Exception) {
                    Logger.w("TTS 准备失败", e)
                    if (continuation.isActive) continuation.resume(false)
                }
            }
        } ?: false
        if (!played) stop()
        played
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        currentFile?.delete()
        currentFile = null
    }

    fun release() = stop()
}
