package com.tongzhouweiji.baichuan.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tongzhouweiji.baichuan.BaichuanApplication
import com.tongzhouweiji.baichuan.core.db.dao.AttachmentDao
import com.tongzhouweiji.baichuan.core.db.dao.ErrorEventDao
import com.tongzhouweiji.baichuan.core.db.dao.MemoryDao
import com.tongzhouweiji.baichuan.core.db.dao.SummaryDao
import com.tongzhouweiji.baichuan.core.db.dao.UsageDao
import com.tongzhouweiji.baichuan.core.db.dao.ConversationDao
import com.tongzhouweiji.baichuan.core.db.dao.ProviderDao
import com.tongzhouweiji.baichuan.core.db.entity.AgentEntity
import com.tongzhouweiji.baichuan.core.db.entity.ApiUsageEntity
import com.tongzhouweiji.baichuan.core.db.entity.AttachmentEntity
import com.tongzhouweiji.baichuan.core.db.entity.ConversationSummaryEntity
import com.tongzhouweiji.baichuan.core.db.entity.ErrorEventEntity
import com.tongzhouweiji.baichuan.core.db.entity.MemoryEntity
import com.tongzhouweiji.baichuan.core.db.entity.MessageEntity
import com.tongzhouweiji.baichuan.core.log.Logger
import com.tongzhouweiji.baichuan.core.media.AttachmentStore
import com.tongzhouweiji.baichuan.core.media.StoredAttachment
import com.tongzhouweiji.baichuan.core.security.SecretStore
import com.tongzhouweiji.baichuan.model.ChatMessage
import com.tongzhouweiji.baichuan.model.ChatRequest
import com.tongzhouweiji.baichuan.model.ModelAdapterRegistry
import com.tongzhouweiji.baichuan.model.ModelException
import com.tongzhouweiji.baichuan.model.ProviderConfig
import com.tongzhouweiji.baichuan.model.ReasoningEffort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicLong

data class BranchInfo(
    val index: Int,
    val total: Int,
)

/** 已选中、尚未发送的附件。 */
data class PendingAttachment(
    val uri: Uri,
    val type: String,
    val name: String,
    val mime: String,
)

enum class ComposerState {
    IDLE, PREPARING, STREAMING, STOP_REQUESTED, COMPLETED, FAILED, CANCELLED
}

data class ChatUiState(
    val agent: AgentEntity? = null,
    val conversationId: Long = 0,
    val activePath: List<MessageEntity> = emptyList(),
    val attachmentsByMessage: Map<Long, List<AttachmentEntity>> = emptyMap(),
    val branchInfo: Map<Long, BranchInfo> = emptyMap(),
    val input: String = "",
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val streamingMessageId: Long? = null,
    val streamingContent: String = "",
    val streamingReasoning: String = "",
    val globalError: String? = null,
    val sessionReasoning: ReasoningEffort? = null,
    /** 一次性的参数/能力提示（例如温度不生效、文件不会被读取）。 */
    val paramNotice: String? = null,
    val modelLabel: String? = null,
    val sessionSearch: Boolean = false,
    /** 输入区状态机，唯一的任务状态事实源。 */
    val composerState: ComposerState = ComposerState.IDLE,
) {
    /** 派生值：只读，不允许独立写入。 */
    val isStreaming: Boolean
        get() = composerState == ComposerState.STREAMING ||
            composerState == ComposerState.STOP_REQUESTED
}

private data class GenerationParams(
    val temperature: Double = 0.7,
    val stream: Boolean = true,
    val reasoning: ReasoningEffort = ReasoningEffort.AUTO,
)

