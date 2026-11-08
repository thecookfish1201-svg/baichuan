package com.tongzhouweiji.baichuan.core.media

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 系统 TTS 播放器。只做「朗读回复」一项能力，不涉及语音输入。
 * 云端 TTS 适配在后续版本接入，未配置时回退到系统 TTS。
 */
class TtsPlayer(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.SIMPLIFIED_CHINESE
                pending?.let { speak(it) }
                pending = null
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) {
            pending = text
            return
        }
        tts?.stop()
        tts?.speak(text.take(4000), TextToSpeech.QUEUE_FLUSH, null, "baichuan-tts")
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        pending = null
    }
}
