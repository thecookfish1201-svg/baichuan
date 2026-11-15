package com.tongzhouweiji.baichuan.chat

import com.tongzhouweiji.baichuan.core.db.entity.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTreeTest {

    private fun message(
        id: Long,
        parentId: Long?,
        role: String = "user",
        deletedAt: Long? = null,
        conversationId: Long = 1L,
        createdAt: Long = id,
    ) = MessageEntity(
        id = id,
        conversationId = conversationId,
        role = role,
        content = "m$id",
        parentId = parentId,
        deletedAt = deletedAt,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun normalChainIsValid() {
        val all = listOf(
            message(1, null),
            message(2, 1, role = "assistant"),
            message(3, 2),
        )
        val path = MessageTree.pathTo(all, 1L, 3L)
        assertTrue(path.valid)
        assertEquals(listOf(1L, 2L, 3L), path.messages.map { it.id })
    }

    @Test
    fun missingParentIsRejectedInsteadOfTruncated() {
        // 父节点被物理删除或从未写入：旧实现会静默截断，这里必须 invalid。
        val all = listOf(message(2, 99, role = "assistant"))
        val path = MessageTree.pathTo(all, 1L, 2L)
        assertFalse(path.valid)
        assertTrue(path.messages.isEmpty())
        assertTrue(path.reason!!.contains("缺失"))
    }

    @Test
    fun deletedAncestorIsRejected() {
        val all = listOf(
            message(1, null, deletedAt = 100L),
            message(2, 1, role = "assistant"),
        )
        val path = MessageTree.pathTo(all, 1L, 2L)
        assertFalse(path.valid)
        assertTrue(path.reason!!.contains("已删除"))
    }

    @Test
    fun crossConversationParentIsRejected() {
        val all = listOf(
            message(1, null, conversationId = 2L),
            message(2, 1, role = "assistant"),
        )
        val path = MessageTree.pathTo(all, 1L, 2L)
        assertFalse(path.valid)
        assertTrue(path.reason!!.contains("跨会话"))
    }

    @Test
    fun cycleIsRejected() {
        val all = listOf(
            message(1, 2),
            message(2, 1, role = "assistant"),
        )
        val path = MessageTree.pathTo(all, 1L, 1L)
        assertFalse(path.valid)
        assertTrue(path.reason!!.contains("循环"))
    }

    @Test
    fun descendantsAndAncestorFallbackWork() {
        val all = listOf(
            message(1, null),
            message(2, 1, role = "assistant"),
            message(3, 2),
            message(4, 3, role = "assistant"),
            message(5, 1, role = "assistant"), // 另一分支
        )
        val descendants = MessageTree.descendants(all, 2L).map { it.id }.toSet()
        assertEquals(setOf(3L, 4L), descendants)

        val byId = all.associateBy { it.id }
        val fallback = MessageTree.nearestValidAncestorId(all.first { it.id == 2L }, byId)
        assertEquals(1L, fallback)
        assertNull(MessageTree.nearestValidAncestorId(all.first { it.id == 1L }, byId))
    }

    @Test
    fun repairLeafFallsBackToLatestVisibleMessage() {
        val all = listOf(
            message(1, null, createdAt = 1),
            message(2, 1, role = "assistant", createdAt = 2, deletedAt = 50L),
            message(3, 1, role = "assistant", createdAt = 3),
        )
        assertEquals(1L, MessageTree.repairLeaf(all, 1L))
        assertEquals(3L, MessageTree.repairLeaf(all, 2L))
        assertEquals(3L, MessageTree.repairLeaf(all, null))
    }

    @Test
    fun emptyLeafIsValidEmptyPath() {
        val path = MessageTree.pathTo(emptyList(), 1L, null)
        assertTrue(path.valid)
        assertTrue(path.messages.isEmpty())
    }
}
