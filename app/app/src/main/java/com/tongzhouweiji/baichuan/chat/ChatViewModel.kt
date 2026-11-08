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

data class ChatUiState(
    val agent: AgentEntity? = null,
    val conversationId: Long = 0,
    val activePath: List<MessageEntity> = emptyList(),
    val attachmentsByMessage: Map<Long, List<AttachmentEntity>> = emptyMap(),
    val branchInfo: Map<Long, BranchInfo> = emptyMap(),
    val input: String = "",
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingMessageId: Long? = null,
    val streamingContent: String = "",
    val streamingReasoning: String = "",
    val globalError: String? = null,
    val sessionReasoning: ReasoningEffort? = null,
    val paramNotice: String? = null,
    val modelLabel: String? = null,
    val sessionSearch: Boolean = false,
)

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
        val conversation = conversationDao.findById(conversationId) ?: return
        val path = repository.pathTo(conversationId, conversation.activeLeafMessageId)
        val all = repository.listMessages(conversationId)
        val branchInfo = mutableMapOf<Long, BranchInfo>()
        path.forEach { message ->
            val siblings = all.filter { it.parentId == message.parentId }.sortedBy { it.siblingSeq }
            if (siblings.size > 1) {
                val index = siblings.indexOfFirst { it.id == message.id } + 1
                branchInfo[message.id] = BranchInfo(index = index, total = siblings.size)
            }
        }
        val attachments = if (path.isEmpty()) emptyMap() else
            attachmentDao.listByMessages(path.map { it.id }).groupBy { it.messageId }
        _state.update {
            it.copy(
                activePath = path,
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
        if (_state.value.isStreaming) return
        val conversationId = _state.value.conversationId
        if (conversationId == 0L) return
        viewModelScope.launch {
            _state.update { it.copy(input = "", pendingAttachments = emptyList()) }
            val conversation = conversationDao.findById(conversationId) ?: return@launch
            val parentId = conversation.activeLeafMessageId
            val stored = withContext(Dispatchers.IO) {
                pending.mapNotNull { p ->
                    if (p.type == "image") attachmentStore.storeImage(p.uri, conversationId)
                    else attachmentStore.storeFile(p.uri, conversationId)
                }
            }
            val userMessage = repository.appendMessage(
                conversationId = conversationId,
                role = "user",
                content = text,
                parentId = parentId,
                attachments = stored,
            )
            generate(conversationId, userMessage.id)
        }
    }

    fun regenerate(messageId: Long) {
        if (_state.value.isStreaming) return
        val conversationId = _state.value.conversationId
        viewModelScope.launch {
            val message = repository.findMessage(messageId) ?: return@launch
            generate(conversationId, message.parentId)
        }
    }

    fun editAndResend(messageId: Long, newText: String) {
        if (_state.value.isStreaming) return
        val text = newText.trim()
        if (text.isEmpty()) return
        val conversationId = _state.value.conversationId
        viewModelScope.launch {
            val message = repository.findMessage(messageId) ?: return@launch
            if (message.role != "user") return@launch
            val newUser = repository.appendMessage(conversationId, "user", text, message.parentId)
            generate(conversationId, newUser.id)
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            repository.softDeleteMessage(messageId)
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
        streamJob?.cancel()
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
        val agent = _state.value.agent ?: return
        val contextPath = repository.pathTo(conversationId, contextLeafId)
        if (app.container.agentManager.isAuthorized(agent.id)) {
            runAgent(conversationId, agent, contextPath)
            return
        }
        val agentParams = parseGeneration(agent)
        val effort = _state.value.sessionReasoning ?: agentParams.reasoning
        val params = agentParams.copy(reasoning = effort)
        val imagesByMessage = withContext(Dispatchers.IO) {
            val map = mutableMapOf<Long, List<String>>()
            contextPath.forEach { message ->
                val images = attachmentDao.listByMessage(message.id)
                    .filter { it.type == "image" }
                    .mapNotNull { attachmentStore.readBase64(it.uri) }
                if (images.isNotEmpty()) map[message.id] = images
            }
            map
        }
        maybeCompress(conversationId, agent, contextPath)
        val memories = memoryDao.listByAgent(agent.id).take(20).map { it.content }
        val summary = summaryDao.latest(conversationId)
        val effectivePath = if (summary != null) {
            contextPath.filter { it.id > summary.upToMessageId }
        } else {
            contextPath
        }
        var searchContext: String? = null
        var sourcesJson: String? = null
        if (_state.value.sessionSearch) {
            val searchConfig = loadSearchConfig()
            if (searchConfig != null) {
                val query = contextPath.lastOrNull { it.role == "user" }?.content.orEmpty()
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
        val messages = PromptAssembler.assemble(
            agent = agent,
            path = effectivePath,
            imagesByMessage = imagesByMessage,
            memories = memories,
            summary = summary?.summary,
            searchContext = searchContext,
        )
        val placeholder = repository.appendAssistantPlaceholder(conversationId, contextLeafId)
        _state.update {
            it.copy(
                isStreaming = true,
                streamingMessageId = placeholder.id,
                streamingContent = "",
                globalError = null,
            )
        }
        refreshPath()

        val resolved = resolveModel(agent)
        if (resolved == null) {
            repository.finishMessage(
                placeholder.id,
                "failed",
                "",
                "未配置模型：请先在「管理 → 相关配置」添加服务商并选择模型",
            )
            _state.update { it.copy(isStreaming = false, streamingMessageId = null) }
            refreshPath()
            return
        }
        val (config, modelName) = resolved
        val isDeepSeek = config.baseUrl.contains("deepseek", ignoreCase = true)
        val notice = if (params.reasoning.isThinking && isDeepSeek) "深度思考模式已开启：温度参数不生效，关闭深度思考后温度才可用" else null
        _state.update { it.copy(modelLabel = modelName, paramNotice = notice) }
        val adapter = ModelAdapterRegistry.adapterFor(config.protocol)
        val request = ChatRequest(
            model = modelName,
            messages = messages,
            temperature = params.temperature,
            stream = params.stream,
            reasoningEffort = params.reasoning,
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
                    val result = adapter.streamChat(
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
                            isStreaming = false,
                            streamingMessageId = null,
                        )
                    }
                } else {
                    val result = adapter.chat(config, request)
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
                            isStreaming = false,
                            streamingMessageId = null,
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                repository.finishMessage(
                    placeholder.id, "cancelled", buffer.toString(),
                    reasoning = reasoning.toString().ifBlank { null },
                    reasoningMs = reasoningMs(),
                )
                _state.update { it.copy(isStreaming = false, streamingMessageId = null) }
                throw e
            } catch (e: ModelException) {
                Logger.w("stream failed: ${e.code} ${e.message}")
                errorEventDao.insert(
                    ErrorEventEntity(
                        agentId = agent.id,
                        conversationId = conversationId,
                        messageId = placeholder.id,
                        code = e.code,
                        summary = e.message,
                        detail = Logger.redact(e.stackTraceToString().take(2000)),
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                repository.finishMessage(
                    placeholder.id, "failed", buffer.toString(), e.message,
                    reasoning = reasoning.toString().ifBlank { null },
                    reasoningMs = reasoningMs(),
                )
                _state.update {
                    it.copy(isStreaming = false, streamingMessageId = null, globalError = e.message)
                }
            } catch (e: Exception) {
                Logger.e("stream unexpected", e)
                repository.finishMessage(
                    placeholder.id, "failed", buffer.toString(), "生成失败，请稍后重试",
                    reasoning = reasoning.toString().ifBlank { null },
                    reasoningMs = reasoningMs(),
                )
                _state.update {
                    it.copy(isStreaming = false, streamingMessageId = null, globalError = "生成失败，请稍后重试")
                }
            } finally {
                withContext(NonCancellable) { refreshPath() }
            }
        }
    }

    /**
     * Agent 闭环：模型自主决定是否调用工具，百川真实执行并把结果回传模型，
     * 直到模型给出最终回复。非流式，界面显示调用状态。
     */
    private suspend fun runAgent(
        conversationId: Long,
        agent: AgentEntity,
        contextPath: List<MessageEntity>,
    ) {
        val resolved = resolveModel(agent)
        if (resolved == null) {
            val placeholder = repository.appendAssistantPlaceholder(conversationId, contextPath.lastOrNull()?.id)
            repository.finishMessage(placeholder.id, "failed", "", "未配置模型，无法使用 Agent")
            refreshPath()
            return
        }
        val (config, modelName) = resolved
        val userText = contextPath.lastOrNull { it.role == "user" }?.content.orEmpty()
        if (userText.isBlank()) {
            refreshPath()
            return
        }
        val systemPrompt = buildString {
            if (agent.systemPrompt.isNotBlank()) append(agent.systemPrompt).append("\n\n")
            if (agent.persona.isNotBlank()) append("人设：").append(agent.persona)
        }
        val placeholder = repository.appendAssistantPlaceholder(conversationId, contextPath.lastOrNull()?.id)
        _state.update {
            it.copy(
                isStreaming = true,
                streamingMessageId = placeholder.id,
                streamingContent = "正在调用工具…",
                globalError = null,
            )
        }
        refreshPath()
        try {
            val result = app.container.agentRunner.run(
                agentId = agent.id,
                goal = userText,
                systemPrompt = systemPrompt,
                config = config,
                modelName = modelName,
            )
            val text = result.text.ifBlank { "（Agent 已完成，但没有返回文本内容）" }
            repository.finishMessage(placeholder.id, "done", text)
            _state.update { it.copy(streamingContent = text, isStreaming = false, streamingMessageId = null) }
        } catch (e: Exception) {
            Logger.e("agent run failed", e)
            repository.finishMessage(placeholder.id, "failed", "", e.message ?: "Agent 执行失败")
            _state.update {
                it.copy(isStreaming = false, streamingMessageId = null, globalError = e.message ?: "Agent 执行失败")
            }
        } finally {
            withContext(NonCancellable) { refreshPath() }
        }
    }

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
