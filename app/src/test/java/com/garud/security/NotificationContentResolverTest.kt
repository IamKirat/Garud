package com.garud.security

import com.garud.security.engine.NotificationAnalysisEngine
import com.garud.security.model.NotificationData
import com.garud.security.model.RiskLevel
import com.garud.security.service.NotificationContentResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationContentResolverTest {

    @Test
    fun messagingStyleContentIsPassedToTheRealAnalysisEngine() {
        val body = NotificationContentResolver.resolveBody(
            bigText = null,
            messagingLines = listOf("Please share your OTP with our support agent to cancel this debit."),
            text = "1 new message",
            textLines = emptyList()
        )
        val result = NotificationAnalysisEngine.analyze(
            NotificationData("com.example.messaging", "Messages", "Bank Support", body)
        )

        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertTrue(result.explanation.isNotBlank())
    }

    @Test
    fun prefersMessagingContentOverNotificationSummary() {
        assertEquals(
            "Latest private message",
            NotificationContentResolver.resolveBody(
                bigText = "2 new messages",
                messagingLines = listOf("Latest private message"),
                text = "2 new messages",
                textLines = emptyList()
            )
        )
    }

    @Test
    fun fallsBackToExpandedTextThenPreviewThenTextLines() {
        assertEquals("Expanded body", NotificationContentResolver.resolveBody("Expanded body", emptyList(), "Preview", emptyList()))
        assertEquals("Preview", NotificationContentResolver.resolveBody(null, emptyList(), "Preview", listOf("line")))
        assertEquals("line one\nline two", NotificationContentResolver.resolveBody(null, emptyList(), null, listOf("line one", "line two")))
    }

    @Test
    fun returnsNullWhenAndroidExposesNoUsableText() {
        assertNull(NotificationContentResolver.resolveBody(" ", emptyList(), null, listOf("\n")))
    }

    @Test
    fun capsTransientNotificationContentLength() {
        val result = NotificationContentResolver.resolveBody(null, emptyList(), "x".repeat(12_000), emptyList())
        assertEquals(10_000, result?.length)
    }
}
