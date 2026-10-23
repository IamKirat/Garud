package com.garud.security.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garud.security.model.AnalysisResult
import com.garud.security.model.RiskLevel
import com.garud.security.ui.theme.GarudAmber
import com.garud.security.ui.theme.GarudForestGreen
import com.garud.security.ui.theme.GarudMutedGray
import com.garud.security.ui.theme.GarudNearBlack
import com.garud.security.ui.theme.GarudWarmCream
import com.garud.security.ui.theme.GarudWarningRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FindingDetailScreen(
    finding: AnalysisResult?,
    onBack: () -> Unit,
    onDelete: (String) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    if (showDeleteConfirm && finding != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(text = "Delete Finding", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove this detection record from your device history?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(finding.id)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GarudWarningRed)
                ) {
                    Text(text = "Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = GarudMutedGray)
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
                text = "Finding Details",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = GarudNearBlack,
                modifier = Modifier.weight(1f)
            )
            if (finding != null) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete finding",
                        tint = GarudWarningRed
                    )
                }
            }
        }

        if (finding == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Finding not found", color = GarudMutedGray)
            }
            return
        }

        val badgeColor = when (finding.riskLevel) {
            RiskLevel.HIGH -> GarudWarningRed
            RiskLevel.CAUTION -> GarudAmber
            RiskLevel.LOW -> GarudForestGreen
            RiskLevel.INSUFFICIENT_INFORMATION -> GarudMutedGray
        }

        val badgeTitle = when (finding.riskLevel) {
            RiskLevel.HIGH -> "HIGH THREAT DETECTED"
            RiskLevel.CAUTION -> "CAUTION ADVISED"
            RiskLevel.LOW -> "NO KNOWN THREATS DETECTED"
            RiskLevel.INSUFFICIENT_INFORMATION -> "INSUFFICIENT INFORMATION"
        }

        val timeFormat = remember { SimpleDateFormat("hh:mm:ss a, EEEE, dd MMMM yyyy", Locale.getDefault()) }
        val dateString = remember(finding.timestamp) { timeFormat.format(Date(finding.timestamp)) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Risk Summary Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = badgeColor.copy(alpha = 0.12f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(badgeColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (finding.riskLevel == RiskLevel.HIGH) Icons.Default.Warning else Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = badgeTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = badgeColor
                        )
                        Text(
                            text = "Origin: ${finding.sourceAppName} (${finding.sourcePackage})",
                            fontSize = 11.sp,
                            color = GarudNearBlack
                        )
                    }
                }
            }

            // Notification Metadata Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Subject / Preview",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GarudMutedGray
                    )
                    Text(
                        text = finding.titleSnippet,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GarudNearBlack
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Timestamp",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GarudMutedGray
                    )
                    Text(
                        text = dateString,
                        fontSize = 12.sp,
                        color = GarudNearBlack
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Privacy Note",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GarudMutedGray
                    )
                    Text(
                        text = "Full message body is never stored in persistent memory to prevent sensitive data leakage.",
                        fontSize = 11.sp,
                        color = GarudMutedGray,
                        lineHeight = 15.sp
                    )
                }
            }

            // Analysis Explanation
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Safety Assessment",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GarudNearBlack
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = finding.explanation,
                        fontSize = 13.sp,
                        color = GarudNearBlack,
                        lineHeight = 19.sp
                    )
                }
            }

            finding.ragReport?.let { report ->
                val uriHandler = LocalUriHandler.current
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Evidence review", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GarudNearBlack)
                        Spacer(Modifier.height(6.dp))
                        Text("Analysis path: ${report.providerStatus}", fontSize = 11.sp, color = GarudMutedGray)
                        Spacer(Modifier.height(6.dp))
                        Text(report.summary, fontSize = 13.sp, color = GarudNearBlack, lineHeight = 19.sp)
                        if (report.isVerifiedCitation && report.citations.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Sources", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GarudNearBlack)
                            report.citations.forEach { citation ->
                                Column(
                                    Modifier.fillMaxWidth().clickable { uriHandler.openUri(citation.url) }.padding(vertical = 8.dp)
                                ) {
                                    Text(citation.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GarudForestGreen)
                                    Text(citation.publisher, fontSize = 11.sp, color = GarudMutedGray)
                                    Text(citation.url, fontSize = 11.sp, color = GarudForestGreen)
                                }
                            }
                        } else if (report.citations.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("Sources were returned but could not be verified, so links are hidden.", fontSize = 12.sp, color = GarudAmber)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(report.limitations, fontSize = 11.sp, color = GarudMutedGray, lineHeight = 16.sp)
                    }
                }
            }

            // Triggered Indicators (if any)
            if (finding.triggeredIndicators.isNotEmpty()) {
                Text(
                    text = "Triggered Indicators (${finding.triggeredIndicators.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = GarudNearBlack
                )

                finding.triggeredIndicators.forEach { indicator ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = indicator.category.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GarudAmber,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = indicator.severity.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (indicator.severity == RiskLevel.HIGH) GarudWarningRed else GarudAmber
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = indicator.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = GarudNearBlack
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = indicator.detail,
                                fontSize = 12.sp,
                                color = GarudMutedGray,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Recommended Safe Actions
            if (finding.safeActions.isNotEmpty()) {
                Text(
                    text = "Recommended Safe Actions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = GarudNearBlack
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        finding.safeActions.forEach { action ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = GarudForestGreen,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = action,
                                    fontSize = 13.sp,
                                    color = GarudNearBlack,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
