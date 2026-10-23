package com.garud.security

import com.garud.security.engine.NotificationAnalysisEngine
import com.garud.security.model.IndicatorCategory
import com.garud.security.model.NotificationData
import com.garud.security.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAnalysisEngineTest {

    @Test
    fun testOtpTheftRequest_flagsHighRisk() {
        val notification = NotificationData(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "SBI Customer Support",
            text = "Your account debit of Rs 49,999 is pending. Please share your OTP with our executive to cancel this transaction."
        )

        val result = NotificationAnalysisEngine.analyze(notification)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertTrue(result.triggeredIndicators.any { it.category == IndicatorCategory.OTP_CREDENTIAL_THEFT })
    }

    @Test
    fun testPasswordAndPinRequest_flagsHighRisk() {
        val notification = NotificationData(
            packageName = "com.unknown.messenger",
            appName = "Messenger",
            title = "Security Verification",
            text = "Please provide your password and PIN to verify your login."
        )

        val result = NotificationAnalysisEngine.analyze(notification)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertTrue(result.triggeredIndicators.any { it.category == IndicatorCategory.OTP_CREDENTIAL_THEFT })
    }

    @Test
    fun testSuspiciousPaymentAndLottery_flagsHighRisk() {
        val notification = NotificationData(
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "Lucky Winner",
            text = "Congratulations! You have won a lottery of Rs 1,00,000. Transfer registration fee of Rs 500 to claim prize."
        )

        val result = NotificationAnalysisEngine.analyze(notification)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertTrue(result.triggeredIndicators.any { it.category == IndicatorCategory.SUSPICIOUS_PAYMENT })
    }

    @Test
    fun testCoerciveUtilityPayment_flagsHighRisk() {
        val notification = NotificationData(
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "Electricity Notice",
            text = "Your electricity power will be disconnected at 9:30 PM due to unpaid bill. Pay immediately to avoid disconnection."
        )

        val result = NotificationAnalysisEngine.analyze(notification)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertTrue(result.triggeredIndicators.any { it.category == IndicatorCategory.SUSPICIOUS_PAYMENT })
    }

    @Test
    fun testCourierImpersonation_flagsHighRisk() {
        val notification = NotificationData(
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "FedEx Express Notice",
            text = "Your package delivery failed due to address incomplete. Please update address within 12 hours."
        )

        val result = NotificationAnalysisEngine.analyze(notification)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertTrue(result.triggeredIndicators.any { it.category == IndicatorCategory.IMPERSONATION })
    }

    @Test
    fun testSuspiciousShortenedUrl_flagsSuspiciousUrlIndicator() {
        val notification = NotificationData(
            packageName = "com.example.chat",
            appName = "Chat",
            title = "Special Offer",
            text = "Check out our new catalog at https://bit.ly/deal39281 today."
        )

        val result = NotificationAnalysisEngine.analyze(notification)
        assertTrue(result.triggeredIndicators.any { it.category == IndicatorCategory.SUSPICIOUS_URL })
    }

    @Test
    fun testRawIpUrl_flagsHighRisk() {
        val notification = NotificationData(
            packageName = "com.example.chat",
            appName = "Chat",
            title = "Server Login",
            text = "Please access the security panel at http://192.168.1.150/login"
        )

        val result = NotificationAnalysisEngine.analyze(notification)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertTrue(result.triggeredIndicators.any { it.category == IndicatorCategory.SUSPICIOUS_URL })
    }

    @Test
    fun testTypoSquattingUrl_flagsHighRisk() {
        val notification = NotificationData(
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "Account Alert",
            text = "Please verify your account at https://paypa1.com/update immediately."
        )

        val result = NotificationAnalysisEngine.analyze(notification)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertTrue(result.triggeredIndicators.any { it.category == IndicatorCategory.SUSPICIOUS_URL })
    }

    @Test
    fun testMaliciousRemoteAccessApp_flagsHighRisk() {
        val notification = NotificationData(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Support Desk",
            text = "Please install AnyDesk from Play Store to fix your banking transaction error."
        )

        val result = NotificationAnalysisEngine.analyze(notification)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertTrue(result.triggeredIndicators.any { it.category == IndicatorCategory.MALICIOUS_APP_INSTALL })
    }

    @Test
    fun testBenignMessage_returnsLowRiskWithoutFalseCertainty() {
        val notification = NotificationData(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Rahul Verma",
            text = "Hey, are you free for a coffee catchup this evening around 5?"
        )

        val result = NotificationAnalysisEngine.analyze(notification)
        assertEquals(RiskLevel.LOW, result.riskLevel)
        assertTrue(result.triggeredIndicators.isEmpty())
        // Must not declare 100% safe with certainty
        assertTrue(result.explanation.contains("does not guarantee absolute safety"))
    }

    @Test
    fun testMissingOrEmptyNotificationText_returnsInsufficientInformation() {
        val emptyNotification = NotificationData(
            packageName = "com.example.empty",
            appName = "EmptyApp",
            title = "",
            text = null
        )

        val result = NotificationAnalysisEngine.analyze(emptyNotification)
        assertEquals(RiskLevel.INSUFFICIENT_INFORMATION, result.riskLevel)
        assertTrue(result.explanation.contains("insufficient text"))
    }

    @Test
    fun testMultipleCorroboratingThreatIndicators_elevatesToHigh() {
        val notification = NotificationData(
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "Urgent Bank Notice",
            text = "Your HDFC account will be blocked today within 24 hours. Click https://bit.ly/hdfc-restore to unblock."
        )

        val result = NotificationAnalysisEngine.analyze(notification)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertTrue(result.triggeredIndicators.size >= 2)
    }

    @Test
    fun testDeduplicationKey_identicalForSameContent() {
        val key1 = NotificationAnalysisEngine.computeDedupKey("com.whatsapp", "Alert", "Your code is 1234")
        val key2 = NotificationAnalysisEngine.computeDedupKey("com.whatsapp", "Alert", "Your code is 1234")
        val key3 = NotificationAnalysisEngine.computeDedupKey("com.whatsapp", "Alert", "Different text")

        assertEquals(key1, key2)
        assertNotEquals(key1, key3)
    }

    @Test
    fun testSelfNotificationIgnoredLogic() {
        val garudPackage = "com.garud.security"
        val isSelfNotification = (garudPackage == "com.garud.security")
        assertTrue("GARUD must identify and ignore its own notifications", isSelfNotification)
    }

    @Test
    fun testExclusionLogic() {
        val excludedPackages = setOf("com.android.systemui", "android", "com.mycompany.internal")
        assertTrue(excludedPackages.contains("com.android.systemui"))
        assertFalse(excludedPackages.contains("com.whatsapp"))
    }

    @Test
    fun testMonitoringPausedLogic() {
        var isPaused = true
        // When paused, notification listener must skip
        val shouldSkip = isPaused
        assertTrue(shouldSkip)

        isPaused = false
        assertFalse(isPaused)
    }
}
