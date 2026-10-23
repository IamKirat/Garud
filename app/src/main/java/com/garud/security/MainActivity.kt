package com.garud.security

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garud.security.data.ThemeMode
import com.garud.security.model.AnalysisResult
import com.garud.security.service.AlertNotificationManager
import com.garud.security.ui.screens.*
import com.garud.security.ui.theme.GarudTheme

enum class AppScreen { WELCOME, NOTIFICATION_ACCESS, HOME, FINDING_DETAIL, SETTINGS, ANALYSE, FINDINGS, LINK_ANALYSIS, QR_ANALYSIS, CALL_TRANSCRIPTION }

class MainActivity : ComponentActivity() {
    private val postNotificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var selectedFindingIdState = mutableStateOf<String?>(null)
    private var isAccessGrantedState = mutableStateOf(false)
    private var selectedFindingState = mutableStateOf<AnalysisResult?>(null)
    private var sharedUrlState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            postNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleIntent(intent)
        setContent { GarudTheme { GarudAppContent() } }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }

    override fun onResume() {
        super.onResume()
        isAccessGrantedState.value = GarudApplication.isNotificationAccessGranted(this)
        GarudApplication.instance.repository.refreshFindings()
    }

    private fun handleIntent(intent: Intent?) {
        val findingId = intent?.getStringExtra(AlertNotificationManager.EXTRA_FINDING_ID)
        if (!findingId.isNullOrBlank()) selectedFindingIdState.value = findingId
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedUrlState.value = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.take(2048)
        }
    }

    @Composable
    private fun GarudAppContent() {
        val app = GarudApplication.instance
        val onboarding by app.preferences.isOnboardingCompleted.collectAsStateWithLifecycle()
        val paused by app.preferences.isMonitoringPaused.collectAsStateWithLifecycle()
        val historyEnabled by app.preferences.isHistoryEnabled.collectAsStateWithLifecycle()
        val alertOnCaution by app.preferences.alertOnCaution.collectAsStateWithLifecycle()
        val cloudEnabled by app.preferences.cloudAnalysisEnabled.collectAsStateWithLifecycle()
        val backendUrl by app.preferences.backendBaseUrl.collectAsStateWithLifecycle()
        val excluded by app.preferences.excludedPackages.collectAsStateWithLifecycle()
        val findings by app.repository.findings.collectAsStateWithLifecycle()
        val findingsLoading by app.repository.isLoading.collectAsStateWithLifecycle()
        val findingsError by app.repository.loadError.collectAsStateWithLifecycle()
        val serviceConnected by GarudApplication.isServiceConnected.collectAsStateWithLifecycle()
        val pipeline by GarudApplication.notificationPipeline.collectAsStateWithLifecycle()
        var detailReturn by remember { mutableStateOf(AppScreen.HOME) }
        var screen by rememberSaveable(onboarding) { mutableStateOf(if (onboarding) AppScreen.HOME else AppScreen.WELCOME) }
        LaunchedEffect(sharedUrlState.value) { if (sharedUrlState.value != null) screen = AppScreen.LINK_ANALYSIS }
        BackHandler(enabled = screen != AppScreen.HOME) {
            screen = when (screen) {
                AppScreen.FINDING_DETAIL -> detailReturn
                AppScreen.WELCOME -> { app.preferences.setOnboardingCompleted(true); AppScreen.HOME }
                else -> AppScreen.HOME
            }
        }

        val selectedTab = when (screen) {
            AppScreen.ANALYSE -> "ANALYSE"
            AppScreen.FINDINGS -> "FINDINGS"
            AppScreen.SETTINGS -> "SETTINGS"
            AppScreen.FINDING_DETAIL -> if (detailReturn == AppScreen.FINDINGS) "FINDINGS" else "HOME"
            else -> "HOME"
        }
        Scaffold(
            bottomBar = {
                if (screen != AppScreen.WELCOME) GarudBottomNavigation(selectedTab) { route ->
                    screen = when (route) { "ANALYSE" -> AppScreen.ANALYSE; "FINDINGS" -> AppScreen.FINDINGS; "SETTINGS" -> AppScreen.SETTINGS; else -> AppScreen.HOME }
                    if (route != "FINDINGS") { selectedFindingIdState.value = null; selectedFindingState.value = null }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (screen) {
                    AppScreen.WELCOME -> WelcomeScreen(
                        onSkip = { app.preferences.setOnboardingCompleted(true); screen = AppScreen.HOME },
                        onAuthenticationSuccess = { app.preferences.setOnboardingCompleted(true); screen = AppScreen.NOTIFICATION_ACCESS }
                    )
                    AppScreen.NOTIFICATION_ACCESS -> NotificationAccessScreen(isAccessGrantedState.value, serviceConnected, onBack = { screen = AppScreen.HOME }, onComplete = { screen = AppScreen.HOME })
                    AppScreen.HOME -> HomeScreen(
                        isAccessGranted = isAccessGrantedState.value, isServiceConnected = serviceConnected, notificationPipeline = pipeline,
                        isMonitoringPaused = paused, findings = findings, onTogglePause = { app.preferences.setMonitoringPaused(it) },
                        onSelectFinding = { id -> detailReturn = AppScreen.HOME; selectedFindingIdState.value = id; screen = AppScreen.FINDING_DETAIL },
                        onOpenSettings = { screen = AppScreen.SETTINGS }, onOpenPermissionsExplainer = { screen = AppScreen.NOTIFICATION_ACCESS },
                        onAnalyzeMessage = { screen = AppScreen.ANALYSE }, onOpenFindings = { screen = AppScreen.FINDINGS },
                        onOpenLinkAnalysis = { screen = AppScreen.LINK_ANALYSIS }, onOpenQrAnalysis = { screen = AppScreen.QR_ANALYSIS },
                        onOpenCallTranscription = { screen = AppScreen.CALL_TRANSCRIPTION }, onClearAllData = { app.repository.clearAllFindings() }
                    )
                    AppScreen.FINDING_DETAIL -> {
                        val id = selectedFindingIdState.value
                        val finding = selectedFindingState.value ?: remember(id, findings) { findings.firstOrNull { it.id == id } ?: id?.let(app.repository::getFindingById) }
                        FindingDetailScreen(finding, onBack = { selectedFindingIdState.value = null; selectedFindingState.value = null; screen = detailReturn }, onDelete = { key -> app.repository.deleteFinding(key); selectedFindingIdState.value = null; selectedFindingState.value = null; screen = detailReturn })
                    }
                    AppScreen.SETTINGS -> SettingsScreen(
                        isAccessGranted = isAccessGrantedState.value, isMonitoringPaused = paused, isHistoryEnabled = historyEnabled,
                        alertOnCaution = alertOnCaution, cloudAnalysisEnabled = cloudEnabled, backendBaseUrl = backendUrl,
                        excludedPackages = excluded, themeMode = app.preferences.themeMode.collectAsStateWithLifecycle().value,
                        onThemeModeChange = app.preferences::setThemeMode,
                        onTogglePause = app.preferences::setMonitoringPaused, onToggleHistory = app.preferences::setHistoryEnabled,
                        onToggleAlertOnCaution = app.preferences::setAlertOnCaution, onToggleCloudAnalysis = app.preferences::setCloudAnalysisEnabled,
                        onBackendBaseUrlChange = app.preferences::setBackendBaseUrl, onAddExcludedPackage = app.preferences::addExcludedPackage,
                        onRemoveExcludedPackage = app.preferences::removeExcludedPackage, onClearHistory = app.repository::clearAllFindings,
                        onClearAllData = app.repository::clearAllFindings,
                        onRevisitOnboarding = { screen = AppScreen.WELCOME }, onBack = { screen = AppScreen.HOME },
                        onOpenNotificationAccess = { screen = AppScreen.NOTIFICATION_ACCESS }
                    )
                    AppScreen.ANALYSE -> ManualMessageAnalysisScreen(
                        onBack = { screen = AppScreen.HOME },
                        onSaveToHistory = { result -> if (historyEnabled) { app.repository.insertFinding(result); true } else false },
                        onViewFullDetail = { result -> detailReturn = AppScreen.ANALYSE; selectedFindingIdState.value = result.id; selectedFindingState.value = result; screen = AppScreen.FINDING_DETAIL }
                    )
                    AppScreen.FINDINGS -> FindingsScreen(
                        findings = findings, onBack = { screen = AppScreen.HOME },
                        onSelectFinding = { id -> detailReturn = AppScreen.FINDINGS; selectedFindingIdState.value = id; selectedFindingState.value = findings.firstOrNull { it.id == id }; screen = AppScreen.FINDING_DETAIL },
                        onClearHistory = app.repository::clearAllFindings, isLoading = findingsLoading, loadError = findingsError, onRetry = app.repository::refreshFindings
                    )
                    AppScreen.LINK_ANALYSIS -> LinkAnalysisScreen(backendUrl, sharedUrlState.value, onConsumedInitialUrl = { sharedUrlState.value = null }, onBack = { screen = AppScreen.HOME })
                    AppScreen.QR_ANALYSIS -> QrAnalysisScreen(onCheckUrl = { url -> sharedUrlState.value = url; screen = AppScreen.LINK_ANALYSIS })
                    AppScreen.CALL_TRANSCRIPTION -> CallTranscriptionScreen(
                        onSaveToHistory = { result -> if (historyEnabled) { app.repository.insertFinding(result); true } else false },
                        onViewFullDetail = { result -> detailReturn = AppScreen.CALL_TRANSCRIPTION; selectedFindingIdState.value = result.id; selectedFindingState.value = result; screen = AppScreen.FINDING_DETAIL }
                    )
                }
            }
        }
    }
}
