package com.garud.security.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.garud.security.model.AnalysisResult
import com.garud.security.model.IndicatorCategory
import com.garud.security.model.RagAnalysisReport
import com.garud.security.model.RagEvidenceSource
import com.garud.security.model.RiskLevel
import com.garud.security.model.TriggeredIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * SQLite open helper for persisting minimal finding records without sensitive message content.
 */
class GarudDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_FINDINGS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_TIMESTAMP INTEGER,
                $COL_PACKAGE TEXT,
                $COL_APP_NAME TEXT,
                $COL_RISK_LEVEL TEXT,
                $COL_TITLE_SNIPPET TEXT,
                $COL_EXPLANATION TEXT,
                $COL_INDICATORS_JSON TEXT,
                $COL_ACTIONS_JSON TEXT,
                $COL_DEDUP_KEY TEXT,
                $COL_RAG_JSON TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_findings_timestamp ON $TABLE_FINDINGS ($COL_TIMESTAMP DESC)")
        db.execSQL("CREATE INDEX idx_findings_dedup ON $TABLE_FINDINGS ($COL_DEDUP_KEY)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_FINDINGS ADD COLUMN $COL_RAG_JSON TEXT")
            } catch (_: Exception) {
                // Column might already exist
            }
        }
    }

    companion object {
        const val DATABASE_NAME = "garud_findings.db"
        const val DATABASE_VERSION = 2

        const val TABLE_FINDINGS = "findings"
        const val COL_ID = "id"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_PACKAGE = "source_package"
        const val COL_APP_NAME = "source_app_name"
        const val COL_RISK_LEVEL = "risk_level"
        const val COL_TITLE_SNIPPET = "title_snippet"
        const val COL_EXPLANATION = "explanation"
        const val COL_INDICATORS_JSON = "indicators_json"
        const val COL_ACTIONS_JSON = "actions_json"
        const val COL_DEDUP_KEY = "dedup_key"
        const val COL_RAG_JSON = "rag_json"
    }
}

/**
 * Repository interface & implementation for threat findings.
 */
