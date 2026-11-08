package com.tongzhouweiji.baichuan.core.log

import android.util.Log

/**
 * 统一日志入口。所有输出经过脱敏，禁止出现 API Key、密码、Token。
 * 错误详情落库前也必须调用 [redact]。
 */
object Logger {
    private const val TAG = "Baichuan"

    private val secretPatterns = listOf(
        Regex("""sk-[A-Za-z0-9_\-]{8,}"""),
        Regex("""(?i)(api[_-]?key\s*[:=]\s*)(\S+)"""),
        Regex("""(?i)(authorization\s*[:=]\s*bearer\s+)(\S+)"""),
    )

    fun d(message: String) = Log.d(TAG, redact(message))

    fun i(message: String) = Log.i(TAG, redact(message))

    fun w(message: String, tr: Throwable? = null) = Log.w(TAG, redact(message), tr)

    fun e(message: String, tr: Throwable? = null) = Log.e(TAG, redact(message), tr)

    fun redact(input: String): String {
        var out = input
        secretPatterns.forEachIndexed { index, regex ->
            out = out.replace(regex) { match ->
                when (index) {
                    0 -> "sk-****"
                    else -> match.groupValues[1] + "****"
                }
            }
        }
        return out
    }
}
