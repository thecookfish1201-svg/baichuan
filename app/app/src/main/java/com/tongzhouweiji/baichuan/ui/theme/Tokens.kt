package com.tongzhouweiji.baichuan.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * UI 风格。三种风格共用同一套页面骨架，差异通过风格枚举切换：
 * 微信式两种共用聊天列表形态，DeepSeek 式默认不显示头像与名称。
 */
enum class UiStyle(val key: String, val label: String) {
    WE_CHAT_FLAT("wechat_flat", "微信式 · 扁平"),
    WE_CHAT_GLASS("wechat_glass", "微信式 · 液态玻璃"),
    DEEPSEEK("deepseek", "DeepSeek 式"),
}

/**
 * 设计令牌：间距、圆角、尺寸。页面不直接写魔法数字，统一从这里取。
 */
object Tokens {
    val PagePadding = 16.dp
    val CardRadius = 10.dp
    val BubbleRadius = 8.dp
    val ListRowHeight = 64.dp
    val DividerThickness = 0.5.dp
    val SectionGap = 10.dp
    val IconSize = 22.dp
    val TabIconSize = 24.dp

    object Type {
        val BubbleSize = 16.sp
        val BubbleLineHeight = 24.sp
        val CaptionSize = 13.sp
    }
}
