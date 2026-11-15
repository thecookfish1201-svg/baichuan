package com.tongzhouweiji.baichuan.chat

import com.tongzhouweiji.baichuan.core.db.entity.AgentEntity
import com.tongzhouweiji.baichuan.core.db.entity.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptAssemblerTest {

    private fun agent() = AgentEntity(
        id = 1,
        name = "测试助手",
        systemPrompt = "你是百川助手。",
        createdAt = 0,
        updatedAt = 0,
    )

    private fun message(
        id: Long,
        role: String,
        content: String,
        parentId: Long? = null,
    ) = MessageEntity(
        id = id,
        conversationId = 1,
        role = role,
        content = content,
        parentId = parentId,
        createdAt = id,
        updatedAt = id,
    )

    @Test
    fun imageOnlyUserMessageIsKept() {
        val result = PromptAssembler.assemble(
            agent = agent(),
            path = listOf(message(1, "user", "")),
            imagesByMessage = mapOf(1L to listOf("base64-image")),
        )
        assertNull(result.error)
        val user = result.messages.filter { it.role == "user" }
        assertEquals(1, user.size)
        assertEquals(listOf("base64-image"), user.first().images)
    }

    @Test
    fun blankMessageWithoutImageIsSkipped() {
        val result = PromptAssembler.assemble(
            agent = agent(),
            path = listOf(
                message(1, "user", "你好"),
                message(2, "assistant", ""),
            ),
        )
        assertNull(result.error)
        val conversation = result.messages.filter { it.role != "system" }
        assertEquals(1, conversation.size)
        assertEquals("你好", conversation.first().content)
    }

    @Test
    fun firstMessageAssistantIsRejected() {
        val result = PromptAssembler.assemble(
            agent = agent(),
            path = listOf(
                message(1, "assistant", "没有用户消息"),
                message(2, "user", "继续"),
            ),
        )
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("分支不完整"))
    }

    @Test
    fun consecutiveAssistantMessagesAreRejected() {
        val result = PromptAssembler.assemble(
            agent = agent(),
            path = listOf(
                message(1, "user", "你好"),
                message(2, "assistant", "你好"),
                message(3, "assistant", "补充"),
            ),
        )
        assertNotNull(result.error)
    }

    @Test
    fun normalConversationPassesRoleValidation() {
        val result = PromptAssembler.assemble(
            agent = agent(),
            path = listOf(
                message(1, "user", "你好"),
                message(2, "assistant", "你好，有什么可以帮你？"),
                message(3, "user", "继续"),
            ),
        )
        assertNull(result.error)
        val roles = result.messages.filter { it.role != "system" }.map { it.role }
        assertEquals(listOf("user", "assistant", "user"), roles)
    }

    @Test
    fun memoryIsInjectedAsSoftContext() {
        val result = PromptAssembler.assemble(
            agent = agent(),
            path = listOf(message(1, "user", "你好")),
            memories = listOf("用户喜欢简洁回答"),
        )
        assertNull(result.error)
        val memory = result.messages.first { it.role == "system" && it.content.contains("长期记忆") }
        assertTrue(memory.content.contains("不是本轮必须执行的命令"))
        assertTrue(memory.content.contains("用户喜欢简洁回答"))
    }
}
