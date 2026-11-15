package com.tongzhouweiji.baichuan.chat

import com.tongzhouweiji.baichuan.core.db.BaichuanDatabase
import com.tongzhouweiji.baichuan.core.db.entity.AgentEntity
import com.tongzhouweiji.baichuan.core.db.entity.AttachmentEntity
import com.tongzhouweiji.baichuan.core.db.entity.ConversationEntity
import com.tongzhouweiji.baichuan.core.db.entity.MessageEntity
import com.tongzhouweiji.baichuan.core.media.StoredAttachment
import kotlinx.coroutines.flow.Flow

/**
 * 消息树路径结果。聊天分支出现断链时 [valid] 为 false，调用方必须本地拦截，
 * 不能再把不完整的 role 序列发给模型服务商。
 */
data class MessagePath(
    val messages: List<MessageEntity>,
    val valid: Boolean,
    val reason: String? = null,
)

/**
 * 聊天数据仓库：负责会话创建、消息树与分支操作。界面不直接拼 SQL。
 */
class ChatRepository(private val db: BaichuanDatabase) {

    fun observeAgents(): Flow<List<AgentEntity>> = db.agentDao().observeAll()

    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>> =
        db.messageDao().observeByConversation(conversationId)

    suspend fun findAgent(agentId: Long): AgentEntity? = db.agentDao().findById(agentId)

    suspend fun findMessage(id: Long): MessageEntity? = db.messageDao().findById(id)

    suspend fun listMessages(conversationId: Long): List<MessageEntity> =
        db.messageDao().listByConversation(conversationId)

    suspend fun ensureConversation(agentId: Long): ConversationEntity {
        db.conversationDao().latestByAgent(agentId)?.let { return it }
        val now = System.currentTimeMillis()
        val id = db.conversationDao().insert(
            ConversationEntity(agentId = agentId, createdAt = now, updatedAt = now),
        )
        return db.conversationDao().findById(id)
            ?: throw IllegalStateException("会话创建失败")
    }

    /**
     * 从指定消息沿 parentId 回溯到根，得到当前分支的完整路径。
     *
     * 与旧实现不同：这里会读取包含已删除消息的完整消息树，校验父节点缺失、
     * 已删除祖先、跨会话父节点和循环引用；任何异常都返回 invalid，而不是静默截断。
     */
    suspend fun pathTo(conversationId: Long, leafId: Long?): MessagePath {
        if (leafId == null) return MessagePath(emptyList(), valid = true)
        val all = db.messageDao().listAllByConversation(conversationId)
        val byId = all.associateBy { it.id }
        val leaf = byId[leafId]
            ?: return MessagePath(emptyList(), valid = false, reason = "当前分支的末端消息不存在")
        val path = ArrayDeque<MessageEntity>()
        val guard = mutableSetOf<Long>()
        var current: MessageEntity? = leaf
        while (current != null) {
            if (!guard.add(current.id)) {
                return MessagePath(emptyList(), valid = false, reason = "消息树存在循环引用")
            }
            if (current.deletedAt != null) {
                return MessagePath(emptyList(), valid = false, reason = "当前分支包含已删除消息")
            }
            path.addFirst(current)
            val parentId = current.parentId ?: break
            val parent = byId[parentId]
                ?: return MessagePath(emptyList(), valid = false, reason = "当前分支的上一条消息缺失")
            if (parent.conversationId != conversationId) {
                return MessagePath(emptyList(), valid = false, reason = "消息树跨会话引用")
            }
            current = parent
        }
        return MessagePath(path.toList(), valid = true)
    }

    suspend fun appendMessage(
        conversationId: Long,
        role: String,
        content: String,
        parentId: Long?,
        status: String = "done",
        attachments: List<StoredAttachment> = emptyList(),
    ): MessageEntity {
        val now = System.currentTimeMillis()
        val siblingSeq = db.messageDao().maxSiblingSeq(parentId) + 1
        val id = db.messageDao().insert(
            MessageEntity(
                conversationId = conversationId,
                role = role,
                content = content,
                parentId = parentId,
                siblingSeq = siblingSeq,
                status = status,
                createdAt = now,
                updatedAt = now,
            ),
        )
        if (attachments.isNotEmpty()) {
            db.attachmentDao().insertAll(
                attachments.map { stored ->
                    AttachmentEntity(
                        messageId = id,
                        type = stored.type,
                        uri = stored.relativePath,
                        mime = stored.mime,
                        size = stored.size,
                        name = stored.name,
                    )
                },
            )
        }
        db.conversationDao().updateActiveLeaf(conversationId, id, now)
        return db.messageDao().findById(id)
            ?: throw IllegalStateException("消息写入失败")
    }