class ChatViewModel(
    private val app: BaichuanApplication,
    private val repository: ChatRepository,
    private val providerDao: ProviderDao,
    private val conversationDao: ConversationDao,
    private val attachmentDao: AttachmentDao,
    private val attachmentStore: AttachmentStore,
    private val memoryDao: MemoryDao,
    private val summaryDao: SummaryDao,
    private val errorEventDao: ErrorEventDao,
    private val usageDao: UsageDao,
    private val agentId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var streamJob: Job? = null
    private var prepareJob: Job? = null
    private val lastUiUpdate = AtomicLong(0)
    private val json = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch { bootstrap() }
    }

    private suspend fun bootstrap() {
        val agent = repository.findAgent(agentId)
        _state.update { it.copy(agent = agent) }
        val conversation = repository.ensureConversation(agentId)
        _state.update { it.copy(conversationId = conversation.id) }
        repository.observeMessages(conversation.id).collect { refreshPath() }
    }

    private suspend fun refreshPath() {
        val conversationId = _state.value.conversationId
        if (conversationId == 0L) return
        var conversation = conversationDao.findById(conversationId) ?: return
        var path = repository.pathTo(conversationId, conversation.activeLeafMessageId)
        if (!path.valid) {
            // 历史数据可能留下已删除的活动叶子：修复到最新一条未删除消息后再展示。
            val repaired = repository.repairActiveLeaf(conversationId)
            conversation = conversationDao.findById(conversationId) ?: return
            path = repository.pathTo(conversationId, repaired)
            if (!path.valid) {
                _state.update {
                    it.copy(
                        activePath = emptyList(),
                        branchInfo = emptyMap(),
                        attachmentsByMessage = emptyMap(),
                        globalError = path.reason ?: PromptAssembler.INVALID_PATH_MESSAGE,
                    )
                }
                return
            }
        }
        val all = repository.listMessages(conversationId)
        val branchInfo = mutableMapOf<Long, BranchInfo>()
        path.messages.forEach { message ->
            val siblings = all.filter { it.parentId == message.parentId }.sortedBy { it.siblingSeq }
            if (siblings.size > 1) {
                val index = siblings.indexOfFirst { it.id == message.id } + 1
                branchInfo[message.id] = BranchInfo(index = index, total = siblings.size)
            }
        }
        val attachments = if (path.messages.isEmpty()) emptyMap() else
            attachmentDao.listByMessages(path.messages.map { it.id }).groupBy { it.messageId }
        _state.update {
            it.copy(
                activePath = path.messages,
                branchInfo = branchInfo,
                attachmentsByMessage = attachments,
            )
        }
    }

    fun updateInput(value: String) {
        _state.update { it.copy(input = value) }
    }

    fun addPendingAttachment(uri: Uri, type: String) {
        val name = runCatching {
            app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull() ?: if (type == "image") "image" else "file"
        val mime = app.contentResolver.getType(uri) ?: if (type == "image") "image/jpeg" else "application/octet-stream"
        _state.update {
            it.copy(
                pendingAttachments = it.pendingAttachments + PendingAttachment(uri, type, name, mime),
            )
        }
    }

    fun removePendingAttachment(index: Int) {
        _state.update {
            if (index !in it.pendingAttachments.indices) it
            else it.copy(pendingAttachments = it.pendingAttachments.filterIndexed { i, _ -> i != index })
        }
    }

    fun consumeNotice() {
        _state.update { it.copy(globalError = null) }
    }

    fun send() {
        val text = _state.value.input.trim()
        val pending = _state.value.pendingAttachments
        if (text.isEmpty() && pending.isEmpty()) return
        val conversationId = _state.value.conversationId
        if (conversationId == 0L) return
        if (!canStartGeneration()) return
        val hasFileAttachment = pending.any { it.type != "image" }
        // 在任何挂起/IO 之前同步进入 PREPARING，避免快速连点产生第二个任务。
        beginPreparing()
        prepareJob = viewModelScope.launch {
            try {
                val conversation = conversationDao.findById(conversationId)
                if (conversation == null) {
                    failGeneration("会话不存在，发送失败")
                    return@launch
                }
                val parentId = conversation.activeLeafMessageId
                val stored = mutableListOf<StoredAttachment>()
                var attachmentFailed = false
                withContext(Dispatchers.IO) {
                    pending.forEach { p ->
                        val result = if (p.type == "image") {
                            attachmentStore.storeImage(p.uri, conversationId)
                        } else {
                            attachmentStore.storeFile(p.uri, conversationId)
                        }
                        if (result == null) attachmentFailed = true else stored.add(result)
                    }
                }
                if (attachmentFailed) {
                    stored.forEach { attachmentStore.delete(it.relativePath) }
                    failGeneration("附件保存失败，草稿已保留，请重试或移除附件")
                    return@launch
                }
                // 先持久化用户消息，成功后再清空输入，避免发送失败时丢失草稿。
                val userMessage = try {
                    repository.appendMessage(
                        conversationId = conversationId,
                        role = "user",
                        content = text,
                        parentId = parentId,
                        attachments = stored,
                    )
                } catch (e: Exception) {
                    Logger.e("append user message failed", e)
                    stored.forEach { attachmentStore.delete(it.relativePath) }
                    failGeneration("发送失败：本地数据写入异常，请重试")
                    return@launch
                }
                _state.update {
                    it.copy(
                        input = "",
                        pendingAttachments = emptyList(),
                        paramNotice = if (hasFileAttachment) {
                            "文件已保存在本地，但当前模型不会读取文件正文；图片会随消息发送。"
                        } else {
                            it.paramNotice
                        },
                    )
                }
                generate(conversationId, userMessage.id)
            } catch (e: CancellationException) {
                if (_state.value.composerState == ComposerState.STOP_REQUESTED) {
                    _state.update { it.copy(composerState = ComposerState.CANCELLED) }
                }
                throw e
            }
        }
    }

    fun regenerate(messageId: Long) {
        if (!canStartGeneration()) return
        beginPreparing()
        prepareJob = viewModelScope.launch {
            try {
                val message = repository.findMessage(messageId)
                if (message == null) {
                    failGeneration("消息不存在，无法重新生成")
                    return@launch
                }
                generate(_state.value.conversationId, message.parentId)
            } catch (e: CancellationException) {
                if (_state.value.composerState == ComposerState.STOP_REQUESTED) {
                    _state.update { it.copy(composerState = ComposerState.CANCELLED) }
                }
                throw e
            }
        }
    }

    fun editAndResend(messageId: Long, newText: String) {
        if (!canStartGeneration()) return
        val text = newText.trim()
        if (text.isEmpty()) return
        val conversationId = _state.value.conversationId
        beginPreparing()
        prepareJob = viewModelScope.launch {
            try {
                val message = repository.findMessage(messageId)
                if (message == null) {
                    failGeneration("消息不存在，无法编辑")
                    return@launch
                }
                if (message.role != "user") {
                    failGeneration("只能编辑用户消息")
                    return@launch
                }
                val newUser = try {
                    repository.appendMessage(conversationId, "user", text, message.parentId)
                } catch (e: Exception) {
                    Logger.e("append edited message failed", e)
                    failGeneration("发送失败：本地数据写入异常，请重试")
                    return@launch
                }
                generate(conversationId, newUser.id)
            } catch (e: CancellationException) {
                if (_state.value.composerState == ComposerState.STOP_REQUESTED) {
                    _state.update { it.copy(composerState = ComposerState.CANCELLED) }
                }
                throw e
            }
        }
    }

    fun deleteMessage(messageId: Long) {
        val conversationId = _state.value.conversationId
        viewModelScope.launch {
            repository.softDeleteMessage(conversationId, messageId)
            refreshPath()
        }
    }

    fun switchBranch(messageId: Long) {
        val conversationId = _state.value.conversationId
        viewModelScope.launch {
            repository.switchBranch(conversationId, messageId)
            refreshPath()
        }
    }

    /** 在同一父节点的兄弟分支间循环切换。 */
    fun cycleBranch(messageId: Long, delta: Int) {
        val conversationId = _state.value.conversationId
        viewModelScope.launch {
            val message = repository.findMessage(messageId) ?: return@launch
            val siblings = repository.listMessages(conversationId)
                .filter { it.parentId == message.parentId }
                .sortedBy { it.siblingSeq }
            if (siblings.size < 2) return@launch
            val index = siblings.indexOfFirst { it.id == messageId }
            if (index < 0) return@launch
            val next = siblings[(index + delta + siblings.size) % siblings.size]
            repository.switchBranch(conversationId, next.id)
            refreshPath()
        }
    }

    fun stopGenerating() {
        val active = _state.value.composerState in setOf(
            ComposerState.PREPARING,
            ComposerState.STREAMING,
            ComposerState.STOP_REQUESTED,
        )
        if (!active) return
        _state.update { it.copy(composerState = ComposerState.STOP_REQUESTED) }
        prepareJob?.cancel()
        streamJob?.cancel()
    }

    /** 清除一次性错误（Snackbar 展示后调用）。 */
    fun clearError() {
        _state.update { it.copy(globalError = null) }
    }

    fun clearParamNotice() {
        _state.update { it.copy(paramNotice = null) }
    }

    /** 当前是否允许发起新的生成任务。 */
    private fun canStartGeneration(): Boolean = when (_state.value.composerState) {
        ComposerState.IDLE, ComposerState.COMPLETED, ComposerState.FAILED, ComposerState.CANCELLED -> true
        else -> false
    }

    private fun beginPreparing() {
        _state.update {
            it.copy(
                composerState = ComposerState.PREPARING,
                globalError = null,
                streamingMessageId = null,
                streamingContent = "",
                streamingReasoning = "",
            )
        }
    }

    private fun beginStreaming(messageId: Long) {
        _state.update {
            it.copy(
                composerState = ComposerState.STREAMING,
                streamingMessageId = messageId,
                streamingContent = "",
                streamingReasoning = "",
                globalError = null,
            )
        }
    }

    /** 早退/失败统一进入终态，并保留草稿。 */
    private fun failGeneration(message: String) {
        _state.update {
            it.copy(
                composerState = ComposerState.FAILED,
                streamingMessageId = null,
                globalError = message,
            )
        }
    }

    /** 会话级联网搜索开关。未配置搜索服务时给出提示。 */
    fun toggleSearch() {
        val next = !_state.value.sessionSearch
        _state.update { it.copy(sessionSearch = next) }
        if (next) {
            viewModelScope.launch {
                if (loadSearchConfig() == null) {
                    _state.update { it.copy(globalError = "搜索不可用：请先在「管理 → 相关配置」添加 DeepSeek 服务商，或到「我的 → 网络搜索」配置自定义服务") }
                }
            }
        }
    }

    private suspend fun loadSearchConfig(): com.tongzhouweiji.baichuan.core.search.SearchManager.SearchConfig? {
        val mode = app.container.settingDao.get("search_mode") ?: "deepseek"
        if (mode == "deepseek") {
            val providers = providerDao.listAll()
            val selectedId = app.container.settingDao.get("search_provider_id")?.toLongOrNull()
            // 优先使用用户选定的服务商；未选择时回退到 DeepSeek 域名的服务商。
            val provider = selectedId?.let { id -> providers.firstOrNull { it.id == id } }
                ?: providers.firstOrNull { it.baseUrl.contains("deepseek", ignoreCase = true) }
                ?: return null
            val key = SecretStore.decrypt(provider.keyRef.orEmpty())
            if (key.isBlank()) return null
            return com.tongzhouweiji.baichuan.core.search.SearchManager.SearchConfig(
                mode = com.tongzhouweiji.baichuan.core.search.SearchManager.Mode.DEEPSEEK,
                endpoint = com.tongzhouweiji.baichuan.core.search.SearchManager.DEEPSEEK_SEARCH_ENDPOINT,
                apiKey = key,
            )
        }
        val url = app.container.settingDao.get("search_base_url") ?: return null
        val key = SecretStore.decrypt(app.container.settingDao.get("search_key_ref").orEmpty())
        if (key.isBlank()) return null
        return com.tongzhouweiji.baichuan.core.search.SearchManager.SearchConfig(
            mode = com.tongzhouweiji.baichuan.core.search.SearchManager.Mode.TAVILY,
            endpoint = url,
            apiKey = key,
        )
    }

    /** 会话级深度思考开关：当前开启则关闭，否则升到高。 */
    fun toggleDeepThinking() {
        val current = _state.value.sessionReasoning
        val next = if (current?.isThinking == true) ReasoningEffort.OFF else ReasoningEffort.HIGH
        _state.update { it.copy(sessionReasoning = next) }
    }

    private suspend fun generate(conversationId: Long, contextLeafId: Long?) {
        val agent = _state.value.agent
        if (agent == null) {
            failGeneration("助手不存在，无法发送")
            return
        }
        val path = repository.pathTo(conversationId, contextLeafId)
        if (!path.valid) {
            failGeneration(PromptAssembler.INVALID_PATH_MESSAGE)
            return
        }
        val resolved = resolveModel(agent)
        if (resolved == null) {
            failGeneration("未配置模型：请先在「管理 → 相关配置」添加服务商并选择模型")
            return
        }
        val (config, modelName) = resolved
        val agentParams = parseGeneration(agent)
        val effort = _state.value.sessionReasoning ?: agentParams.reasoning
        val params = agentParams.copy(reasoning = effort)

        var imageReadFailed = false
        val imagesByMessage = withContext(Dispatchers.IO) {
            val map = mutableMapOf<Long, List<String>>()
            path.messages.forEach { message ->
                val images = attachmentDao.listByMessage(message.id)
                    .filter { it.type == "image" }
                    .mapNotNull { attachment ->
                        attachmentStore.readBase64(attachment.uri).also { if (it == null) imageReadFailed = true }
                    }
                if (images.isNotEmpty()) map[message.id] = images
            }
            map
        }

        maybeCompress(conversationId, agent, path.messages)
        val memories = selectMemories(memoryDao.listByAgent(agent.id), latestUserText(path.messages))
        val summary = summaryDao.latest(conversationId)
        val effectivePath = if (summary != null) {
            path.messages.filter { it.id > summary.upToMessageId }
        } else {
            path.messages
        }
        var searchContext: String? = null
        var sourcesJson: String? = null
        val isDeepSeekSearchModel = modelName.contains("deepseek", ignoreCase = true) &&
            modelName.contains("flash", ignoreCase = true)
        if (_state.value.sessionSearch && !isDeepSeekSearchModel) {
            val searchConfig = loadSearchConfig()
            if (searchConfig != null) {
                val query = effectivePath.lastOrNull { it.role == "user" }?.content.orEmpty()
                if (query.isNotBlank()) {
                    runCatching {
                        com.tongzhouweiji.baichuan.core.search.SearchManager.search(searchConfig, query)
                    }.getOrNull()?.let { results ->
                        if (results.isNotEmpty()) {
                            searchContext = com.tongzhouweiji.baichuan.core.search.SearchManager.formatForPrompt(results)
                            sourcesJson = com.tongzhouweiji.baichuan.core.search.SearchManager.sourcesJson(results)
                        }
                    }
                }
            }
        }
        val assembled = PromptAssembler.assemble(
            agent = agent,
            path = effectivePath,
            imagesByMessage = imagesByMessage,
            memories = memories,
            summary = summary?.summary,
            searchContext = searchContext,
        )
        if (assembled.error != null) {
            failGeneration(assembled.error)
            return
        }
        val notice = when {
            imageReadFailed -> "部分图片读取失败，已从本次请求中跳过"
            params.reasoning.isThinking && config.baseUrl.contains("deepseek", ignoreCase = true) ->
                "深度思考模式已开启：温度参数不生效，关闭深度思考后温度才可用"
            else -> null
        }
        if (notice != null) {
            _state.update { it.copy(paramNotice = notice) }
        }

        val placeholder = repository.appendAssistantPlaceholder(conversationId, contextLeafId)
        beginStreaming(placeholder.id)
        _state.update { it.copy(modelLabel = modelName) }
        refreshPath()

        val isDeepSeek = config.baseUrl.contains("deepseek", ignoreCase = true)
        val serverTools = if (isDeepSeekSearchModel && _state.value.sessionSearch) {
            buildList {
                add("deepseek_web_search")
                if (params.reasoning.isThinking) add("deepseek_read_url")
            }
        } else {
            emptyList()
        }
        val request = ChatRequest(
            model = modelName,
            messages = assembled.messages,
            temperature = params.temperature,
            stream = params.stream,
            reasoningEffort = params.reasoning,
            serverTools = serverTools,
            safetyLevel = app.container.settingDao.get("safety_level")?.takeIf { it.isNotBlank() },
        )

        streamJob = viewModelScope.launch {
            val buffer = StringBuilder()
            val reasoning = StringBuilder()
            val startedAt = System.currentTimeMillis()
            var reasoningStartAt = 0L
            var reasoningEndAt = 0L
            val reasoningMs: () -> Long? = {
                when {
                    reasoningStartAt > 0L && reasoningEndAt > 0L -> reasoningEndAt - reasoningStartAt
                    reasoningStartAt > 0L -> System.currentTimeMillis() - reasoningStartAt
                    else -> null
                }
            }
            try {
                if (params.stream) {
                    val result = adapterStream(
                        config = config,
                        request = request,
                        onDelta = { delta ->
                            if (reasoningEndAt == 0L) reasoningEndAt = System.currentTimeMillis()
                            buffer.append(delta)
                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdate.get() > 80) {
                                lastUiUpdate.set(now)
                                _state.update {
                                    it.copy(
                                        streamingContent = buffer.toString(),
                                        streamingReasoning = reasoning.toString(),
                                    )
                                }
                            }
                        },
                        onReasoning = { think ->
                            if (reasoningStartAt == 0L) reasoningStartAt = System.currentTimeMillis()
                            reasoning.append(think)
                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdate.get() > 80) {
                                lastUiUpdate.set(now)
                                _state.update { it.copy(streamingReasoning = reasoning.toString()) }
                            }
                        },
                    )
                    val finalText = result.content.ifEmpty { buffer.toString() }
                    val finalReasoning = result.reasoning ?: reasoning.toString().ifBlank { null }
                    if (sourcesJson != null) {
                        repository.updateSources(placeholder.id, sourcesJson)
                    }
                    usageDao.insert(
                        ApiUsageEntity(
                            scope = "chat",
                            conversationId = conversationId,
                            messageId = placeholder.id,
                            promptTokens = result.promptTokens,
                            completionTokens = result.completionTokens,
                            cachedTokens = result.cachedTokens,
                            latencyMs = System.currentTimeMillis() - startedAt,
                            success = true,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                    repository.finishMessage(
                        messageId = placeholder.id,
                        status = "done",
                        content = finalText,
                        reasoning = finalReasoning,
                        reasoningMs = reasoningMs(),
                    )
                    _state.update {
                        it.copy(
                            streamingContent = finalText,
                            streamingReasoning = finalReasoning.orEmpty(),
                            streamingMessageId = null,
                            composerState = ComposerState.COMPLETED,
                        )
                    }
                } else {
                    val result = ModelAdapterRegistry.adapterFor(config.protocol).chat(config, request)
                    repository.finishMessage(
                        messageId = placeholder.id,
                        status = "done",
                        content = result.content,
                        reasoning = result.reasoning,
                        reasoningMs = if (result.reasoning != null) System.currentTimeMillis() - startedAt else null,
                    )
                    _state.update {
                        it.copy(
                            streamingContent = result.content,
                            streamingReasoning = result.reasoning.orEmpty(),
                            streamingMessageId = null,
                            composerState = ComposerState.COMPLETED,
                        )
                    }
                }
            } catch (e: CancellationException) {
                repository.finishMessage(
                    placeholder.id, "cancelled", buffer.toString(),
                    reasoning = reasoning.toString().ifBlank { null },
                    reasoningMs = reasoningMs(),
                )
                _state.update {
                    it.copy(
                        composerState = ComposerState.CANCELLED,
                        streamingMessageId = null,
                    )
                }
                throw e
            } catch (e: ModelException) {
                Logger.w("stream failed: ${e.code}")
                errorEventDao.insert(
                    ErrorEventEntity(
                        agentId = agent.id,
                        conversationId = conversationId,
                        messageId = placeholder.id,
                        code = e.code,
                        summary = e.message,
                        detail = Logger.redact((e.diagnostic ?: e.stackTraceToString()).take(4000)),
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                repository.finishMessage(
                    placeholder.id, "failed", buffer.toString(), e.message,
                    reasoning = reasoning.toString().ifBlank { null },
                    reasoningMs = reasoningMs(),
                )
                _state.update {
                    it.copy(
                        streamingMessageId = null,
                        globalError = e.message,
                        composerState = ComposerState.FAILED,
                    )
                }
            } catch (e: Exception) {
                Logger.e("stream unexpected", e)
                errorEventDao.insert(
                    ErrorEventEntity(
                        agentId = agent.id,
                        conversationId = conversationId,
                        messageId = placeholder.id,
                        code = "unexpected",
                        summary = "生成失败，请稍后重试",
                        detail = Logger.redact(e.stackTraceToString().take(4000)),
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                repository.finishMessage(
                    placeholder.id, "failed", buffer.toString(), "生成失败，请稍后重试",
                    reasoning = reasoning.toString().ifBlank { null },
                    reasoningMs = reasoningMs(),
                )
                _state.update {
                    it.copy(
                        streamingMessageId = null,
                        globalError = "生成失败，请稍后重试",
                        composerState = ComposerState.FAILED,
                    )
                }
            } finally {
                withContext(NonCancellable) { refreshPath() }
            }
        }
    }

    private suspend fun adapterStream(
        config: ProviderConfig,
        request: ChatRequest,
        onDelta: suspend (String) -> Unit,
        onReasoning: suspend (String) -> Unit,
    ) = ModelAdapterRegistry.adapterFor(config.protocol).streamChat(
        config = config,
        request = request,
        onDelta = onDelta,
        onReasoning = onReasoning,
    )

    /**
     * 记忆筛选：置顶记忆始终注入；其余记忆只在与当前问题有词面重叠时注入，
     * 避免所有记忆无差别进入每一轮 system prompt。
     */
    private fun selectMemories(all: List<MemoryEntity>, question: String, limit: Int = 8): List<String> {
        if (all.isEmpty()) return emptyList()
        val keywords = tokenize(question)
        return all.asSequence()
            .filter { memory ->
                memory.pinned || keywords.any { keyword ->
                    keyword.length >= 2 && memory.content.contains(keyword, ignoreCase = true)
                }
            }
            .take(limit)
            .map { it.content.trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    private fun tokenize(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val ascii = Regex("[A-Za-z0-9_]{2,}").findAll(text).map { it.value }.toList()
        val chineseRuns = Regex("[\\u4e00-\\u9fa5]{2,}").findAll(text).map { it.value }.toList()
        val bigrams = chineseRuns.flatMap { run ->
            (0 until run.length - 1).map { index -> run.substring(index, index + 2) }
        }
        return (ascii + bigrams).distinct().take(24)
    }

    private fun latestUserText(path: List<MessageEntity>): String =
        path.lastOrNull { it.role == "user" }?.content.orEmpty()

    /**
     * 上下文压缩：未压缩消息超过 30 条时，用当前模型对最早 20 条生成摘要，
     * 后续对话用摘要替代这批消息。失败不影响正常聊天。
     */
    private suspend fun maybeCompress(conversationId: Long, agent: AgentEntity, path: List<MessageEntity>) {
        val latest = summaryDao.latest(conversationId)
        val uncovered = if (latest != null) path.filter { it.id > latest.upToMessageId } else path
        if (uncovered.size < 30) return
        val toCompress = uncovered.take(20)
        val binding = app.container.auxBindingDao.find("compression")
        val boundModel = binding?.modelId?.let { providerDao.findModelById(it) }
        val resolved = if (boundModel != null) {
            val provider = providerDao.findById(boundModel.providerId)
            val key = SecretStore.decrypt(provider?.keyRef.orEmpty())
            if (provider == null || key.isEmpty()) null
            else ProviderConfig(
                id = provider.id,
                name = provider.name,
                baseUrl = provider.baseUrl,
                protocol = provider.protocol,
                apiKey = key,
            ) to boundModel.modelName
        } else {
            resolveModel(agent)
        } ?: return
        val (config, modelName) = resolved
        val adapter = ModelAdapterRegistry.adapterFor(config.protocol)
        val transcript = toCompress.joinToString("\n") { "${it.role}: ${it.content}" }
        val request = ChatRequest(
            model = modelName,
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = "请用简洁的中文总结下面这段对话的要点，保留人物、设定与关键决定，不要添加评论：\n\n$transcript",
                ),
            ),
            stream = false,
        )
        runCatching {
            val result = adapter.chat(config, request)
            if (result.content.isNotBlank()) {
                summaryDao.insert(
                    ConversationSummaryEntity(
                        conversationId = conversationId,
                        upToMessageId = toCompress.last().id,
                        summary = result.content.take(2000),
                        tokenCount = result.completionTokens ?: 0,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }.onFailure { Logger.w("上下文压缩失败", it) }
    }

    private fun parseGeneration(agent: AgentEntity): GenerationParams = runCatching {
        val obj = json.parseToJsonElement(agent.generationJson).jsonObject
        GenerationParams(
            temperature = obj["temperature"]?.jsonPrimitive?.doubleOrNull ?: 0.7,
            stream = obj["stream"]?.jsonPrimitive?.booleanOrNull ?: true,
            reasoning = ReasoningEffort.fromKey(obj["reasoning"]?.jsonPrimitive?.contentOrNull),
        )
    }.getOrDefault(GenerationParams())

    private suspend fun resolveModel(agent: AgentEntity): Pair<ProviderConfig, String>? {
        val modelId = agent.modelConfigId ?: providerDao.defaultModel()?.id ?: return null
        val model = providerDao.findModelById(modelId) ?: return null
        val provider = providerDao.findById(model.providerId) ?: return null
        val apiKey = SecretStore.decrypt(provider.keyRef.orEmpty())
        if (apiKey.isEmpty()) return null
        return ProviderConfig(
            id = provider.id,
            name = provider.name,
            baseUrl = provider.baseUrl,
            protocol = provider.protocol,
            apiKey = apiKey,
        ) to model.modelName
    }

    class Factory(
        private val app: BaichuanApplication,
        private val agentId: Long,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(
                app = app,
                repository = app.container.chatRepository,
                providerDao = app.container.providerDao,
                conversationDao = app.container.conversationDao,
                attachmentDao = app.container.attachmentDao,
                attachmentStore = app.container.attachmentStore,
                memoryDao = app.container.memoryDao,
                summaryDao = app.container.summaryDao,
                errorEventDao = app.container.errorEventDao,
                usageDao = app.container.usageDao,
                agentId = agentId,
            ) as T
    }
}
