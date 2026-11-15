package com.tongzhouweiji.baichuan.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 轻量 Markdown 渲染：代码块、行内代码、粗体、斜体、标题、有序/无序列表、引用、链接。
 * 不引入大型渲染引擎，覆盖聊天回复的常见格式。
 *
 * 链接只在提供 [onUrlClick] 时可点击；流式未闭合的 ** 标记会被去掉而不是裸奔。
 */
@Composable
fun MarkdownText(
    text: String,
    fontSize: Int = 17,
    lineHeight: Int = 26,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onUrlClick: ((String) -> Unit)? = null,
) {
    val blocks = remember(text) { parseMarkdown(text) }
    val linkColor = MaterialTheme.colorScheme.primary
    Column {
        blocks.forEach { block ->
            when (block) {
                is Block.Code -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    if (block.language.isNotBlank()) {
                        Text(
                            text = block.language,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        Text(
                            text = block.content,
                            fontFamily = FontFamily.Monospace,
                            fontSize = (fontSize - 2).sp,
                            lineHeight = (lineHeight - 2).sp,
                            color = color,
                            softWrap = false,
                        )
                    }
                }

                is Block.Heading -> Text(
                    text = buildInline(block.text, linkColor),
                    fontSize = (fontSize + if (block.level == 1) 4 else 2).sp,
                    lineHeight = (lineHeight + 4).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    modifier = Modifier.padding(vertical = 2.dp),
                )

                is Block.ListItem -> Row(Modifier.padding(vertical = 1.dp)) {
                    Text(
                        text = "•",
                        fontSize = fontSize.sp,
                        color = color,
                        modifier = Modifier.width(16.dp),
                    )
                    InlineText(
                        annotated = buildInline(block.text, linkColor),
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        color = color,
                        onUrlClick = onUrlClick,
                    )
                }

                is Block.OrderedItem -> Row(Modifier.padding(vertical = 1.dp)) {
                    Text(
                        text = "${block.index}.",
                        fontSize = fontSize.sp,
                        lineHeight = lineHeight.sp,
                        color = color,
                        modifier = Modifier.width(24.dp),
                    )
                    InlineText(
                        annotated = buildInline(block.text, linkColor),
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        color = color,
                        onUrlClick = onUrlClick,
                    )
                }

                is Block.Quote -> Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                ) {
                    Text(
                        text = "▍",
                        fontSize = (fontSize - 1).sp,
                        lineHeight = (lineHeight - 1).sp,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = block.text,
                        fontSize = (fontSize - 1).sp,
                        lineHeight = (lineHeight - 1).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                is Block.Paragraph -> {
                    val annotated = buildInline(block.text, linkColor)
                    InlineText(
                        annotated = annotated,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        color = color,
                        onUrlClick = onUrlClick,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineText(
    annotated: AnnotatedString,
    fontSize: Int,
    lineHeight: Int,
    color: Color,
    onUrlClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val style = TextStyle(
        fontSize = fontSize.sp,
        lineHeight = lineHeight.sp,
        color = color,
    )
    if (onUrlClick == null) {
        Text(text = annotated, style = style, modifier = modifier)
    } else {
        ClickableText(
            text = annotated,
            style = style,
            modifier = modifier,
            onClick = { offset ->
                annotated.getStringAnnotations("URL", offset, offset)
                    .firstOrNull()
                    ?.let { onUrlClick(it.item) }
            },
        )
    }
}

private sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Heading(val level: Int, val text: String) : Block
    data class ListItem(val text: String) : Block
    data class OrderedItem(val index: Int, val text: String) : Block
    data class Quote(val text: String) : Block
    data class Code(val language: String, val content: String) : Block
}

private val orderedPattern = Regex("""^(\d+)\.\s+(.*)$""")

private fun parseMarkdown(text: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val lines = text.replace("\r\n", "\n").split('\n')
    var i = 0
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) {
            blocks.add(Block.Paragraph(paragraph.toString().trim()))
            paragraph.clear()
        }
    }

    while (i < lines.size) {
        val line = lines[i]
        when {
            line.trimStart().startsWith("```") -> {
                flushParagraph()
                val language = line.trim().removePrefix("```").trim()
                val content = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    if (content.isNotEmpty()) content.append('\n')
                    content.append(lines[i])
                    i++
                }
                blocks.add(Block.Code(language = language, content = content.toString()))
                i++
            }

            line.startsWith("#") -> {
                flushParagraph()
                val level = line.takeWhile { it == '#' }.length.coerceIn(1, 3)
                blocks.add(Block.Heading(level = level, text = line.removePrefix("#".repeat(level)).trim()))
                i++
            }

            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                flushParagraph()
                blocks.add(Block.ListItem(line.trimStart().substring(2).trim()))
                i++
            }

            orderedPattern.matches(line.trimStart()) -> {
                flushParagraph()
                val match = orderedPattern.matchEntire(line.trimStart())!!
                blocks.add(Block.OrderedItem(index = match.groupValues[1].toIntOrNull() ?: 1, text = match.groupValues[2].trim()))
                i++
            }

            line.trimStart().startsWith("> ") -> {
                flushParagraph()
                val quote = StringBuilder()
                while (i < lines.size && lines[i].trimStart().startsWith("> ")) {
                    if (quote.isNotEmpty()) quote.append('\n')
                    quote.append(lines[i].trimStart().removePrefix("> ").trim())
                    i++
                }
                blocks.add(Block.Quote(quote.toString()))
            }

            line.isBlank() -> {
                flushParagraph()
                i++
            }

            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
                i++
            }
        }
    }
    flushParagraph()
    return blocks
}

/** 行内格式：**粗体**、*斜体*、`代码`、[链接](url)、裸 URL。 */
private fun buildInline(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", index + 2)
                if (end > index) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(text.substring(index + 2, end))
                    }
                    index = end + 2
                } else {
                    // 流式未闭合：去掉标记符号，剩余文本按普通样式显示，避免 ** 裸露。
                    append(text.substring(index + 2))
                    index = text.length
                }
            }

            text[index] == '`' -> {
                val end = text.indexOf('`', index + 1)
                if (end > index) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x1F000000),
                        ),
                    ) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(text[index])
                    index++
                }
            }

            text[index] == '[' -> {
                val close = text.indexOf(']', index + 1)
                val open = if (close > index && close + 1 < text.length && text[close + 1] == '(') {
                    text.indexOf(')', close + 2)
                } else {
                    -1
                }
                if (close > index && open > close) {
                    val label = text.substring(index + 1, close)
                    val url = text.substring(close + 2, open)
                    pushStringAnnotation("URL", url)
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                        append(label)
                    }
                    pop()
                    index = open + 1
                } else {
                    append(text[index])
                    index++
                }
            }

            text.startsWith("http://", index) || text.startsWith("https://", index) -> {
                var end = index
                while (end < text.length && !text[end].isWhitespace()) end++
                val url = text.substring(index, end)
                pushStringAnnotation("URL", url)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(url)
                }
                pop()
                index = end
            }

            text[index] == '*' && index + 1 < text.length -> {
                val end = text.indexOf('*', index + 1)
                if (end > index + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(text[index])
                    index++
                }
            }

            else -> {
                append(text[index])
                index++
            }
        }
    }
}
