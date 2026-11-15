package com.tongzhouweiji.baichuan.chat

import com.tongzhouweiji.baichuan.core.db.entity.MessageEntity

/**
 * 消息树纯逻辑：不依赖 Room，便于 JVM 单元测试。
 * 所有路径操作都必须经过这里，保证聊天分支不会静默断裂。
 */
object MessageTree {

    fun pathTo(all: List<MessageEntity>, conversationId: Long, leafId: Long?): MessagePath {
        if (leafId == null) return MessagePath(emptyList(), valid = true)
        val byId = all.associateBy { it.id }
        val leaf = byId[leafId]
            ?: return invalid("当前分支的末端消息不存在")
        val path = ArrayDeque<MessageEntity>()
        val guard = mutableSetOf<Long>()
        var current: MessageEntity? = leaf
        while (current != null) {
            if (!guard.add(current.id)) return invalid("消息树存在循环引用")
            if (current.deletedAt != null) return invalid("当前分支包含已删除消息")
            path.addFirst(current)
            val parentId = current.parentId ?: break
            val parent = byId[parentId] ?: return invalid("当前分支的上一条消息缺失")
            if (parent.conversationId != conversationId) return invalid("消息树跨会话引用")
            current = parent
        }
        return MessagePath(path.toList(), valid = true)
    }

    /** 某条消息在本会话内的全部后代（不含自身）。 */
    fun descendants(all: List<MessageEntity>, messageId: Long): List<MessageEntity> {
        val result = mutableListOf<MessageEntity>()
        val stack = ArrayDeque<MessageEntity>()
        all.filter { it.parentId == messageId }.forEach { stack.addLast(it) }
        val seen = mutableSetOf<Long>()
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (!seen.add(current.id)) continue
            result.add(current)
            all.filter { it.parentId == current.id }.forEach { stack.addLast(it) }
        }
        return result
    }

    /** 最近一个未删除的祖先；没有则返回 null（空会话）。 */
    fun nearestValidAncestorId(
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

    /** 活动叶子无效时，回退到最新一条未删除消息。 */
    fun repairLeaf(all: List<MessageEntity>, leafId: Long?): Long? {
        val existing = leafId?.let { id -> all.firstOrNull { it.id == id } }
        if (existing != null && existing.deletedAt == null) return existing.id
        return all.filter { it.deletedAt == null }.maxByOrNull { it.createdAt }?.id
    }

    private fun invalid(reason: String): MessagePath =
        MessagePath(emptyList(), valid = false, reason = reason)
}
