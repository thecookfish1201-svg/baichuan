package com.tongzhouweiji.baichuan.core.tts

import android.content.Context
import android.media.MediaPlayer
import com.tongzhouweiji.baichuan.core.db.BaichuanDatabase
import com.tongzhouweiji.baichuan.core.log.Logger
import com.tongzhouweiji.baichuan.core.security.SecretStore
import java.io.File

/**
 * 朗读服务：优先使用 DeepSeek 云端 TTS（需配置 DeepSeek 服务商），失败时由界面回退系统 TTS。
 * 设置项：tts_provider（deepseek 开启）、tts_voice（stella / tide）。
 */
class TtsService(
    private val context: Context,
    private val db: BaichuanDatabase,
) {

    private var player: MediaPlayer? = null
    private var currentFile: File? = null

    suspend fun speak(text: String): Boolean {
        if (db.settingDao().get("tts_provider") != "deepseek") return false
        val provider = db.providerDao().listAll()
            .firstOrNull { it.baseUrl.contains("deepseek", ignoreCase = true) } ?: return false
        val apiKey = SecretStore.decrypt(provider.keyRef.orEmpty())
        if (apiKey.isBlank()) return false
        val voice = db.settingDao().get("tts_voice")?.takeIf { it in setOf("stella", "tide") } ?: "stella"
        val bytes = runCatching { DeepSeekTts.synthesize(apiKey, text, voice) }
            .onFailure { Logger.w("云端 TTS 合成失败，将回退系统 TTS", it) }
            .getOrNull() ?: return false
        playWav(bytes)
        return true
    }

    private fun playWav(bytes: ByteArray) {
        stop()
        val file = File(context.cacheDir, "tts_${System.currentTimeMillis()}.wav")
        file.writeBytes(bytes)
        currentFile = file
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener { stop() }
            }
        } catch (e: Exception) {
            Logger.w("音频播放失败", e)
            stop()
        }
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
