package com.garud.security.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garud.security.GarudApplication
import com.garud.security.model.AnalysisResult
import com.garud.security.model.MessageSource
import com.garud.security.model.NotificationData
import com.garud.security.model.RiskLevel
import com.garud.security.ui.theme.GarudAmber
import com.garud.security.ui.theme.GarudForestGreen
import com.garud.security.ui.theme.GarudMutedGray
import com.garud.security.ui.theme.GarudNearBlack
import com.garud.security.ui.theme.GarudWarmCream
import com.garud.security.ui.theme.GarudWarningRed
import kotlinx.coroutines.launch

@Composable
fun ManualMessageAnalysisScreen(
    onBack: () -> Unit,
    onSaveToHistory: (AnalysisResult) -> Boolean,
    onViewFullDetail: (AnalysisResult) -> Unit
) {
    val context = LocalContext.current
    var selectedSource by remember { mutableStateOf(MessageSource.SMS) }
    var senderInput by remember { mutableStateOf("") }
    var messageTextInput by remember { mutableStateOf("") }
    var analysisResult by remember { mutableStateOf<AnalysisResult?>(null) }
    var isSaved by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar
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
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Third Eye",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = GarudNearBlack
                )
                Text(
                    text = "Message threat analysis",
                    fontSize = 11.sp,
                    color = GarudMutedGray
                )
            }
            if (messageTextInput.isNotBlank() || senderInput.isNotBlank() || analysisResult != null) {
                TextButton(
                    onClick = {
                        messageTextInput = ""
                        senderInput = ""
                        analysisResult = null
                        isSaved = false
                        saveMessage = null
                    }
                ) {
                    Text("Clear All", fontSize = 12.sp, color = GarudWarningRed, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Message Source Selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Message Source",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GarudNearBlack
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MessageSource.values().forEach { source ->
                            FilterChip(
                                selected = selectedSource == source,
                                onClick = { selectedSource = source },
                                label = { Text(source.displayName, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GarudForestGreen,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Optional Sender header
                    OutlinedTextField(
                        value = senderInput,
                        onValueChange = { senderInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Sender Name or Number (e.g. VK-HDFCBK, +91 98765)", fontSize = 12.sp) },
                        label = { Text("Sender / Header (Optional)", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GarudForestGreen,
                            focusedLabelColor = GarudForestGreen
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // 2. Message Body Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Suspicious Content",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = GarudNearBlack
                        )
                        Spacer(modifier = Modifier.weight(1f))

                        // Paste from clipboard button
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val item = clipboard?.primaryClip?.getItemAt(0)
                                val text = item?.text?.toString()
                                if (!text.isNullOrBlank()) {
                                    messageTextInput = text
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = GarudForestGreen
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste", fontSize = 12.sp, color = GarudForestGreen)
                        }

                        if (messageTextInput.isNotBlank() || senderInput.isNotBlank() || analysisResult != null) {
                            TextButton(
                                onClick = {
                                    messageTextInput = ""
                                    senderInput = ""
                                    analysisResult = null
                                    isSaved = false
                                    saveMessage = null
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = GarudWarningRed,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear", fontSize = 12.sp, color = GarudWarningRed)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = messageTextInput,
                        onValueChange = {
                            messageTextInput = it
                            isSaved = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        placeholder = {
                            Text(
                                "Paste the message you want to review.",
                                fontSize = 12.sp,
                                color = GarudMutedGray,
                                lineHeight = 16.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GarudForestGreen,
                            cursorColor = GarudForestGreen
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Primary Scan Button
                    Button(
                        onClick = {
                            isAnalyzing = true
                            coroutineScope.launch {
                                try {
                                    val data = NotificationData(
                                        packageName = selectedSource.defaultPackage,
                                        appName = selectedSource.displayName,
                                        title = senderInput.ifBlank { selectedSource.displayName },
                                        text = messageTextInput
                                    )
                                    analysisResult = GarudApplication.instance.messageAnalysisService.analyze(data)
                                    isSaved = false
                                    saveMessage = null
                                } finally {
                                    isAnalyzing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = messageTextInput.trim().isNotBlank() && !isAnalyzing,
                        colors = ButtonDefaults.buttonColors(containerColor = GarudForestGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isAnalyzing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAnalyzing) "Analyzing…" else "Screen & Retrieve Evidence",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = GarudForestGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "On-device screening runs first. Cloud sharing is optional in Settings.",
                            fontSize = 10.sp,
                            color = GarudMutedGray
                        )
                    }
                }
            }

            // 3. Analysis Results View
            val result = analysisResult
            if (result != null) {
                val badgeColor = when (result.riskLevel) {
                    RiskLevel.HIGH -> GarudWarningRed
                    RiskLevel.CAUTION -> GarudAmber
                    RiskLevel.LOW -> GarudForestGreen
                            RiskLevel.INSUFFICIENT_INFORMATION -> GarudMutedGray
                }

                val badgeText = when (result.riskLevel) {
                    RiskLevel.HIGH -> "HIGH RISK DETECTED"
                    RiskLevel.CAUTION -> "CAUTION ADVISED"
                    RiskLevel.LOW -> "LOW RISK / NORMAL"
                    RiskLevel.INSUFFICIENT_INFORMATION -> "INSUFFICIENT TEXT"
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (result.riskLevel) {
                            RiskLevel.HIGH -> Color(0xFFFDECEB)
                            RiskLevel.CAUTION -> Color(0xFFFFF8EC)
                            RiskLevel.INSUFFICIENT_INFORMATION -> Color(0xFFF0EFEB)
                            RiskLevel.LOW -> Color(0xFFE9F4EE)
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        // Verdict Header
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(badgeColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = badgeText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = badgeColor
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = result.explanation,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = GarudNearBlack,
                            lineHeight = 18.sp
                        )
                        result.ragReport?.let { report ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when {
                                    report.providerStatus.contains("RAG_AI_PIPELINE") -> "Analysis path: cloud AI (${report.providerStatus.substringAfter("RAG_AI_PIPELINE").trim(' ', '·')})"
                                    report.providerStatus.contains("BACKEND_UNAVAILABLE") -> "Analysis path: on-device rules; backend unavailable"
                                    report.providerStatus.contains("LOCAL_EVIDENCE_GROUNDING") -> "Analysis path: server fallback; no reviewed source corpus"
                                    else -> "Analysis path: on-device rules; source review pending"
                                },
                                fontSize = 11.sp,
                                color = GarudMutedGray
                            )
                        }

                        // Triggered Indicators
                        if (result.triggeredIndicators.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Detected Threat Indicators (${result.triggeredIndicators.size}):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GarudNearBlack
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            result.triggeredIndicators.forEach { ind ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = badgeColor,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = ind.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GarudNearBlack
                                        )
                                        Text(
                                            text = ind.detail,
                                            fontSize = 11.sp,
                                            color = GarudMutedGray,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Grounded RAG Evidence & Citations
                        val rag = result.ragReport
                        if (rag != null && rag.citations.isNotEmpty()) {
                            val uriHandler = LocalUriHandler.current
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = null,
                                            tint = GarudForestGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Grounded Evidence & Regulatory Citations",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GarudNearBlack
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (!rag.isVerifiedCitation) {
                                        Text("Returned sources could not be verified. Links are hidden.", fontSize = 11.sp, color = GarudAmber)
                                    }
                                    rag.citations.filter { rag.isVerifiedCitation }.forEach { citation ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { uriHandler.openUri(citation.url) }
                                                .padding(vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "[${citation.id}] ${citation.title}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = GarudForestGreen
                                            )
                                            Text(
                                                text = "Source: ${citation.publisher}",
                                                fontSize = 10.sp,
                                                color = GarudMutedGray
                                            )
                                            Text(citation.url, fontSize = 10.sp, color = GarudForestGreen)
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "\"${citation.relevantSnippet}\"",
                                                fontSize = 11.sp,
                                                color = GarudNearBlack,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = rag.limitations,
                                        fontSize = 10.sp,
                                        color = GarudMutedGray,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }

                        // Recommended Safe Actions
                        if (result.safeActions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Safe Actions Recommended:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GarudNearBlack
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            result.safeActions.forEach { act ->
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = GarudForestGreen,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = act,
                                        fontSize = 11.sp,
                                        color = GarudNearBlack,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons: Save to History & View Full Details
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    isSaved = onSaveToHistory(result)
                                    saveMessage = if (isSaved) "Saved to this device." else "History retention is off. Enable it in Settings to save this finding."
                                },
                                enabled = !isSaved,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSaved) GarudForestGreen else GarudNearBlack
                                )
                            ) {
                                Icon(
                                    imageVector = if (isSaved) Icons.Default.CheckCircle else Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSaved) "Saved" else "Save Finding",
                                    fontSize = 12.sp
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    onSaveToHistory(result)
                                    onViewFullDetail(result)
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Full Details", fontSize = 12.sp, color = GarudNearBlack)
                            }

                            OutlinedButton(
                                onClick = {
                                    messageTextInput = ""
                                    senderInput = ""
                                    analysisResult = null
                                    isSaved = false
                                    saveMessage = null
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Clear", fontSize = 12.sp, color = GarudWarningRed)
                            }
                        }
                        saveMessage?.let { message ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(message, fontSize = 11.sp, color = if (isSaved) GarudForestGreen else GarudAmber)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
