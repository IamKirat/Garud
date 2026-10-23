package com.garud.security

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.garud.security.data.FindingRepository
import com.garud.security.data.GarudDatabaseHelper
import com.garud.security.data.GarudPreferences
import com.garud.security.model.RiskLevel
import com.garud.security.service.AlertNotificationManager
import com.garud.security.service.GarudNotificationListenerService
import com.garud.security.service.MessageAnalysisService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GarudApplication : Application() {

    enum class NotificationStage { WAITING, RECEIVED, ANALYZING, COMPLETE, FAILED }

    data class NotificationPipelineState(
        val stage: NotificationStage = NotificationStage.WAITING,
        val sourceApp: String? = null,
        val changedAt: Long = System.currentTimeMillis(),
        val riskLevel: RiskLevel? = null
    )

    lateinit var preferences: GarudPreferences
        private set

    lateinit var repository: FindingRepository
        private set

    lateinit var alertManager: AlertNotificationManager
        private set

    lateinit var messageAnalysisService: MessageAnalysisService
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferences = GarudPreferences(this)
        val dbHelper = GarudDatabaseHelper(this)
        repository = FindingRepository(dbHelper, preferences)
        alertManager = AlertNotificationManager(this)
        messageAnalysisService = MessageAnalysisService(this)
    }

    companion object {
        lateinit var instance: GarudApplication
            private set

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _notificationPipeline = MutableStateFlow(NotificationPipelineState())
        val notificationPipeline: StateFlow<NotificationPipelineState> = _notificationPipeline.asStateFlow()

        fun setServiceConnected(connected: Boolean) {
            _isServiceConnected.value = connected
        }

        fun setNotificationPipelineState(
            stage: NotificationStage,
            sourceApp: String? = _notificationPipeline.value.sourceApp,
            riskLevel: RiskLevel? = null
        ) {
            _notificationPipeline.value = NotificationPipelineState(stage, sourceApp, System.currentTimeMillis(), riskLevel)
        }

        /**
         * Checks whether Android Notification Access has been granted to GARUD.
         */
        fun isNotificationAccessGranted(context: Context): Boolean {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            return enabledListeners.contains(context.packageName)
        }
    }
}
