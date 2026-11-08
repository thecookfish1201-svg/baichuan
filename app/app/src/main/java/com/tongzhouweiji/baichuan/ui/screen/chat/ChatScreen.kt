package com.tongzhouweiji.baichuan.ui.screen.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tongzhouweiji.baichuan.BaichuanApplication
import com.tongzhouweiji.baichuan.R
import com.tongzhouweiji.baichuan.chat.BranchInfo
import com.tongzhouweiji.baichuan.chat.ChatViewModel
import com.tongzhouweiji.baichuan.chat.PendingAttachment
import com.tongzhouweiji.baichuan.core.db.entity.AttachmentEntity
import com.tongzhouweiji.baichuan.core.db.entity.MessageEntity
import com.tongzhouweiji.baichuan.core.media.AttachmentStore
import com.tongzhouweiji.baichuan.core.media.TtsPlayer
import com.tongzhouweiji.baichuan.ui.theme.ChatGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    agentId: Long,
    onBack: () -> Unit,
    onOpenAgentSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as BaichuanApplication
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory(app, agentId))
    val state by viewModel.state.collectAsState()
    val style by app.container.settingDao.observe("ui_style").collectAsState(initial = null)
    val listState = rememberLazyListState()

    val tts = remember { TtsPlayer(context) }
    DisposableEffect(Unit) {
        onDispose { tts.release() }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) viewModel.addPendingAttachment(uri, "image")
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.addPendingAttachment(uri, "file")
    }

    LaunchedEffect(state.activePath.size, state.streamingContent.length) {
        if (state.activePath.isNotEmpty()) {
            listState.animateScrollToItem(state.activePath.lastIndex)
        }
    }

    if (style == null) {
        // 设置尚未读取完成时先留空，避免先闪一帧微信式再切换。
        Box(Modifier.fillMaxSize())
        return
    }
    if (style == "deepseek") {
        DeepSeekChat(
            state = state,
            attachmentStore = app.container.attachmentStore,
            onBack = onBack,
            onInputChange = viewModel::updateInput,
            onSend = viewModel::send,
            onStop = viewModel::stopGenerating,
            onPickImage = {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onPickFile = { filePicker.launch(arrayOf("*/*")) },
            onRemoveAttachment = viewModel::removePendingAttachment,
            onRegenerate = viewModel::regenerate,
            onCopy = { text ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
            },
            onSpeak = { text -> tts.speak(text) },
            onToggleDeepThinking = viewModel::toggleDeepThinking,
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.agent?.name ?: "对话") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAgentSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "智能体设置")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            ChatInputBar(
                input = state.input,
                pending = state.pendingAttachments,
                isStreaming = state.isStreaming,
                deepSeekStyle = style == "deepseek",
                onInputChange = viewModel::updateInput,
                onSend = viewModel::send,
                onStop = viewModel::stopGenerating,
                onPickImage = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onPickFile = { filePicker.launch(arrayOf("*/*")) },
                onRemoveAttachment = viewModel::removePendingAttachment,
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.activePath, key = { it.id }) { message ->
                    val display = if (message.id == state.streamingMessageId) {
                        message.copy(content = state.streamingContent, status = "streaming")
                    } else {
                        message
                    }
                    Column(Modifier.fillMaxWidth()) {
                    MessageItem(
                        message = display,
                        reasoning = if (message.id == state.streamingMessageId) state.streamingReasoning else message.reasoningContent,
                        reasoningMs = message.reasoningMs,
                        attachments = state.attachmentsByMessage[message.id].orEmpty(),
                        attachmentStore = app.container.attachmentStore,
                        deepSeekStyle = style == "deepseek",
                        branch = state.branchInfo[message.id],
                        onRegenerate = { viewModel.regenerate(message.id) },
                        onEdit = { viewModel.editAndResend(message.id, it) },
                        onDelete = { viewModel.deleteMessage(message.id) },
                        onSpeak = { tts.speak(message.content) },
                        onCycleBranch = { delta -> viewModel.cycleBranch(message.id, delta) },
                    )
                    }
                }
                if (state.activePath.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "开始和${state.agent?.name ?: "它"}聊天吧",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } }
                }
            }
        }
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(
    message: MessageEntity,
    reasoning: String?,
    reasoningMs: Long?,
    attachments: List<AttachmentEntity>,
    attachmentStore: AttachmentStore,
    deepSeekStyle: Boolean,
    branch: BranchInfo?,
    onRegenerate: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit,
    onSpeak: () -> Unit,
    onCycleBranch: (Int) -> Unit,
) {
    val isUser = message.role == "user"
    var menuExpanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser && message.status == "failed") {
            Icon(
                painter = painterResource(R.drawable.ic_error_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp).padding(top = 10.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            if (!isUser && !reasoning.isNullOrBlank()) {
                ReasoningPanel(reasoning = reasoning, durationMs = reasoningMs, streaming = message.status == "streaming")
            }
            Box {
                Column(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .background(
                            color = when {
                                isUser && deepSeekStyle -> MaterialTheme.colorScheme.surfaceVariant
                                isUser -> ChatGreen
                                deepSeekStyle -> Color.Transparent
                                else -> MaterialTheme.colorScheme.surface
                            },
                            shape = if (deepSeekStyle) RoundedCornerShape(14.dp) else RoundedCornerShape(10.dp),
                        )
                        .combinedClickable(onClick = {}, onLongClick = { menuExpanded = true })
                        .padding(
                            horizontal = if (deepSeekStyle && !isUser) 4.dp else 12.dp,
                            vertical = if (deepSeekStyle && !isUser) 2.dp else 9.dp,
                        ),
                ) {
                    if (message.content.isNotEmpty() || message.status == "streaming") {
                        Text(
                            text = if (message.status == "streaming" && message.content.isEmpty()) "正在生成…" else message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isUser && !deepSeekStyle) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (isUser) {
                        DropdownMenuItem(text = { Text("编辑并重发") }, onClick = {
                            menuExpanded = false
                            editing = true
                        })
                    } else {
                        DropdownMenuItem(text = { Text("重新生成") }, onClick = {
                            menuExpanded = false
                            onRegenerate()
                        })
                        DropdownMenuItem(text = { Text("朗读") }, onClick = {
                            menuExpanded = false
                            onSpeak()
                        })
                    }
                    DropdownMenuItem(text = { Text("删除") }, onClick = {
                        menuExpanded = false
                        onDelete()
                    })
                }
            }
            attachments.forEach { attachment ->
                Box(Modifier.padding(top = 4.dp)) {
                    AttachmentPreview(attachment = attachment, store = attachmentStore)
                }
            }
            if (!isUser && deepSeekStyle && message.status != "streaming") {
                val context = LocalContext.current
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("message", message.content))
                            android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) { Text("复制", style = MaterialTheme.typography.labelSmall) }
                    TextButton(
                        onClick = onRegenerate,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) { Text("重新生成", style = MaterialTheme.typography.labelSmall) }
                }
            }
            if (message.status == "failed" && !message.errorSummary.isNullOrBlank()) {
                Text(
                    text = message.errorSummary.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
            if (branch != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        text = "‹",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .combinedClickable(onClick = { onCycleBranch(-1) }, onLongClick = {}),
                    )
                    Text(
                        text = " ${branch.index}/${branch.total} ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .combinedClickable(onClick = { onCycleBranch(1) }, onLongClick = {}),
                    )
                }
            }
        }
    }

    if (editing) {
        var text by remember { mutableStateOf(message.content) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("编辑消息") },
            text = { TextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    editing = false
                    onEdit(text)
                }) { Text("发送") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun AttachmentPreview(attachment: AttachmentEntity, store: AttachmentStore) {
    if (attachment.type == "image") {
        val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, attachment.id) {
            value = withContext(Dispatchers.IO) {
                val file = store.fileOf(attachment.uri)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            }
        }
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 180.dp, height = 180.dp),
            )
        } else {
            Text(
                text = "[图片] ${attachment.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Text(
            text = "[文件] ${attachment.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatInputBar(
    input: String,
    pending: List<PendingAttachment>,
    isStreaming: Boolean,
    deepSeekStyle: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onRemoveAttachment: (Int) -> Unit,
) {
    var attachMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
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
                            modifier = Modifier.widthIn(max = 120.dp),
                        )
                        IconButton(onClick = { onRemoveAttachment(index) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Box {
                IconButton(onClick = { attachMenu = true }) {
                    Icon(Icons.Default.Add, contentDescription = "附件")
                }
                DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                    DropdownMenuItem(text = { Text("图片") }, onClick = {
                        attachMenu = false
                        onPickImage()
                    })
                    DropdownMenuItem(text = { Text("文件") }, onClick = {
                        attachMenu = false
                        onPickFile()
                    })
                }
            }
            TextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("发消息或按住说话") },
                shape = RoundedCornerShape(22.dp),
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!isStreaming) onSend() }),
            )
            Spacer(Modifier.width(6.dp))
            if (isStreaming) {
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
        if (deepSeekStyle) {
            val context = LocalContext.current
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillButton(
                    icon = Icons.Default.Star,
                    label = "深度思考",
                    onClick = { Toast.makeText(context, "深度思考将在阶段三接入", Toast.LENGTH_SHORT).show() },
                )
                PillButton(
                    icon = Icons.Default.Search,
                    label = "智能搜索",
                    onClick = { Toast.makeText(context, "智能搜索将在阶段三接入", Toast.LENGTH_SHORT).show() },
                )
            }
            Text(
                text = "回答由 AI 生成，仅供参考",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun PillButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ReasoningPanel(reasoning: String, durationMs: Long?, streaming: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(bottom = 4.dp)) {
        Row(
            modifier = Modifier.clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    streaming -> "思考中…"
                    durationMs != null -> "已思考（用时 ${(durationMs / 1000).coerceAtLeast(1)} 秒）"
                    else -> "已思考"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        if (expanded) {
            Row(Modifier.padding(top = 6.dp)) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(IntrinsicSize.Max)
                        .background(MaterialTheme.colorScheme.outline),
                )
                Text(
                    text = reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}
