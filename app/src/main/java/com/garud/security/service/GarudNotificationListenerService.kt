package com.garud.security.service

import android.app.Notification
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.util.LruCache
import com.garud.security.GarudApplication
import com.garud.security.engine.NotificationAnalysisEngine
import com.garud.security.model.NotificationData
import com.garud.security.model.RiskLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Receives only notification content exposed by Android, then screens it locally. */
class GarudNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dedupLock = Any()
    private val recentHashes = LruCache<String, Long>(128)
    private val pendingHashes = mutableSetOf<String>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        GarudApplication.setServiceConnected(true)
        GarudApplication.setNotificationPipelineState(GarudApplication.NotificationStage.WAITING, sourceApp = null)
        Log.i(TAG, "stage=listener_connected")
    }

    override fun onListenerDisconnected() {
        GarudApplication.setServiceConnected(false)
        Log.w(TAG, "stage=listener_disconnected")
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) {
            logSkipped("missing_status_bar_notification")
            return
        }

        val packageName = sbn.packageName
        Log.d(TAG, "stage=received package=$packageName notification_id=${sbn.id}")
        if (packageName == applicationContext.packageName) {
            logSkipped("own_notification")
            return
        }

        val app = applicationContext as? GarudApplication ?: GarudApplication.instance
        if (app.preferences.isMonitoringPaused.value) {
            logSkipped("monitoring_paused")
            return
        }
        if (app.preferences.isPackageExcluded(packageName)) {
            logSkipped("package_excluded")
            return
        }

        val notification = sbn.notification
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 &&
            (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        ) {
            logSkipped("ongoing_foreground_notification")
            return
        }

        val notificationData = try {
            extractNotificationData(sbn, notification)
        } catch (error: Exception) {
            Log.e(TAG, "stage=extraction_failed error_type=${error.javaClass.simpleName}")
            GarudApplication.setNotificationPipelineState(GarudApplication.NotificationStage.FAILED)
            return
        }
        if (notificationData.title.isNullOrBlank() && notificationData.text.isNullOrBlank() && notificationData.subText.isNullOrBlank()) {
            logSkipped("notification_has_no_exposed_text")
            return
        }

        val dedupKey = NotificationAnalysisEngine.computeDedupKey(
            packageName,
            notificationData.title,
            notificationData.text
        )
        val now = System.currentTimeMillis()
        val isDuplicate = synchronized(dedupLock) {
            val lastSuccessfulTime = recentHashes.get(dedupKey)
            if (dedupKey in pendingHashes || (lastSuccessfulTime != null && now - lastSuccessfulTime < DEDUP_WINDOW_MS)) {
                true
            } else {
                pendingHashes.add(dedupKey)
                false
            }
        }
        if (isDuplicate) {
            logSkipped("duplicate_within_window")
            return
        }

        GarudApplication.setNotificationPipelineState(
            GarudApplication.NotificationStage.RECEIVED,
            sourceApp = notificationData.appName
        )
        serviceScope.launch {
            try {
                GarudApplication.setNotificationPipelineState(
                    GarudApplication.NotificationStage.ANALYZING,
                    sourceApp = notificationData.appName
                )
                val result = app.messageAnalysisService.analyze(notificationData)
                val canOpenFinding = app.preferences.isHistoryEnabled.value
                if (canOpenFinding) app.repository.insertFinding(result)

                synchronized(dedupLock) {
                    pendingHashes.remove(dedupKey)
                    recentHashes.put(dedupKey, System.currentTimeMillis())
                }
                GarudApplication.setNotificationPipelineState(
                    GarudApplication.NotificationStage.COMPLETE,
                    sourceApp = notificationData.appName,
                    riskLevel = result.riskLevel
                )
                Log.i(TAG, "stage=analysis_complete finding_id=${result.id} risk=${result.riskLevel}")

                val shouldAlert = when (result.riskLevel) {
                    RiskLevel.HIGH -> true
                    RiskLevel.CAUTION -> app.preferences.alertOnCaution.value
                    else -> false
                }
                if (shouldAlert) {
                    runCatching { app.alertManager.showThreatAlert(result, openFindingDetails = canOpenFinding) }
                        .onFailure { Log.w(TAG, "stage=alert_failed error_type=${it.javaClass.simpleName}") }
                }
            } catch (cancelled: CancellationException) {
                synchronized(dedupLock) { pendingHashes.remove(dedupKey) }
                throw cancelled
            } catch (error: Exception) {
                synchronized(dedupLock) { pendingHashes.remove(dedupKey) }
                GarudApplication.setNotificationPipelineState(
                    GarudApplication.NotificationStage.FAILED,
                    sourceApp = notificationData.appName
                )
                Log.e(TAG, "stage=analysis_failed error_type=${error.javaClass.simpleName}")
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        GarudApplication.setServiceConnected(false)
        Log.i(TAG, "stage=service_destroyed")
        super.onDestroy()
    }

    private fun extractNotificationData(
        sbn: StatusBarNotification,
        notification: Notification
    ): NotificationData {
        val extras: Bundle = notification.extras ?: Bundle.EMPTY
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?.takeIf(String::isNotBlank)
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map(CharSequence::toString).orEmpty()
        val messagingLines = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?.mapNotNull { message ->
                (message as? Bundle)?.getCharSequence("text")?.toString()
            }
            .orEmpty()
        val resolvedText = NotificationContentResolver.resolveBody(bigText, messagingLines, text, textLines)
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            sbn.packageName
        }
        return NotificationData(
            packageName = sbn.packageName,
            appName = appName,
            title = title?.take(MAX_TITLE_CHARS),
            text = resolvedText,
            subText = subText?.take(MAX_TITLE_CHARS),
            postTime = sbn.postTime,
            notificationId = sbn.id,
            tag = sbn.tag
        )
    }

    private fun logSkipped(reason: String) {
        Log.d(TAG, "stage=notification_skipped reason=$reason")
    }

    companion object {
        private const val TAG = "GarudNotification"
        private const val DEDUP_WINDOW_MS = 60_000L
        private const val MAX_TITLE_CHARS = 512
    }
}
