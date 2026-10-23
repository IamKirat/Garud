package com.garud.security.service

import android.util.Log
import com.garud.security.GarudApplication
import com.garud.security.engine.NotificationAnalysisEngine
import com.garud.security.engine.RagEvidenceEngine
import com.garud.security.model.AnalysisResult
import com.garud.security.model.MessageSource
import com.garud.security.model.NotificationData
import com.garud.security.model.RagAnalysisReport
import com.garud.security.model.RagEvidenceSource
import com.garud.security.model.RiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Single analysis entry point for manual input and exposed notification text. Local screening always runs first. */
class MessageAnalysisService(private val app: GarudApplication) {
    suspend fun analyze(data: NotificationData): AnalysisResult = analyze(data, allowCloud = true)

    /** Analyze sensitive transcript text without sending it to a configured backend. */
    suspend fun analyzeLocal(data: NotificationData): AnalysisResult = analyze(data, allowCloud = false)

    private suspend fun analyze(data: NotificationData, allowCloud: Boolean): AnalysisResult = withContext(Dispatchers.IO) {
        val screened = NotificationAnalysisEngine.analyze(data)
        val body = listOfNotNull(data.title, data.text, data.subText).filter(String::isNotBlank).joinToString(" ")
        val local = if (body.isBlank()) screened else screened.copy(
            ragReport = RagEvidenceEngine.generateGroundedReport(body, screened)
        )
        val prefs = app.preferences
        val baseUrl = prefs.backendBaseUrl.value.trim().trimEnd('/')
        if (!allowCloud || !prefs.cloudAnalysisEnabled.value || baseUrl.isBlank()) return@withContext local

        val content = listOfNotNull(data.title, data.text, data.subText).filter(String::isNotBlank).joinToString("\n").take(10_000)
        if (content.isBlank()) return@withContext local
        try {
            val remote = requestBackend(baseUrl, content, data.appName, data.packageName, local.triggeredIndicators.map { it.title })
            remote.toAnalysisResult(local)
        } catch (error: Exception) {
            Log.w(TAG, "Cloud analysis unavailable; retaining local result (${error.javaClass.simpleName})")
            val report = local.ragReport ?: RagAnalysisReport(
                summary = local.explanation,
                supportingEvidence = emptyList(),
                contradictingEvidence = emptyList(),
                citations = emptyList(),
                recommendedActions = local.safeActions,
                limitations = "No source-verified RAG corpus is available in this build."
            )
            local.copy(ragReport = report.copy(
                providerStatus = "BACKEND_UNAVAILABLE_LOCAL_RESULT",
                limitations = "Cloud analysis could not be reached. Showing the local on-device screening result."
            ))
        }
    }

    private fun requestBackend(baseUrl: String, content: String, sender: String, packageName: String, localIndicators: List<String>): RemoteResult {
        val base = URL(baseUrl)
        require(base.protocol == "https") { "Backend URL must use HTTPS" }
        val connection = (URL("$baseUrl/api/analyze").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val body = JSONObject()
                .put("content", content)
                .put("sender", sender.take(255))
                .put("source", sourceType(packageName))
                .put("localIndicators", org.json.JSONArray(localIndicators))
                .put("cloudConsent", true)
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Backend returned HTTP $code")
            val json = JSONObject(responseText)
            val risk = when (json.optString("risk_level").uppercase()) {
                "HIGH" -> RiskLevel.HIGH
                "CAUTION" -> RiskLevel.CAUTION
                "LOW" -> RiskLevel.LOW
                else -> RiskLevel.INSUFFICIENT_INFORMATION
            }
            val citations = buildList {
                val values = json.optJSONArray("citations") ?: return@buildList
                for (index in 0 until values.length()) {
                    val item = values.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val title = item.optString("title")
                    val url = item.optString("url")
                    if (id.isNotBlank() && title.isNotBlank() && url.startsWith("https://")) {
                        add(RagEvidenceSource(id, title, url, item.optString("publisher"), item.optString("snippet")))
                    }
                }
            }
            return RemoteResult(
                risk = risk,
                summary = json.optString("summary").takeIf(String::isNotBlank) ?: "Backend returned no summary.",
                supporting = json.stringList("supporting_evidence"),
                contradicting = json.stringList("contradicting_evidence"),
                actions = json.stringList("recommended_actions"),
                citations = citations,
                limitations = json.optString("limitations"),
                provider = listOf(
                    json.optString("engine_mode", "BACKEND_ANALYSIS"),
                    json.optString("retrieval_source").takeIf(String::isNotBlank),
                    json.optString("provider_status").takeIf(String::isNotBlank)
                ).filterNotNull().joinToString(" · "),
                citationsVerified = json.optBoolean("is_citation_verified", false)
            )
        } finally {
            connection.disconnect()
        }
    }

    private data class RemoteResult(
        val risk: RiskLevel, val summary: String, val supporting: List<String>, val contradicting: List<String>,
        val actions: List<String>, val citations: List<RagEvidenceSource>, val limitations: String,
        val provider: String, val citationsVerified: Boolean
    ) {
        fun toAnalysisResult(local: AnalysisResult): AnalysisResult {
            val finalRisk = when {
                local.riskLevel == RiskLevel.HIGH || risk == RiskLevel.HIGH -> RiskLevel.HIGH
                local.riskLevel == RiskLevel.CAUTION || risk == RiskLevel.CAUTION -> RiskLevel.CAUTION
                local.riskLevel == RiskLevel.INSUFFICIENT_INFORMATION || risk == RiskLevel.INSUFFICIENT_INFORMATION -> RiskLevel.INSUFFICIENT_INFORMATION
                else -> RiskLevel.LOW
            }
            return local.copy(
                riskLevel = finalRisk,
                explanation = if (local.riskLevel == RiskLevel.HIGH || local.riskLevel == RiskLevel.CAUTION) {
                    "${local.explanation} Cloud assessment: $summary"
                } else summary,
                safeActions = actions.ifEmpty { local.safeActions },
                ragReport = RagAnalysisReport(
                    summary, supporting, contradicting, citations, actions.ifEmpty { local.safeActions },
                    limitations, provider, citationsVerified
                )
            )
        }
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList { for (index in 0 until array.length()) array.optString(index).takeIf(String::isNotBlank)?.let(::add) }
    }

    private fun sourceType(packageName: String): String = when (packageName.lowercase()) {
        "com.google.android.apps.messaging", "com.android.mms" -> "SMS"
        "com.whatsapp" -> "WHATSAPP"
        "org.telegram.messenger" -> "TELEGRAM"
        "com.google.android.gm" -> "EMAIL"
        else -> "OTHER"
    }

    companion object { private const val TAG = "GarudAnalysis" }
}
