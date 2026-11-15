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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import com.tongzhouweiji.baichuan.R
import com.tongzhouweiji.baichuan.chat.BranchInfo
import com.tongzhouweiji.baichuan.chat.ChatViewModel
import com.tongzhouweiji.baichuan.chat.ComposerState
import com.tongzhouweiji.baichuan.chat.PendingAttachment
import com.tongzhouweiji.baichuan.core.db.entity.AttachmentEntity
import com.tongzhouweiji.baichuan.core.db.entity.MessageEntity
import com.tongzhouweiji.baichuan.core.media.AttachmentStore
import com.tongzhouweiji.baichuan.core.media.TtsPlayer
import com.tongzhouweiji.baichuan.core.tts.TtsSpeakResult
import com.tongzhouweiji.baichuan.ui.component.MarkdownText
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
    val listState = rememberLazyListState()

    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    val scope = rememberCoroutineScope()
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

    // 仅当用户接近底部时跟随流式内容；上滑阅读时不打断。
    val nearBottom by remember {
        androidx.compose.runtime.derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(state.activePath.size) {
        if (state.activePath.isNotEmpty()) {
            listState.animateScrollToItem(state.activePath.lastIndex)
        }
    }
    LaunchedEffect(state.streamingContent.length) {
        if (nearBottom && state.activePath.isNotEmpty()) {
            listState.animateScrollToItem(state.activePath.lastIndex)
        }
    }

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(state.globalError) {
        val message = state.globalError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearError()
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
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
                composerState = state.composerState,
                notice = state.paramNotice,
                onDismissNotice = viewModel::clearParamNotice,
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
                        reasoningMs = message.reasoningMs,
                        attachments = state.attachmentsByMessage[message.id].orEmpty(),
                        attachmentStore = app.container.attachmentStore,
                        branch = state.branchInfo[message.id],
                        onRegenerate = { viewModel.regenerate(message.id) },
                        onEdit = { viewModel.editAndResend(message.id, it) },
                        onDelete = { pendingDeleteId = message.id },
                        onSpeak = {
                            scope.launch {
                                val result = runCatching {
                                    app.container.ttsService.speak(message.content)
                                }.getOrElse {
                                    TtsSpeakResult.Failed("云端朗读失败，已切换系统朗读")
                                }
                                when (result) {
                                    TtsSpeakResult.Played -> Unit
                                    is TtsSpeakResult.Unavailable -> {
                                        tts.speak(message.content)
                                        snackbarHostState.showSnackbar("云端朗读未启用：${result.reason}")
                                    }
                                    is TtsSpeakResult.Failed -> {
                                        tts.speak(message.content)
                                        snackbarHostState.showSnackbar(result.userMessage)
                                    }
                                }
                            }
                        },
                        onCycleBranch = { delta -> viewModel.cycleBranch(message.id, delta) },
                    )
                    com.tongzhouweiji.baichuan.ui.component.SourcesRow(
                        sourcesJson = message.sourcesJson,
                        onOpenUrl = { url ->
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(url),
                                    ),
                                )
                            }
                        },
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

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("删除消息") },
            text = { Text("删除这条消息及其后续回复？该分支会回退到上一条消息，其他分支不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(id)
                    pendingDeleteId = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(
    message: MessageEntity,
    reasoningMs: Long?,
    attachments: List<AttachmentEntity>,
    attachmentStore: AttachmentStore,
    branch: BranchInfo?,
    onRegenerate: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit,
    onSpeak: () -> Unit,
    onCycleBranch: (Int) -> Unit,
) {
    val isUser = message.role == "user"
    val context = LocalContext.current
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
            if (!isUser && (message.status == "streaming" || reasoningMs != null)) {
                ReasoningStatus(streaming = message.status == "streaming", durationMs = reasoningMs)
            }
            Box {
                Column(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .background(
                            color = if (isUser) ChatGreen else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .combinedClickable(onClick = {}, onLongClick = { menuExpanded = true })
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    if (message.content.isNotEmpty() || message.status == "streaming") {
                        if (message.status == "streaming" && message.content.isEmpty()) {
                            Text(
                                text = "正在生成…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isUser) Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            MarkdownText(
                                text = message.content,
                                fontSize = 17,
                                lineHeight = 26,
                                color = if (isUser) Color.White
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("复制") }, onClick = {
                        menuExpanded = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("message", message.content))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    })
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
    composerState: ComposerState,
    notice: String?,
    onDismissNotice: () -> Unit,
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
        if (!notice.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = notice,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismissNotice, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭提示", modifier = Modifier.size(14.dp))
                }
            }
        }
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
                placeholder = { Text("发消息") },
                shape = RoundedCornerShape(22.dp),
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { if (composerState != ComposerState.PREPARING) onSend() },
                ),
            )
            Spacer(Modifier.width(6.dp))
            if (composerState == ComposerState.PREPARING) {
                Text(
                    text = "准备中…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                )
            } else if (composerState == ComposerState.STREAMING || composerState == ComposerState.STOP_REQUESTED) {
                TextButton(onClick = onStop) {
                    Text(if (composerState == ComposerState.STOP_REQUESTED) "正在停止…" else "停止")
                }
            } else {
                val canSend = composerState != ComposerState.PREPARING
                IconButton(onClick = onSend, enabled = canSend && (input.isNotBlank() || pending.isNotEmpty())) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = if (input.isNotBlank() || pending.isNotEmpty()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReasoningStatus(streaming: Boolean, durationMs: Long?) {
    val label = when {
        streaming -> "思考中…"
        durationMs != null -> "已思考（用时 ${(durationMs / 1000).coerceAtLeast(1)} 秒）"
        else -> "已思考"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp, start = 2.dp),
    )
}
