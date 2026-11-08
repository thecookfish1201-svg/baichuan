package com.tongzhouweiji.baichuan.chat

import com.tongzhouweiji.baichuan.core.db.entity.AgentEntity
import com.tongzhouweiji.baichuan.core.db.entity.MessageEntity
import com.tongzhouweiji.baichuan.model.ChatMessage

/**
 * 提示词组装。分层顺序固定：
 * 系统提示词 / 人设 → 长期记忆 → 上下文压缩摘要 → 当前分支历史消息。
 * 消息时间只作为元数据保存在数据库，绝不拼进提示词前缀，避免破坏上下文缓存。
 */
object PromptAssembler {

    fun assemble(
        agent: AgentEntity,
        path: List<MessageEntity>,
        imagesByMessage: Map<Long, List<String>> = emptyMap(),
        memories: List<String> = emptyList(),
        summary: String? = null,
        searchContext: String? = null,
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        val system = buildString {
            if (agent.systemPrompt.isNotBlank()) append(agent.systemPrompt.trim())
            if (agent.persona.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("人设：").append(agent.persona.trim())
            }
        }
        if (system.isNotBlank()) {
            messages.add(ChatMessage(role = "system", content = system))
        }
        if (memories.isNotEmpty()) {
            messages.add(
                ChatMessage(
                    role = "system",
                    content = "长期记忆：\n" + memories.joinToString("\n") { "- $it" },
                ),
            )
        }
        if (!summary.isNullOrBlank()) {
            messages.add(
                ChatMessage(
                    role = "system",
                    content = "历史对话摘要（较早内容已压缩）：\n$summary",
                ),
            )
        }
        if (!searchContext.isNullOrBlank()) {
            messages.add(ChatMessage(role = "system", content = searchContext))
        }
        path.forEach { message ->
            if (message.deletedAt != null) return@forEach
            if (message.content.isBlank()) return@forEach
            messages.add(
                ChatMessage(
                    role = message.role,
                    content = message.content,
                    images = imagesByMessage[message.id].orEmpty(),
                ),
            )
        }
        return messages
    }
}
