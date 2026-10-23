package com.garud.security.model

import java.util.UUID

/**
 * Categorical risk levels for notification threats.
 */
enum class RiskLevel {
    HIGH,
    CAUTION,
    LOW,
    INSUFFICIENT_INFORMATION
}

/**
 * Distinct indicator categories flagged by the rule engine.
 */
enum class IndicatorCategory(val displayName: String) {
    OTP_CREDENTIAL_THEFT("OTP & Credential Theft Risk"),
    SUSPICIOUS_PAYMENT("Suspicious Payment / Fee Demand"),
    URGENT_COERCION("Urgent Coercion & Pressure"),
    IMPERSONATION("Brand / Entity Impersonation"),
    SUSPICIOUS_URL("Suspicious / Obfuscated URL"),
    MALICIOUS_APP_INSTALL("App / Malicious File Download"),
    MULTIPLE_INDICATORS("Compound Threat Indicators")
}

/**
 * A specific indicator triggered during analysis.
 */
data class TriggeredIndicator(
    val category: IndicatorCategory,
    val title: String,
    val detail: String,
    val severity: RiskLevel
)

/**
 * Complete analysis result for an inspected notification.
 * Sensitive notification body text is NOT stored here to protect privacy.
 */
data class AnalysisResult(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val sourcePackage: String,
    val sourceAppName: String,
    val riskLevel: RiskLevel,
    val triggeredIndicators: List<TriggeredIndicator>,
    val explanation: String,
    val safeActions: List<String>,
    val titleSnippet: String,
    val dedupKey: String,
    val ragReport: RagAnalysisReport? = null
)

/**
 * Raw notification data extracted from Android status bar or manual input.
 */
data class NotificationData(
    val packageName: String,
    val appName: String,
    val title: String?,
    val text: String?,
    val subText: String? = null,
    val postTime: Long = System.currentTimeMillis(),
    val notificationId: Int = 0,
    val tag: String? = null
)

/**
 * Source types for manual message inspection.
 */
enum class MessageSource(val displayName: String, val defaultPackage: String) {
    SMS("SMS / Text", "com.google.android.apps.messaging"),
    WHATSAPP("WhatsApp", "com.whatsapp"),
    TELEGRAM("Telegram", "org.telegram.messenger"),
    EMAIL("Email", "com.google.android.gm"),
    OTHER("Other / Unknown", "com.garud.security.manual")
}

/**
 * Verified evidence source retrieved for RAG threat analysis.
 */
data class RagEvidenceSource(
    val id: String,
    val title: String,
    val url: String,
    val publisher: String,
    val relevantSnippet: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Structured RAG / AI threat analysis report with verified citations.
 */
data class RagAnalysisReport(
    val summary: String,
    val supportingEvidence: List<String>,
    val contradictingEvidence: List<String>,
    val citations: List<RagEvidenceSource>,
    val recommendedActions: List<String>,
    val limitations: String,
    val providerStatus: String = "LOCAL_EVIDENCE_RETRIEVAL",
    val isVerifiedCitation: Boolean = true
)