class FindingRepository(
    private val dbHelper: GarudDatabaseHelper,
    private val preferences: GarudPreferences,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val _findings = MutableStateFlow<List<AnalysisResult>>(emptyList())
    val findings: StateFlow<List<AnalysisResult>> = _findings.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    init {
        refreshFindings()
    }

    fun refreshFindings() {
        scope.launch {
            _isLoading.value = true
            try {
                _findings.value = loadAllFromDb()
                _loadError.value = null
            } catch (error: Exception) {
                _loadError.value = error.message ?: "History could not be loaded."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun insertFinding(result: AnalysisResult) {
        if (!preferences.isHistoryEnabled.value) {
            // History retention is disabled by user
            return
        }

        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(GarudDatabaseHelper.COL_ID, result.id)
            put(GarudDatabaseHelper.COL_TIMESTAMP, result.timestamp)
            put(GarudDatabaseHelper.COL_PACKAGE, result.sourcePackage)
            put(GarudDatabaseHelper.COL_APP_NAME, result.sourceAppName)
            put(GarudDatabaseHelper.COL_RISK_LEVEL, result.riskLevel.name)
            put(GarudDatabaseHelper.COL_TITLE_SNIPPET, result.titleSnippet)
            put(GarudDatabaseHelper.COL_EXPLANATION, result.explanation)
            put(GarudDatabaseHelper.COL_INDICATORS_JSON, serializeIndicators(result.triggeredIndicators))
            put(GarudDatabaseHelper.COL_ACTIONS_JSON, serializeActions(result.safeActions))
            put(GarudDatabaseHelper.COL_DEDUP_KEY, result.dedupKey)
            put(GarudDatabaseHelper.COL_RAG_JSON, serializeRagReport(result.ragReport))
        }

        db.insertWithOnConflict(
            GarudDatabaseHelper.TABLE_FINDINGS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )

        refreshFindings()
    }

    fun deleteFinding(id: String) {
        val db = dbHelper.writableDatabase
        db.delete(
            GarudDatabaseHelper.TABLE_FINDINGS,
            "${GarudDatabaseHelper.COL_ID} = ?",
            arrayOf(id)
        )
        refreshFindings()
    }

    fun clearAllFindings() {
        val db = dbHelper.writableDatabase
        db.delete(GarudDatabaseHelper.TABLE_FINDINGS, null, null)
        refreshFindings()
    }

    fun getFindingById(id: String): AnalysisResult? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            GarudDatabaseHelper.TABLE_FINDINGS,
            null,
            "${GarudDatabaseHelper.COL_ID} = ?",
            arrayOf(id),
            null, null, null
        )
        return cursor.use {
            if (it.moveToFirst()) parseCursor(it) else null
        }
    }

    fun isRecentDuplicate(dedupKey: String, withinMillis: Long = 60_000): Boolean {
        val cutoff = System.currentTimeMillis() - withinMillis
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT COUNT(*) FROM ${GarudDatabaseHelper.TABLE_FINDINGS}
            WHERE ${GarudDatabaseHelper.COL_DEDUP_KEY} = ? AND ${GarudDatabaseHelper.COL_TIMESTAMP} > ?
            """.trimIndent(),
            arrayOf(dedupKey, cutoff.toString())
        )
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) > 0 else false
        }
    }

    private fun loadAllFromDb(): List<AnalysisResult> {
        val results = mutableListOf<AnalysisResult>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            GarudDatabaseHelper.TABLE_FINDINGS,
            null,
            null, null, null, null,
            "${GarudDatabaseHelper.COL_TIMESTAMP} DESC",
            "100"
        )
        cursor.use {
            while (it.moveToNext()) {
                results.add(parseCursor(it))
            }
        }
        return results
    }

    private fun parseCursor(cursor: Cursor): AnalysisResult {
        val id = cursor.getString(cursor.getColumnIndexOrThrow(GarudDatabaseHelper.COL_ID))
        val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(GarudDatabaseHelper.COL_TIMESTAMP))
        val pkg = cursor.getString(cursor.getColumnIndexOrThrow(GarudDatabaseHelper.COL_PACKAGE))
        val appName = cursor.getString(cursor.getColumnIndexOrThrow(GarudDatabaseHelper.COL_APP_NAME))
        val riskLevelStr = cursor.getString(cursor.getColumnIndexOrThrow(GarudDatabaseHelper.COL_RISK_LEVEL))
        val snippet = cursor.getString(cursor.getColumnIndexOrThrow(GarudDatabaseHelper.COL_TITLE_SNIPPET))
        val explanation = cursor.getString(cursor.getColumnIndexOrThrow(GarudDatabaseHelper.COL_EXPLANATION))
        val indJson = cursor.getString(cursor.getColumnIndexOrThrow(GarudDatabaseHelper.COL_INDICATORS_JSON))
        val actJson = cursor.getString(cursor.getColumnIndexOrThrow(GarudDatabaseHelper.COL_ACTIONS_JSON))
        val dedupKey = cursor.getString(cursor.getColumnIndexOrThrow(GarudDatabaseHelper.COL_DEDUP_KEY))

        val ragJsonIdx = cursor.getColumnIndex(GarudDatabaseHelper.COL_RAG_JSON)
        val ragJson = if (ragJsonIdx != -1 && !cursor.isNull(ragJsonIdx)) cursor.getString(ragJsonIdx) else null

        val riskLevel = try {
            RiskLevel.valueOf(riskLevelStr)
        } catch (_: Exception) {
            RiskLevel.INSUFFICIENT_INFORMATION
        }

        return AnalysisResult(
            id = id,
            timestamp = timestamp,
            sourcePackage = pkg,
            sourceAppName = appName,
            riskLevel = riskLevel,
            triggeredIndicators = deserializeIndicators(indJson),
            explanation = explanation,
            safeActions = deserializeActions(actJson),
            titleSnippet = snippet,
            dedupKey = dedupKey,
            ragReport = deserializeRagReport(ragJson)
        )
    }

    private fun serializeIndicators(indicators: List<TriggeredIndicator>): String {
        val array = JSONArray()
        for (item in indicators) {
            val obj = JSONObject().apply {
                put("category", item.category.name)
                put("title", item.title)
                put("detail", item.detail)
                put("severity", item.severity.name)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeIndicators(json: String?): List<TriggeredIndicator> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<TriggeredIndicator>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val catStr = obj.optString("category", IndicatorCategory.MULTIPLE_INDICATORS.name)
                val category = try {
                    IndicatorCategory.valueOf(catStr)
                } catch (_: Exception) {
                    IndicatorCategory.MULTIPLE_INDICATORS
                }
                val severityStr = obj.optString("severity", RiskLevel.CAUTION.name)
                val severity = try {
                    RiskLevel.valueOf(severityStr)
                } catch (_: Exception) {
                    RiskLevel.CAUTION
                }
                list.add(
                    TriggeredIndicator(
                        category = category,
                        title = obj.optString("title", ""),
                        detail = obj.optString("detail", ""),
                        severity = severity
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore parse errors
        }
        return list
    }

    private fun serializeActions(actions: List<String>): String {
        val array = JSONArray()
        actions.forEach { array.put(it) }
        return array.toString()
    }

    private fun deserializeActions(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        } catch (_: Exception) {
            // Ignore parse errors
        }
        return list
    }

    private fun serializeRagReport(report: RagAnalysisReport?): String? {
        if (report == null) return null
        return try {
            val obj = JSONObject().apply {
                put("summary", report.summary)
                put("limitations", report.limitations)
                put("providerStatus", report.providerStatus)
                put("isVerifiedCitation", report.isVerifiedCitation)

                val suppArr = JSONArray()
                report.supportingEvidence.forEach { suppArr.put(it) }
                put("supportingEvidence", suppArr)

                val contArr = JSONArray()
                report.contradictingEvidence.forEach { contArr.put(it) }
                put("contradictingEvidence", contArr)

                val actArr = JSONArray()
                report.recommendedActions.forEach { actArr.put(it) }
                put("recommendedActions", actArr)

                val citArr = JSONArray()
                report.citations.forEach { cit ->
                    val cObj = JSONObject().apply {
                        put("id", cit.id)
                        put("title", cit.title)
                        put("url", cit.url)
                        put("publisher", cit.publisher)
                        put("relevantSnippet", cit.relevantSnippet)
                        put("timestamp", cit.timestamp)
                    }
                    citArr.put(cObj)
                }
                put("citations", citArr)
            }
            obj.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun deserializeRagReport(json: String?): RagAnalysisReport? {
        if (json.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(json)
            val suppList = mutableListOf<String>()
            val suppArr = obj.optJSONArray("supportingEvidence")
            if (suppArr != null) {
                for (i in 0 until suppArr.length()) suppList.add(suppArr.getString(i))
            }

            val contList = mutableListOf<String>()
            val contArr = obj.optJSONArray("contradictingEvidence")
            if (contArr != null) {
                for (i in 0 until contArr.length()) contList.add(contArr.getString(i))
            }

            val actList = mutableListOf<String>()
            val actArr = obj.optJSONArray("recommendedActions")
            if (actArr != null) {
                for (i in 0 until actArr.length()) actList.add(actArr.getString(i))
            }

            val citList = mutableListOf<RagEvidenceSource>()
            val citArr = obj.optJSONArray("citations")
            if (citArr != null) {
                for (i in 0 until citArr.length()) {
                    val c = citArr.getJSONObject(i)
                    citList.add(
                        RagEvidenceSource(
                            id = c.optString("id", ""),
                            title = c.optString("title", ""),
                            url = c.optString("url", ""),
                            publisher = c.optString("publisher", ""),
                            relevantSnippet = c.optString("relevantSnippet", ""),
                            timestamp = c.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }

            RagAnalysisReport(
                summary = obj.optString("summary", ""),
                supportingEvidence = suppList,
                contradictingEvidence = contList,
                citations = citList,
                recommendedActions = actList,
                limitations = obj.optString("limitations", ""),
                providerStatus = obj.optString("providerStatus", "LOCAL_EVIDENCE_RETRIEVAL"),
                isVerifiedCitation = obj.optBoolean("isVerifiedCitation", true)
            )
        } catch (_: Exception) {
            null
        }
    }
}
