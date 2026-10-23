package com.garud.security.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garud.security.ui.theme.GarudAmber
import com.garud.security.ui.theme.GarudForestGreen
import com.garud.security.ui.theme.GarudMutedGray
import com.garud.security.ui.theme.GarudNearBlack
import com.garud.security.ui.theme.GarudWarmCream
import com.garud.security.ui.theme.GarudWarningRed
import com.garud.security.data.ThemeMode

@Composable
fun SettingsScreen(
    isAccessGranted: Boolean,
    isMonitoringPaused: Boolean,
    isHistoryEnabled: Boolean,
    alertOnCaution: Boolean,
    cloudAnalysisEnabled: Boolean,
    backendBaseUrl: String,
    excludedPackages: Set<String>,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onTogglePause: (Boolean) -> Unit,
    onToggleHistory: (Boolean) -> Unit,
    onToggleAlertOnCaution: (Boolean) -> Unit,
    onToggleCloudAnalysis: (Boolean) -> Unit,
    onBackendBaseUrlChange: (String) -> Unit,
    onAddExcludedPackage: (String) -> Unit,
    onRemoveExcludedPackage: (String) -> Unit,
    onClearHistory: () -> Unit,
    onClearAllData: () -> Unit,
    onRevisitOnboarding: () -> Unit,
    onBack: () -> Unit,
    onOpenNotificationAccess: () -> Unit
) {
    val context = LocalContext.current
    val appVersion = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown" }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showAddPackageDialog by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var cacheMessage by remember { mutableStateOf<String?>(null) }
    var cacheBytes by remember { mutableStateOf(0L) }
    var databaseBytes by remember { mutableStateOf(0L) }
    var newPackageInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    fun refreshStorage() {
        cacheBytes = (listOf(context.cacheDir) + context.externalCacheDirs.filterNotNull()).sumOf(::folderSize)
        databaseBytes = context.getDatabasePath("garud_findings.db").length()
    }
    LaunchedEffect(Unit) { refreshStorage() }

    if (showClearAllConfirm) AlertDialog(
        onDismissRequest = { showClearAllConfirm = false },
        title = { Text("Clear all GARUD data?") },
        text = { Text("This deletes GARUD’s locally saved finding records and analysis details. It does not delete other apps’ files, phone notifications, or GARUD preferences.") },
        confirmButton = { TextButton(onClick = { runCatching { onClearAllData() }.onSuccess { cacheMessage = "GARUD analysis data cleared." }.onFailure { cacheMessage = "Could not clear GARUD data: ${it.message ?: "storage error"}" }; showClearAllConfirm = false; refreshStorage() }) { Text("Delete GARUD data", color = GarudWarningRed) } },
        dismissButton = { TextButton(onClick = { showClearAllConfirm = false }) { Text("Cancel") } }
    )

    if (showClearCacheConfirm) AlertDialog(
        onDismissRequest = { showClearCacheConfirm = false },
        title = { Text("Clear temporary cache?") },
        text = { Text("Only regenerable files in GARUD’s cache folders will be removed. The findings database, preferences, and other app or device files will remain.") },
        confirmButton = { TextButton(onClick = {
            runCatching { (listOf(context.cacheDir) + context.externalCacheDirs.filterNotNull()).forEach { directory -> directory.listFiles()?.forEach { child -> if (!child.deleteRecursively()) error("A cache file could not be removed.") } } }
                .onSuccess { cacheMessage = "GARUD cache cleared."; refreshStorage() }
                .onFailure { cacheMessage = "Could not clear GARUD cache: ${it.message ?: "storage error"}" }
            showClearCacheConfirm = false
        }) { Text("Clear cache") } },
        dismissButton = { TextButton(onClick = { showClearCacheConfirm = false }) { Text("Cancel") } }
    )

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(text = "Clear All Detections", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently erase all locally stored screening findings?") },
            confirmButton = {
                Button(
                    onClick = {
                        runCatching { onClearHistory() }
                            .onSuccess { cacheMessage = "Analysis history cleared." }
                            .onFailure { cacheMessage = "Could not clear history: ${it.message ?: "storage error"}" }
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GarudWarningRed)
                ) {
                    Text("Clear All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = GarudMutedGray)
                }
            }
        )
    }

    if (showAddPackageDialog) {
        AlertDialog(
            onDismissRequest = { showAddPackageDialog = false },
            title = { Text(text = "Exclude Application", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Enter package name (e.g. com.example.app) to exclude its notifications from screening:",
                        fontSize = 12.sp,
                        color = GarudMutedGray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPackageInput,
                        onValueChange = { newPackageInput = it },
                        placeholder = { Text("com.package.name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPackageInput.isNotBlank()) {
                            onAddExcludedPackage(newPackageInput.trim())
                            newPackageInput = ""
                            showAddPackageDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GarudForestGreen)
                ) {
                    Text("Exclude")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPackageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GarudWarmCream)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = GarudNearBlack
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Settings & Privacy",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = GarudNearBlack
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Appearance", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GarudMutedGray)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Theme", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeMode.values().forEach { option ->
                            androidx.compose.material3.FilterChip(selected = themeMode == option, onClick = { onThemeModeChange(option) }, label = { Text(option.name.lowercase().replaceFirstChar(Char::uppercase)) })
                        }
                    }
                }
            }

            Text("Notification access", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GarudMutedGray)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (isAccessGranted) "Android access enabled" else "Android access not enabled", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = GarudNearBlack)
                        Text("GARUD checks this permission state when the app returns from Android settings.", fontSize = 11.sp, color = GarudMutedGray, lineHeight = 15.sp)
                    }
                    TextButton(onClick = onOpenNotificationAccess) { Text("Manage") }
                }
            }

            Text("Storage & data", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GarudMutedGray)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Temporary cache: ${formatBytes(cacheBytes)}", fontWeight = FontWeight.SemiBold)
                    Text("Findings database: ${formatBytes(databaseBytes)}. These figures are GARUD cache and database only, not total Android app storage.", fontSize = 11.sp, color = GarudMutedGray)
                    OutlinedButton(onClick = { showClearCacheConfirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Clear cache only") }
                    OutlinedButton(onClick = { showClearConfirm = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = GarudAmber)) { Text("Clear analysis history") }
                    Button(onClick = { showClearAllConfirm = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = GarudWarningRed)) { Text("Clear all GARUD data", color = Color.White) }
                    cacheMessage?.let { Text(it, fontSize = 12.sp, color = GarudMutedGray) }
                }
            }
            // Group 1: Monitoring & Alerts
            Text(
                text = "Protection Controls",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = GarudMutedGray
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Pause toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pause Threat Screening",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GarudNearBlack
                            )
                            Text(
                                text = "Temporarily suspend notification analysis without revoking Android permission.",
                                fontSize = 11.sp,
                                color = GarudMutedGray,
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = isMonitoringPaused,
                            onCheckedChange = { onTogglePause(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = GarudAmber
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Caution alerts toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Alert on Caution Findings",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GarudNearBlack
                            )
                            Text(
                                text = "By default, system warning notifications only trigger on High Risk findings. Turn this on to also alert on moderate caution items.",
                                fontSize = 11.sp,
                                color = GarudMutedGray,
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = alertOnCaution,
                            onCheckedChange = { onToggleAlertOnCaution(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = GarudForestGreen
                            )
                        )
                    }
                }
            }

            // Group 2: Privacy & History Retention
            Text(
                text = "Privacy & Data Retention",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = GarudMutedGray
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // History retention toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Retain Minimal Finding History",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GarudNearBlack
                            )
                            Text(
                                text = "Saves minimal finding metadata (risk, app name, explanation) locally. When disabled, no history is stored to disk.",
                                fontSize = 11.sp,
                                color = GarudMutedGray,
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = isHistoryEnabled,
                            onCheckedChange = { onToggleHistory(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = GarudForestGreen
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Clear history button
                    OutlinedButton(
                        onClick = { showClearConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GarudWarningRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Clear Local Screening History", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Text("GARUD Backend", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GarudMutedGray)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Server URL for VirusTotal and cloud analysis", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = GarudNearBlack)
                    OutlinedTextField(
                        value = backendBaseUrl,
                        onValueChange = onBackendBaseUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://api.your-domain.com") },
                        singleLine = true,
                        label = { Text("Public HTTPS base URL") }
                    )
                    Text(
                        "Paste the URL assigned to your deployed GARUD server (for example https://garud-api.onrender.com). Do not paste your VirusTotal key here. Add that key to your server host as VIRUSTOTAL_API_KEY; the server uses it privately for /api/analyze/url.",
                        fontSize = 11.sp, color = GarudMutedGray, lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Send message text for cloud analysis", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GarudNearBlack)
                            Text("When enabled, exposed notification or pasted text is sent to this server and may be forwarded to its configured AI and embedding providers. Off by default. Do not send sensitive content.", fontSize = 11.sp, color = GarudMutedGray, lineHeight = 15.sp)
                        }
                        Switch(checked = cloudAnalysisEnabled, onCheckedChange = onToggleCloudAnalysis)
                    }
                }
            }

            Text("Custom LLM provider", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GarudMutedGray)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Not configured in this build", fontWeight = FontWeight.SemiBold)
                    Text("GARUD currently uses the provider configured on its backend. This app does not store custom provider keys on-device or proxy arbitrary user-supplied URLs. A secure authenticated backend provider manager is required before custom endpoints can be enabled.", fontSize = 11.sp, color = GarudMutedGray, lineHeight = 16.sp)
                }
            }

            // Group 3: Excluded Applications
            Text(
                text = "Excluded Applications (${excludedPackages.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = GarudMutedGray
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Notifications originating from these packages are ignored by GARUD:",
                        fontSize = 11.sp,
                        color = GarudMutedGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    excludedPackages.forEach { pkg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = GarudMutedGray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = pkg,
                                fontSize = 12.sp,
                                color = GarudNearBlack,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onRemoveExcludedPackage(pkg) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove exclusion",
                                    tint = GarudWarningRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showAddPackageDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Add Application Exclusion")
                    }
                }
            }

            // Group 4: Onboarding Revisit
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Welcome & Setup",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GarudNearBlack
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Revisit the initial setup walkthrough and permission guidance screens.",
                        fontSize = 11.sp,
                        color = GarudMutedGray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onRevisitOnboarding,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Revisit Welcome Onboarding")
                    }
                }
            }

            // Group 5: Zero-Leak Architecture Note
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F6F3)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = GarudForestGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GARUD V1 Architecture Guarantee",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = GarudForestGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Local rules run on this device. If you enable cloud analysis, exposed notification or pasted text is sent to your configured HTTPS backend. URL reputation checks separately submit the URL to VirusTotal only after your explicit confirmation. GARUD does not capture system phone-call audio.",
                        fontSize = 11.sp,
                        color = GarudNearBlack,
                        lineHeight = 16.sp
                    )
                }
            }

            Text(
                text = "GARUD $appVersion",
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                fontSize = 12.sp,
                color = GarudMutedGray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun folderSize(file: java.io.File): Long = if (file.isFile) file.length() else file.listFiles()?.sumOf(::folderSize) ?: 0L

private fun formatBytes(value: Long): String = when {
    value < 1024 -> "$value B"
    value < 1024 * 1024 -> "%.1f KB".format(value / 1024.0)
    else -> "%.1f MB".format(value / (1024.0 * 1024.0))
}
