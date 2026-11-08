package com.tongzhouweiji.baichuan.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 轻量 Markdown 渲染：代码块、行内代码、粗体、标题、列表。
 * 不引入大型渲染引擎，覆盖聊天回复的常见格式即可。
 */
@Composable
fun MarkdownText(
    text: String,
    fontSize: Int = 17,
    lineHeight: Int = 26,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val blocks = remember(text) { parseMarkdown(text) }
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
                    Text(
                        text = block.language,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = block.content,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (fontSize - 2).sp,
                        lineHeight = (lineHeight - 2).sp,
                        color = color,
                    )
                }

                is Block.Heading -> Text(
                    text = buildInline(block.text),
                    fontSize = (fontSize + if (block.level == 1) 4 else 2).sp,
                    lineHeight = (lineHeight + 4).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    modifier = Modifier.padding(vertical = 2.dp),
                )

                is Block.ListItem -> Row(Modifier.padding(vertical = 1.dp)) {
                    Text(text = "•", fontSize = fontSize.sp, color = color, modifier = Modifier.width(16.dp))
                    Text(text = buildInline(block.text), fontSize = fontSize.sp, lineHeight = lineHeight.sp, color = color)
                }

                is Block.Paragraph -> Text(
                    text = buildInline(block.text),
                    fontSize = fontSize.sp,
                    lineHeight = lineHeight.sp,
                    color = color,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}

private sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Heading(val level: Int, val text: String) : Block
    data class ListItem(val text: String) : Block
    data class Code(val language: String, val content: String) : Block
}

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

/** 行内格式：**粗体**、*斜体*、`代码`。 */
private fun buildInline(text: String): AnnotatedString = buildAnnotatedString {
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
                    append(text[index])
                    index++
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