    suspend fun appendAssistantPlaceholder(conversationId: Long, parentId: Long?): MessageEntity =
        appendMessage(conversationId, "assistant", "", parentId, status = "streaming")

    suspend fun updateContent(messageId: Long, content: String) {
        db.messageDao().updateContent(messageId, content, System.currentTimeMillis())
    }

    suspend fun finishMessage(
        messageId: Long,
        status: String,
        content: String,
        error: String? = null,
        reasoning: String? = null,
        reasoningMs: Long? = null,
    ) {
        db.messageDao().updateStatus(
            id = messageId,
            status = status,
            content = content,
            error = error,
            reasoning = reasoning,
            reasoningMs = reasoningMs,
            now = System.currentTimeMillis(),
        )
    }

    suspend fun updateSources(messageId: Long, json: String) {
        db.messageDao().updateSources(messageId, json, System.currentTimeMillis())
    }

    /**
     * 删除一条消息及其在当前会话中的全部后代，并把活动叶子回退到最近一个未删除祖先。
     * 其他独立分支不受影响；不物理删除数据。
     */
    suspend fun softDeleteMessage(conversationId: Long, messageId: Long) {
        val all = db.messageDao().listAllByConversation(conversationId)
        val byId = all.associateBy { it.id }
        val target = byId[messageId] ?: return
        val now = System.currentTimeMillis()
        val descendants = mutableListOf<MessageEntity>()
        val stack = ArrayDeque<MessageEntity>()
        all.filter { it.parentId == messageId }.forEach { stack.addLast(it) }
        val seen = mutableSetOf<Long>()
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (!seen.add(current.id)) continue
            descendants.add(current)
            all.filter { it.parentId == current.id }.forEach { stack.addLast(it) }
        }
        val ids = listOf(messageId) + descendants.map { it.id }
        db.messageDao().softDeleteMany(ids, now)

        val conversation = db.conversationDao().findById(conversationId) ?: return
        val activeLeaf = conversation.activeLeafMessageId
        if (activeLeaf == messageId || descendants.any { it.id == activeLeaf }) {
            db.conversationDao().updateActiveLeaf(
                conversationId,
                nearestValidAncestorId(target, byId),
                now,
            )
        }
    }

    /** 找最近一个未删除的祖先；没有则返回 null（空会话）。 */
    private fun nearestValidAncestorId(
        message: MessageEntity,
        byId: Map<Long, MessageEntity>,
    ): Long? {
        var current = message.parentId?.let { byId[it] }
        while (current != null) {
            if (current.deletedAt == null) return current.id
            current = current.parentId?.let { byId[it] }
        }
        return null
    }

    /** 历史数据修复：活动叶子无效时回退到最新一条未删除消息。 */
    suspend fun repairActiveLeaf(conversationId: Long): Long? {
        val conversation = db.conversationDao().findById(conversationId) ?: return null
        val existing = db.messageDao().findById(conversation.activeLeafMessageId ?: -1)
        if (existing != null && existing.deletedAt == null) return existing.id
        val fallback = db.messageDao().listByConversation(conversationId).lastOrNull()
        db.conversationDao().updateActiveLeaf(
            conversationId,
            fallback?.id,
            System.currentTimeMillis(),
        )
        return fallback?.id
    }

    /** 切换到某条消息所在分支：以该消息最新的后代作为当前末端。 */
    suspend fun switchBranch(conversationId: Long, messageId: Long) {
        val all = db.messageDao().listByConversation(conversationId)
        val children = all.groupBy { it.parentId }
        var leaf = messageId
        val guard = mutableSetOf<Long>()
        while (guard.add(leaf)) {
            val next = children[leaf]?.maxByOrNull { it.siblingSeq } ?: break
            leaf = next.id
        }
        db.conversationDao().updateActiveLeaf(conversationId, leaf, System.currentTimeMillis())
    }

    /** 同一父节点下的兄弟消息（用于分支切换器）。 */
    suspend fun siblings(conversationId: Long, message: MessageEntity): List<MessageEntity> {
        val all = db.messageDao().listByConversation(conversationId)
        return all.filter { it.parentId == message.parentId }.sortedBy { it.siblingSeq }
    }
}
