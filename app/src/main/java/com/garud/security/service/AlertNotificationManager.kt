package com.garud.security.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.garud.security.MainActivity
import com.garud.security.R
import com.garud.security.model.AnalysisResult
import com.garud.security.model.RiskLevel

/**
 * Dispatches on-device warning alerts when threats are detected in incoming notifications.
 */
class AlertNotificationManager(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_THREAT_ALERTS,
                "GARUD Threat Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Immediate alerts for high-risk and suspicious notification threats detected on-device."
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showThreatAlert(result: AnalysisResult, openFindingDetails: Boolean = true) {
        // Do not alert for low or insufficient information findings
        if (result.riskLevel != RiskLevel.HIGH && result.riskLevel != RiskLevel.CAUTION) {
            return
        }

        // Deep-link intent into GARUD MainActivity with finding ID
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (openFindingDetails) putExtra(EXTRA_FINDING_ID, result.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            result.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (result.riskLevel) {
            RiskLevel.HIGH -> "GARUD Alert: Suspicious ${result.sourceAppName} Notification"
            RiskLevel.CAUTION -> "GARUD Notice: Caution on ${result.sourceAppName}"
            else -> "GARUD Security Finding"
        }

        // Sanitized explanation without leaking raw private message text on lock screen
        val summaryText = if (result.triggeredIndicators.isNotEmpty()) {
            result.triggeredIndicators.first().title
        } else {
            result.explanation
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_THREAT_ALERTS)
            .setSmallIcon(R.drawable.ic_garud_shield)
            .setContentTitle(title)
            .setContentText(summaryText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText("${result.explanation}\n\nTap to review safe actions inside GARUD.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setColor(0xFFB33A32.toInt())
            .setContentIntent(pendingIntent)
            .build()

        try {
            // Stable notification ID derived from dedupKey hash to avoid duplicate spam
            val notificationId = (result.dedupKey.hashCode() and 0x7FFFFFFF)
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission POST_NOTIFICATIONS may not be granted by user yet
        }
    }

    companion object {
        const val CHANNEL_THREAT_ALERTS = "garud_threat_alerts_v1"
        const val EXTRA_FINDING_ID = "extra_finding_id"
    }
}
