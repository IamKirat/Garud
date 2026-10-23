package com.garud.security.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class UrlReputationResult(
    val target: String,
    val status: String,
    val flagged: Boolean?,
    val malicious: Int?,
    val suspicious: Int?,
    val harmless: Int?,
    val undetected: Int?,
    val source: String,
    val details: String,
    val checkedAt: Long
)

object UrlReputationClient {
    fun normalize(input: String): String? = runCatching {
        require(input.length <= 2048)
        val uri = URI(input.trim()).normalize()
        require(uri.scheme?.lowercase() in setOf("http", "https"))
        require(!uri.host.isNullOrBlank() && uri.rawUserInfo == null)
        URI(uri.scheme.lowercase(), null, uri.host.lowercase(), uri.port, uri.path.ifBlank { "/" }, uri.query, null).toASCIIString()
    }.getOrNull()

    suspend fun scan(baseAddress: String, input: String): UrlReputationResult = withContext(Dispatchers.IO) {
        val target = normalize(input) ?: throw IllegalArgumentException("Enter a valid HTTP or HTTPS URL without embedded username or password.")
        if (baseAddress.isBlank()) throw IllegalStateException(
            "No GARUD server address is set. In Settings, enter the public HTTPS base URL of your deployed GARUD backend. The VirusTotal API key is not a URL."
        )
        val base = runCatching { URI(baseAddress.trim().trimEnd('/')) }.getOrNull()
            ?: throw IllegalStateException("Enter a server address such as https://api.your-domain.com.")
        require(base.scheme.equals("https", ignoreCase = true) && !base.host.isNullOrBlank() && base.rawUserInfo == null) {
            "Use the public HTTPS base URL of a deployed GARUD server (for example https://api.your-domain.com), not an API key. It must provide /api/analyze/url."
        }
        val connection = (URL("${base.toASCIIString()}/api/analyze/url").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 25_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(JSONObject().put("url", target).put("consent", true).toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("GARUD backend returned HTTP $code${if (code == 429) " (rate limited)" else ""}.")
            val json = JSONObject(body)
            val stats = json.optJSONObject("stats")
            UrlReputationResult(
                target = json.optString("target", target), status = json.optString("status", "unknown"),
                flagged = if (json.isNull("isFlagged")) null else json.optBoolean("isFlagged"),
                malicious = stats?.optInt("malicious"), suspicious = stats?.optInt("suspicious"),
                harmless = stats?.optInt("harmless"), undetected = stats?.optInt("undetected"),
                source = json.optString("source", "GARUD backend"), details = json.optString("details", "No provider details were returned."),
                checkedAt = System.currentTimeMillis()
            )
        } finally { connection.disconnect() }
    }
}
