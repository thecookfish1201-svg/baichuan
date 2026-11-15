package com.tongzhouweiji.baichuan.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMappingTest {

    @Test
    fun authErrorHidesServerBodyAndKeepsDiagnostic() {
        val exception = OpenAiCompatibleAdapter.httpError(
            code = 401,
            body = """{"error":{"message":"Invalid API key sk-secret-123456"}}""",
            requestId = "req-abc",
        )
        assertTrue(exception.message.contains("认证失败"))
        assertFalse(exception.message.contains("sk-secret"))
        assertNotNull(exception.diagnostic)
        assertTrue(exception.diagnostic!!.contains("request_id=req-abc"))
        assertTrue(exception.diagnostic.contains("HTTP 401"))
    }

    @Test
    fun invalidRoleSequenceGetsActionableMessage() {
        val exception = OpenAiCompatibleAdapter.httpError(
            code = 400,
            body = """{"error":{"message":"an assistant message must follow a user message"}}""",
        )
        assertTrue(exception.message.contains("分支不完整"))
        assertFalse(exception.message.contains("assistant message"))
    }

    @Test
    fun rateLimitIsMappedToActionableText() {
        val exception = OpenAiCompatibleAdapter.httpError(429, "")
        assertTrue(exception.message.contains("频繁"))
    }

    @Test
    fun streamErrorMapsContentRisk() {
        val text = OpenAiCompatibleAdapter.streamErrorMessage("Content Exists Risk")
        assertTrue(text.contains("安全审核"))
    }

    @Test
    fun streamErrorMapsInvalidSequence() {
        val text = OpenAiCompatibleAdapter.streamErrorMessage(
            "an assistant message must follow a user message",
        )
        assertTrue(text.contains("分支不完整"))
    }
}
