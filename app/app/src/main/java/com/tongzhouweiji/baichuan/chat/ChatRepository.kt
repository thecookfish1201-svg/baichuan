package com.tongzhouweiji.baichuan.chat

import com.tongzhouweiji.baichuan.core.db.BaichuanDatabase
import com.tongzhouweiji.baichuan.core.db.entity.AgentEntity
import com.tongzhouweiji.baichuan.core.db.entity.AttachmentEntity
import com.tongzhouweiji.baichuan.core.db.entity.ConversationEntity
import com.tongzhouweiji.baichuan.core.db.entity.MessageEntity
import com.tongzhouweiji.baichuan.core.media.StoredAttachment
import kotlinx.coroutines.flow.Flow

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

    /** 从指定消息沿 parentId 回溯到根，得到当前分支的完整路径。 */
    suspend fun pathTo(conversationId: Long, leafId: Long?): List<MessageEntity> {
        val all = db.messageDao().listByConversation(conversationId)
        if (all.isEmpty() || leafId == null) return emptyList()
        val byId = all.associateBy { it.id }
        val path = ArrayDeque<MessageEntity>()
        var current: MessageEntity? = byId[leafId]
        val guard = mutableSetOf<Long>()
        while (current != null && guard.add(current.id)) {
            path.addFirst(current)
            current = current.parentId?.let { byId[it] }
        }
        return path.toList()
    }

    suspend fun activePath(conversation: ConversationEntity): List<MessageEntity> {
        val leaf = conversation.activeLeafMessageId
        return if (leaf == null) emptyList() else pathTo(conversation.id, leaf)
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

    suspend fun softDeleteMessage(messageId: Long) {
        db.messageDao().softDelete(messageId, System.currentTimeMillis())
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
