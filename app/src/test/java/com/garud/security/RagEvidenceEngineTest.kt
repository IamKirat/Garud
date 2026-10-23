package com.garud.security

import com.garud.security.engine.NotificationAnalysisEngine
import com.garud.security.engine.RagEvidenceEngine
import com.garud.security.model.MessageSource
import com.garud.security.model.RagEvidenceSource
import com.garud.security.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RagEvidenceEngineTest {

    @Test
    fun testExtractEntities_identifiesThreatKeywords() {
        val message = "Dear customer, your SBI account OTP is required. Download AnyDesk to resolve pending transaction."
        val entities = RagEvidenceEngine.extractEntities(message)

        assertTrue(entities.contains("otp"))
        assertTrue(entities.contains("sbi"))
        assertTrue(entities.contains("anydesk"))
    }

    @Test
    fun testRetrieveEvidence_doesNotCiteUnreviewedSourceSummaries() {
        val message = "Please share your secret OTP with our bank representative to cancel the debit."
        val evidence = RagEvidenceEngine.retrieveEvidence(message)

        assertTrue(evidence.isEmpty())
    }

    @Test
    fun testRetrieveEvidence_doesNotCiteUnreviewedRemoteAccessSummary() {
        val message = "Kindly install AnyDesk from Play Store to fix your KYC error immediately."
        val evidence = RagEvidenceEngine.retrieveEvidence(message)

        assertTrue(evidence.isEmpty())
    }

    @Test
    fun testRetrieveEvidence_doesNotCiteUnreviewedUtilitySummary() {
        val message = "Your electricity power will be disconnected at 9:30 PM due to unpaid bill. Pay now."
        val evidence = RagEvidenceEngine.retrieveEvidence(message)

        assertTrue(evidence.isEmpty())
    }

    @Test
    fun testRetrieveEvidence_doesNotCiteUnreviewedCourierSummary() {
        val message = "FedEx shipment failed due to wrong address. Click bit.ly/fedx-track to update."
        val evidence = RagEvidenceEngine.retrieveEvidence(message)

        assertTrue(evidence.isEmpty())
    }

    @Test
    fun testValidateCitations_acceptsValidRetrievedIds() {
        val actualRetrieved = listOf(
            RagEvidenceSource("TEST-1", "Test fixture", "https://example.test/source", "Test", "Synthetic test fixture, not a real advisory.")
        )
        val isValid = RagEvidenceEngine.validateCitations(actualRetrieved, actualRetrieved)
        assertTrue(isValid)
    }

    @Test
    fun testValidateCitations_rejectsFabricatedOrInventedSourceId() {
        val actualRetrieved = listOf(
            RagEvidenceSource("TEST-1", "Test fixture", "https://example.test/source", "Test", "Synthetic test fixture, not a real advisory.")
        )
        val fabricatedSource = listOf(
            RagEvidenceSource(
                id = "FAKE-INVENTED-ID-999",
                title = "Fabricated Source",
                publisher = "Unknown",
                url = "https://fake.example.com",
                relevantSnippet = "Fake snippet"
            )
        )

        val isValid = RagEvidenceEngine.validateCitations(fabricatedSource, actualRetrieved)
        assertFalse("Must reject unverified or hallucinated citation IDs", isValid)
    }

    @Test
    fun testGenerateGroundedReport_disclosesMissingReviewedSourceCorpus() {
        val text = "Electricity bill overdue. Power disconnected tonight. Pay via UPI."
        val localResult = NotificationAnalysisEngine.analyzeCustomMessage(
            text = text,
            sender = "Electricity-Dept",
            source = MessageSource.SMS,
            includeRagEvidence = true
        )

        val report = localResult.ragReport
        assertNotNull(report)
        assertTrue(report!!.citations.isEmpty())
        assertTrue(report.supportingEvidence.isEmpty())
        assertEquals("LOCAL_RULES_SOURCE_REVIEW_PENDING", report.providerStatus)
        assertTrue(report.limitations.contains("No source-verified RAG corpus"))
    }

    @Test
    fun testAnalyzeCustomMessage_benignMessage_returnsLowRisk() {
        val benignText = "Hey, are you free for a coffee catchup this evening around 5?"
        val result = NotificationAnalysisEngine.analyzeCustomMessage(
            text = benignText,
            sender = "Priya",
            source = MessageSource.WHATSAPP,
            includeRagEvidence = true
        )

        assertEquals(RiskLevel.LOW, result.riskLevel)
        assertTrue(result.triggeredIndicators.isEmpty())
    }
}
