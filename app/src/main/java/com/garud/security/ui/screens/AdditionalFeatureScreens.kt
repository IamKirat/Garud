package com.garud.security.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.garud.security.service.UrlReputationClient
import com.garud.security.service.UrlReputationResult
import com.garud.security.GarudApplication
import com.garud.security.model.AnalysisResult
import com.garud.security.model.NotificationData
import com.garud.security.model.RiskLevel
import com.garud.security.ui.theme.GarudAmber
import com.garud.security.ui.theme.GarudForestGreen
import com.garud.security.ui.theme.GarudPurple
import com.garud.security.ui.theme.GarudWarningRed
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun GarudBottomNavigation(selected: String, onSelect: (String) -> Unit) {
    val tabs = listOf(
        Triple("HOME", "Home", Icons.Default.Home),
        Triple("ANALYSE", "Third Eye", Icons.Default.Visibility),
        Triple("FINDINGS", "History", Icons.Default.History),
        Triple("SETTINGS", "Settings", Icons.Default.Settings)
    )
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
        shape = RoundedCornerShape(36.dp), color = Color(0xFF20211F),
        shadowElevation = 14.dp, tonalElevation = 3.dp
    ) {
        Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { (route, label, icon) ->
                val active = selected == route
                val scale by animateFloatAsState(if (active) 1f else .96f, tween(180), label = "tab-scale")
                val tint by animateColorAsState(if (active) Color.White else Color(0xFFB8B9B3), tween(180), label = "tab-tint")
                Column(
                    Modifier.weight(1f).fillMaxHeight().scale(scale).clipPill(active)
                        .clickable { onSelect(route) }.semantics { contentDescription = label },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(label, color = tint, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun Modifier.clipPill(active: Boolean): Modifier = this.then(
    Modifier.background(if (active) GarudPurple else Color.Transparent, CircleShape)
)

@Composable
fun LinkAnalysisScreen(backendUrl: String, initialUrl: String?, onConsumedInitialUrl: () -> Unit, onBack: () -> Unit) {
    var input by remember { mutableStateOf(initialUrl.orEmpty()) }
    var result by remember { mutableStateOf<UrlReputationResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var askConsent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(initialUrl) { if (!initialUrl.isNullOrBlank()) { input = initialUrl; onConsumedInitialUrl() } }

    if (askConsent) AlertDialog(
        onDismissRequest = { askConsent = false },
        title = { Text("Submit URL for reputation analysis?") },
        text = { Text("GARUD will send only this URL to your configured backend. The backend submits it to VirusTotal, where it may be shared with security partners. Continue only if you consent.") },
        confirmButton = { TextButton(onClick = {
            askConsent = false; loading = true; error = null; result = null
            scope.launch { runCatching { UrlReputationClient.scan(backendUrl, input) }.onSuccess { result = it }.onFailure { error = it.message ?: "URL analysis failed." }; loading = false }
        }) { Text("Submit URL") } },
        dismissButton = { TextButton(onClick = { askConsent = false }) { Text("Cancel") } }
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Suspicious links", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Text("Check URL reputation without opening the destination.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = input, onValueChange = { input = it; result = null; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Paste or share a URL") }, singleLine = false, minLines = 2)
        Button(onClick = {
            val normalized = UrlReputationClient.normalize(input)
            if (normalized == null) error = "Enter a valid HTTP or HTTPS URL without embedded credentials."
            else { input = normalized; askConsent = true }
        }, enabled = !loading && input.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Link, null); Spacer(Modifier.width(8.dp)); Text(if (loading) "Checking…" else "Check URL reputation")
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        result?.let { UrlResultCard(it) }
    }
}

@Composable
private fun UrlResultCard(result: UrlReputationResult) {
    val risk = when {
        result.flagged == true -> "SUSPICIOUS — detections reported" to GarudWarningRed
        result.status == "completed" -> "NO DETECTIONS RETURNED — not proof of safety" to GarudAmber
        result.status == "provider_not_configured" -> "UNKNOWN — provider not configured" to GarudAmber
        result.status == "submitted" -> "PENDING — provider analysis is not complete" to GarudAmber
        else -> "UNKNOWN — no completed reputation verdict" to GarudAmber
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp), elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(risk.first, color = risk.second, fontWeight = FontWeight.Bold)
            Text(result.target, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(result.details)
            result.malicious?.let { Text("VirusTotal results: $it malicious · ${result.suspicious} suspicious · ${result.harmless} harmless · ${result.undetected} undetected", style = MaterialTheme.typography.bodySmall) }
            Text("Source: ${result.source} · Checked ${DateFormat.getDateTimeInstance().format(Date(result.checkedAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun QrAnalysisScreen(onCheckUrl: (String) -> Unit) {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var decoded by remember { mutableStateOf<String?>(null) }
    val options = remember { GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build() }
    val imageOptions = remember { BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build() }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true; message = null; decoded = null
            runCatching { InputImage.fromFilePath(context, uri) }
                .onSuccess { image ->
                    val scanner = BarcodeScanning.getClient(imageOptions)
                    scanner.process(image)
                        .addOnSuccessListener { codes -> decoded = codes.firstOrNull()?.rawValue; if (decoded == null) message = "No readable QR code was found in this image."; busy = false }
                        .addOnFailureListener { message = "Could not decode this image: ${it.localizedMessage ?: "QR decoder failed"}"; busy = false }
                        .addOnCompleteListener { scanner.close() }
                }
                .onFailure { message = "Could not open the selected image."; busy = false }
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Suspicious QR codes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Scan with Google Play services. GARUD receives the decoded text only and never opens it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = {
            busy = true; message = null; decoded = null
            GmsBarcodeScanning.getClient(context, options).startScan()
                .addOnSuccessListener { barcode -> decoded = barcode.rawValue; if (barcode.rawValue == null) message = "The QR code did not contain readable text."; busy = false }
                .addOnFailureListener { error -> message = "QR scan failed (camera denied or scanner unavailable): ${error.localizedMessage ?: "scanner unavailable"}"; busy = false }
                .addOnCanceledListener { message = "Scan cancelled."; busy = false }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "Opening scanner…" else "Scan a QR code")
        }
        OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Choose QR image") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        decoded?.let { value ->
            val url = UrlReputationClient.normalize(value)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (url != null) "QR contains a URL" else "QR contains non-URL text", fontWeight = FontWeight.Bold)
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                    if (url != null) Button(onClick = { onCheckUrl(url) }) { Text("Check URL reputation") }
                    else Text("URL reputation analysis does not apply. This content has not been opened.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Camera permission is handled by the system scanner when scanning starts. No scanning occurs in the background.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CallTranscriptionScreen(
    onSaveToHistory: (AnalysisResult) -> Boolean,
    onViewFullDetail: (AnalysisResult) -> Unit
) {
    val context = LocalContext.current
    var transcript by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Not recording") }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var startAfterPermission by remember { mutableStateOf(false) }
    var showConsent by remember { mutableStateOf(false) }
    var analysis by remember { mutableStateOf<AnalysisResult?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun begin() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { status = "No speech-recognition service is available on this device."; return }
        runCatching {
            val speech = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status = "Listening to nearby microphone audio" }
                override fun onBeginningOfSpeech() { status = "Listening…" }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status = "Transcribing…" }
                override fun onError(error: Int) { status = "Recognition stopped (error $error). No audio was saved."; recognizer?.destroy(); recognizer = null }
                override fun onResults(results: Bundle?) { transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty(); status = "Finished"; recognizer?.destroy(); recognizer = null }
                override fun onPartialResults(partialResults: Bundle?) { partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { transcript = it } }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) }
            status = "Starting microphone…"
            speech.startListening(intent)
        }.onFailure { status = "Could not start speech recognition: ${it.message ?: "unavailable"}" }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && startAfterPermission) begin() else status = "Microphone permission denied."
        startAfterPermission = false
    }
    DisposableEffect(Unit) { onDispose { recognizer?.cancel(); recognizer?.destroy(); recognizer = null } }
    if (showConsent) AlertDialog(
        onDismissRequest = { showConsent = false }, title = { Text("Start microphone transcription?") },
        text = { Text("GARUD will listen to nearby microphone audio, not system phone-call audio. The Android speech service configured on this device may process speech externally. Audio and transcript are not saved by GARUD. Only record or transcribe a conversation with everyone’s consent.") },
        confirmButton = { TextButton(onClick = { showConsent = false; startAfterPermission = true; permission.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { showConsent = false }) { Text("Cancel") } }
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Call transcription", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("GARUD cannot capture the other caller’s audio. This feature transcribes speech picked up by your microphone; it is not a phone-call recorder.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Status: $status", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { showConsent = true }, enabled = recognizer == null) { Text("Start transcription") }
            OutlinedButton(onClick = { if (recognizer != null) { recognizer?.stopListening(); status = "Stopping…" } }) { Text("Stop") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
            Text(transcript.ifBlank { "Transcript appears here while listening. It remains only on this screen." }, Modifier.fillMaxWidth().padding(18.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    val capturedTranscript = transcript.trim()
                    if (capturedTranscript.isNotBlank() && !analyzing) {
                        analyzing = true
                        analysis = null
                        saved = false
                        saveMessage = null
                        scope.launch {
                            try {
                                val data = NotificationData(
                                    packageName = "com.garud.security.calltranscript",
                                    appName = "Call transcript",
                                    title = "Call transcript",
                                    text = capturedTranscript
                                )
                                analysis = GarudApplication.instance.messageAnalysisService.analyzeLocal(data)
                            } catch (_: Exception) {
                                saveMessage = "Third Eye could not analyze this transcript. Please try again."
                            } finally {
                                analyzing = false
                            }
                        }
                    }
                },
                enabled = transcript.isNotBlank() && !analyzing
            ) {
                if (analyzing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Analyze with Third Eye")
            }
            TextButton(onClick = { transcript = ""; analysis = null; saved = false; saveMessage = null }) { Text("Clear transcript") }
        }
        Text("Transcript analysis stays on this device. It does not use the cloud setting.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        analysis?.let { result ->
            val riskColor = when (result.riskLevel) {
                RiskLevel.HIGH -> MaterialTheme.colorScheme.error
                RiskLevel.CAUTION -> GarudAmber
                RiskLevel.LOW -> GarudForestGreen
                RiskLevel.INSUFFICIENT_INFORMATION -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Third Eye result · ${result.riskLevel.name.replace('_', ' ')}", color = riskColor, fontWeight = FontWeight.Bold)
                    Text(result.explanation)
                    result.triggeredIndicators.forEach { indicator ->
                        Text("• ${indicator.title}: ${indicator.detail}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (result.safeActions.isNotEmpty()) {
                        Text("Recommended actions", fontWeight = FontWeight.SemiBold)
                        result.safeActions.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            saved = onSaveToHistory(result)
                            saveMessage = if (saved) "Saved to History." else "History is off. Enable it in Settings to save findings."
                        }, enabled = !saved) { Text(if (saved) "Saved" else "Save to History") }
                        TextButton(onClick = { onViewFullDetail(result) }) { Text("Full details") }
                    }
                    saveMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
