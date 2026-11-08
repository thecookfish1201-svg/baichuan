package com.tongzhouweiji.baichuan.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tongzhouweiji.baichuan.chat.ChatUiState
import com.tongzhouweiji.baichuan.chat.PendingAttachment
import com.tongzhouweiji.baichuan.core.db.entity.AttachmentEntity
import com.tongzhouweiji.baichuan.core.db.entity.MessageEntity
import com.tongzhouweiji.baichuan.core.media.AttachmentStore
import com.tongzhouweiji.baichuan.ui.component.AttachmentPreview
import com.tongzhouweiji.baichuan.ui.component.MarkdownText
import com.tongzhouweiji.baichuan.ui.theme.DSLine
import com.tongzhouweiji.baichuan.ui.theme.DSLineDark
import com.tongzhouweiji.baichuan.ui.theme.DSPillBg
import com.tongzhouweiji.baichuan.ui.theme.DSPillText
import com.tongzhouweiji.baichuan.ui.theme.DSReasoningGray
import com.tongzhouweiji.baichuan.ui.theme.DSUserBubbleDark
import com.tongzhouweiji.baichuan.ui.theme.DSUserBubbleLight
import androidx.compose.foundation.isSystemInDarkTheme

/**
 * DeepSeek 风格聊天页。
 * 结构学官方：无气泡壳正文 + 思考轨 + 操作栏 + 大圆角输入卡。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepSeekChat(
    state: ChatUiState,
    attachmentStore: AttachmentStore,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onRegenerate: (Long) -> Unit,
    onCopy: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onToggleDeepThinking: () -> Unit,
    onToggleSearch: () -> Unit = {},
    onOpenAgentSettings: () -> Unit = {},
) {
    val dark = isSystemInDarkTheme()
    val listState = rememberLazyListState()

    LaunchedEffect(state.activePath.size, state.streamingContent.length, state.streamingReasoning.length) {
        if (state.activePath.isNotEmpty()) {
            listState.animateScrollToItem(state.activePath.lastIndex)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.agent?.name ?: "百川",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        state.modelLabel?.let { model ->
                            Text(
                                text = model,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAgentSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "智能体设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.activePath.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "有什么可以帮您",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "输入问题，或从「+」添加图片与文件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(state.activePath, key = { it.id }) { message ->
                        val streaming = message.id == state.streamingMessageId
                        val display = if (streaming) {
                            message.copy(content = state.streamingContent, status = "streaming")
                        } else {
                            message
                        }
                        DsMessageItem(
                            message = display,
                            reasoning = if (streaming) state.streamingReasoning else message.reasoningContent,
                            reasoningMs = message.reasoningMs,
                            attachments = state.attachmentsByMessage[message.id].orEmpty(),
                            store = attachmentStore,
                            streaming = streaming,
                            dark = dark,
                            onRegenerate = { onRegenerate(message.id) },
                            onCopy = { onCopy(message.content) },
                            onSpeak = { onSpeak(message.content) },
                        )
                    }
                }
            }
            if (state.paramNotice != null) {
                Text(
                    text = state.paramNotice.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            DsInputCard(
                input = state.input,
                pending = state.pendingAttachments,
                streaming = state.isStreaming,
                deepThinking = state.sessionReasoning?.isThinking == true,
                searchEnabled = state.sessionSearch,
                modelLabel = state.modelLabel,
                onInputChange = onInputChange,
                onSend = onSend,
                onStop = onStop,
                onPickImage = onPickImage,
                onPickFile = onPickFile,
                onRemoveAttachment = onRemoveAttachment,
                onToggleDeepThinking = onToggleDeepThinking,
                onToggleSearch = onToggleSearch,
            )
        }
    }
}

@Composable
private fun DsMessageItem(
    message: MessageEntity,
    reasoning: String?,
    reasoningMs: Long?,
    attachments: List<AttachmentEntity>,
    store: AttachmentStore,
    streaming: Boolean,
    dark: Boolean,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
) {
    val isUser = message.role == "user"
    if (isUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .background(
                            if (dark) DSUserBubbleDark else DSUserBubbleLight,
                            RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = message.content,
                        fontSize = 17.sp,
                        lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                attachments.forEach { attachment ->
                    Box(Modifier.padding(top = 4.dp)) {
                        AttachmentPreview(attachment = attachment, store = store)
                    }
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        if (!reasoning.isNullOrBlank()) {
            DsThinkingPanel(
                reasoning = reasoning,
                durationMs = reasoningMs,
                streaming = streaming,
                dark = dark,
            )
        }
        if (message.content.isNotEmpty()) {
            Box(Modifier.padding(top = if (reasoning.isNullOrBlank()) 0.dp else 8.dp)) {
                MarkdownText(
                    text = message.content,
                    fontSize = 17,
                    lineHeight = 26,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (streaming) {
            Text(
                text = "生成中…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row {
                    TextButton(onClick = onCopy, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("复制", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onSpeak, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("朗读", style = MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(onClick = onRegenerate, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text("重新生成", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (message.status == "failed" && !message.errorSummary.isNullOrBlank()) {
            Text(
                text = message.errorSummary.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun DsThinkingPanel(
    reasoning: String,
    durationMs: Long?,
    streaming: Boolean,
    dark: Boolean,
) {
    var expanded by remember(reasoning.length > 0) { mutableStateOf(false) }
    val gray = if (dark) Color(0xFFA0A0A0) else DSReasoningGray
    val lineColor = if (dark) DSLineDark else DSLine
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    streaming -> "深度思考中"
                    durationMs != null -> "已深度思考（用时 ${(durationMs / 1000).coerceAtLeast(1)} 秒）"
                    else -> "已深度思考"
                },
                fontSize = 17.sp,
                color = gray,
            )
            Icon(
                imageVector = if (expanded || streaming) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = gray,
                modifier = Modifier.size(20.dp).padding(start = 2.dp),
            )
        }
        if (expanded || streaming) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Column(Modifier.width(8.dp).fillMaxHeight()) {
                    Box(
                        Modifier
                            .size(4.dp)
                            .background(gray, CircleShape)
                            .align(Alignment.CenterHorizontally),
                    )
                    Box(
                        Modifier
                            .width(2.dp)
                            .weight(1f)
                            .background(lineColor)
                            .align(Alignment.CenterHorizontally),
                    )
                }
                Text(
                    text = reasoning,
                    fontSize = 15.sp,
                    lineHeight = 26.sp,
                    color = gray,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun DsInputCard(
    input: String,
    pending: List<PendingAttachment>,
    streaming: Boolean,
    deepThinking: Boolean,
    searchEnabled: Boolean,
    modelLabel: String?,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onToggleDeepThinking: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    var attachMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(26.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (pending.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    pending.forEachIndexed { index, attachment ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                        ) {
                            Text(
                                text = (if (attachment.type == "image") "[图] " else "[文件] ") + attachment.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 100.dp),
                            )
                            IconButton(onClick = { onRemoveAttachment(index) }, modifier = Modifier.size(22.dp)) {
                                Text("×", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                TextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("发消息或按住说话", style = MaterialTheme.typography.bodyMedium) },
                    maxLines = 5,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (!streaming) onSend() }),
                )
                Box {
                    IconButton(onClick = { attachMenu = true }) {
                        Icon(Icons.Default.Add, contentDescription = "附件")
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = attachMenu,
                        onDismissRequest = { attachMenu = false },
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("图片") },
                            onClick = {
                                attachMenu = false
                                onPickImage()
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("文件") },
                            onClick = {
                                attachMenu = false
                                onPickFile()
                            },
                        )
                    }
                }
                if (streaming) {
                    TextButton(onClick = onStop) { Text("停止") }
                } else {
                    IconButton(onClick = onSend, enabled = input.isNotBlank() || pending.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = if (input.isNotBlank() || pending.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DsPill(
                    label = "深度思考",
                    active = deepThinking,
                    onClick = onToggleDeepThinking,
                )
                Spacer(Modifier.width(8.dp))
                DsPill(
                    label = "智能搜索",
                    active = searchEnabled,
                    onClick = onToggleSearch,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = modelLabel ?: "默认模型",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "回答由 AI 生成，仅供参考",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun DsPill(label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(
                if (active) DSPillText else DSPillBg,
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = if (active) Color.White else DSPillText,
        )
    }
}
