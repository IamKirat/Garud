package com.garud.security.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garud.security.model.AnalysisResult
import com.garud.security.model.RiskLevel
import com.garud.security.GarudApplication
import com.garud.security.ui.theme.*
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import android.content.Context
import android.view.accessibility.AccessibilityManager
import android.provider.Settings

@Composable
fun HomeScreen(
    isAccessGranted: Boolean,
    isServiceConnected: Boolean,
    notificationPipeline: GarudApplication.NotificationPipelineState,
    isMonitoringPaused: Boolean,
    findings: List<AnalysisResult>,
    onTogglePause: (Boolean) -> Unit,
    onSelectFinding: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissionsExplainer: () -> Unit,
    onAnalyzeMessage: () -> Unit,
    onOpenFindings: () -> Unit,
    onOpenLinkAnalysis: () -> Unit,
    onOpenQrAnalysis: () -> Unit,
    onOpenCallTranscription: () -> Unit,
    onClearAllData: () -> Unit
) {
    var askClearAll by remember { mutableStateOf(false) }
    var showDust by remember { mutableStateOf(false) }
    var clearDataMessage by remember { mutableStateOf<String?>(null) }
    if (askClearAll) AlertDialog(
        onDismissRequest = { askClearAll = false },
        title = { Text("Clear GARUD analysis data?") },
        text = { Text("This permanently deletes saved GARUD screening findings and analysis details from this device. It does not delete phone notifications, other apps’ data, or your GARUD settings.") },
        confirmButton = { TextButton(onClick = { runCatching { onClearAllData() }.onSuccess { clearDataMessage = "GARUD analysis data cleared."; showDust = true }.onFailure { clearDataMessage = "Could not clear GARUD data: ${it.message ?: "storage error"}" }; askClearAll = false }) { Text("Delete GARUD data", color = GarudWarningRed) } },
        dismissButton = { TextButton(onClick = { askClearAll = false }) { Text("Cancel") } }
    )
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = GarudForestGreen, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("GARUD", fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onBackground)
                Text("Your digital safety companion", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Column(Modifier.padding(top = 8.dp)) {
                    Text("Stay a step ahead.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Review suspicious messages privately on your device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("NOTIFICATION SCREENING", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                        Spacer(Modifier.height(10.dp))
                        val status = when { !isAccessGranted -> "Access not enabled"; isMonitoringPaused -> "Monitoring paused"; !isServiceConnected -> "Service reconnecting"; else -> "Notification screening ready" }
                        val description = when { !isAccessGranted -> "Enable Android notification access to screen incoming notifications on this device."; isMonitoringPaused -> "Incoming notifications are not being analysed while monitoring is paused."; !isServiceConnected -> "Android access is granted, but GARUD’s listener is not currently connected."; else -> "GARUD screens eligible notification text locally. Full message text is not retained in history." }
                        Text(status, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = when { !isAccessGranted || isMonitoringPaused || !isServiceConnected -> GarudAmber; else -> GarudForestGreen })
                        Spacer(Modifier.height(4.dp))
                        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                        Spacer(Modifier.height(12.dp))
                        if (!isAccessGranted) TextButton(onClick = onOpenPermissionsExplainer) { Text("Review notification access") }
                        else Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isMonitoringPaused) "Resume monitoring" else "Pause monitoring", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                            Switch(checked = !isMonitoringPaused, onCheckedChange = { onTogglePause(!it) }, modifier = Modifier.semantics { contentDescription = "Notification monitoring" })
                        }
                    }
                }
            }
            item {
                Button(onClick = onAnalyzeMessage, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("Analyse a message", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
            item {
                Text("Quick actions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickAction("Suspicious links", Icons.Default.Link, onOpenLinkAnalysis, Modifier.weight(1f))
                        QuickAction("Suspicious QR codes", Icons.Default.QrCodeScanner, onOpenQrAnalysis, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickAction("Call transcription", Icons.Default.Call, onOpenCallTranscription, Modifier.weight(1f))
                        QuickAction(if (isMonitoringPaused) "Resume monitoring" else "Notification monitoring", Icons.Default.Shield, { onTogglePause(!isMonitoringPaused) }, Modifier.weight(1f))
                    }
                    OutlinedButton(onClick = { askClearAll = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = GarudWarningRed)) {
                        Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(8.dp)); Text("Clear GARUD data")
                    }
                    clearDataMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            item {
                val activityStatus = when (notificationPipeline.stage) {
                    GarudApplication.NotificationStage.WAITING -> "Waiting for a notification"
                    GarudApplication.NotificationStage.RECEIVED -> "Notification received"
                    GarudApplication.NotificationStage.ANALYZING -> "Analysing notification on this device…"
                    GarudApplication.NotificationStage.COMPLETE -> "Analysis complete${notificationPipeline.riskLevel?.let { " · ${it.name.replace('_', ' ')}" }.orEmpty()}"
                    GarudApplication.NotificationStage.FAILED -> "Analysis failed. Try analysing the message manually, or review notification access."
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Recent notification activity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(activityStatus, color = if (notificationPipeline.stage == GarudApplication.NotificationStage.FAILED) GarudWarningRed else MaterialTheme.colorScheme.onSurfaceVariant)
                        notificationPipeline.sourceApp?.let { source ->
                            Spacer(Modifier.height(3.dp))
                            Text("Source: $source", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent findings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (findings.isNotEmpty()) TextButton(onClick = onOpenFindings) { Text("View history") }
                }
            }
            if (findings.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No findings yet", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Findings from notification screening and message analysis will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else itemsIndexed(findings.take(3), key = { _, it -> it.id }) { index, finding -> FindingItemCard(finding, onClick = { onSelectFinding(finding.id) }, entranceIndex = index) }
            item {
                OutlinedButton(onClick = onOpenFindings, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.History, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Open findings history")
                }
            }
        }
    }
    ThanosDustOverlay(showDust) { showDust = false }
    }
}

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.heightIn(min = 84.dp).clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = GarudForestGreen, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun ThanosDustOverlay(visible: Boolean, onFinished: () -> Unit) {
    if (!visible) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val reduceMotion = remember {
        val accessibility = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        (accessibility?.isEnabled == true) || runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(true)
    }
    if (reduceMotion) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }
    val progress by androidx.compose.animation.core.animateFloatAsState(1f, animationSpec = androidx.compose.animation.core.tween(720), finishedListener = { onFinished() }, label = "clear-dust")
    Canvas(Modifier.fillMaxSize()) {
        val palette = listOf(GarudForestGreen, GarudAmber, GarudWarningRed, Color(0xFF77756F))
        repeat(72) { index ->
            val angle = index * 2.3999632f
            val travel = (28 + (index % 9) * 10) * progress
            val alpha = (1f - progress) * .72f
            drawCircle(palette[index % palette.size].copy(alpha = alpha), radius = (1.2f + (index % 4)), center = androidx.compose.ui.geometry.Offset(size.width / 2 + kotlin.math.cos(angle.toDouble()).toFloat() * travel, size.height / 2 + kotlin.math.sin(angle.toDouble()).toFloat() * travel))
        }
    }
}

@Composable
fun FindingItemCard(finding: AnalysisResult, onClick: () -> Unit, entranceIndex: Int = 0) {
    var entered by remember(finding.id) { mutableStateOf(false) }
    LaunchedEffect(finding.id) { delay((entranceIndex.coerceAtMost(8) * 45L)); entered = true }
    val progress by androidx.compose.animation.core.animateFloatAsState(if (entered) 1f else 0f, label = "finding-enter")
    val tint = when (finding.riskLevel) { RiskLevel.HIGH -> GarudWarningRed; RiskLevel.CAUTION -> GarudAmber; RiskLevel.LOW -> GarudForestGreen; RiskLevel.INSUFFICIENT_INFORMATION -> GarudMutedGray }
    val riskLabel = when (finding.riskLevel) { RiskLevel.HIGH -> "HIGH RISK"; RiskLevel.CAUTION -> "CAUTION"; RiskLevel.LOW -> "LOW RISK"; RiskLevel.INSUFFICIENT_INFORMATION -> "NEEDS VERIFICATION" }
    Card(Modifier.fillMaxWidth().graphicsLayer { alpha = progress; translationY = (1f - progress) * 18f }.clickable(onClick = onClick).border(1.dp, tint.copy(alpha = .42f), RoundedCornerShape(18.dp)), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(finding.sourceAppName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(riskLabel, color = tint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(finding.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, lineHeight = 19.sp)
            Spacer(Modifier.height(6.dp))
            Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(finding.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
