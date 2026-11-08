package com.tongzhouweiji.baichuan.model

/**
 * 推理强度。取值与 DeepSeek / OpenAI 官方对齐：
 * DeepSeek: none / low / high / max；OpenAI: minimal / low / medium / high。
 * medium 在 DeepSeek 侧等价于 high，因此 UI 不再提供「中」档，避免切换无感。
 */
enum class ReasoningEffort(val key: String, val label: String) {
    AUTO("auto", "跟随默认"),
    OFF("off", "关闭"),
    LOW("low", "低"),
    HIGH("high", "高"),
    MAX("max", "最高");

    val isThinking: Boolean
        get() = this == LOW || this == HIGH || this == MAX

    companion object {
        fun fromKey(key: String?): ReasoningEffort = when (key?.lowercase()) {
            "off", "none", "disabled" -> OFF
            "low", "minimal" -> LOW
            "high" -> HIGH
            "max", "xhigh", "ultra" -> MAX
            "medium" -> HIGH
            else -> AUTO
        }
    }
}
