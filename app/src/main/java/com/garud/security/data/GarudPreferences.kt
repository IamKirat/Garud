package com.garud.security.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Manages user preferences and settings for GARUD.
 * Persisted locally on device.
 */
class GarudPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    private val _isMonitoringPaused = MutableStateFlow(prefs.getBoolean(KEY_MONITORING_PAUSED, false))
    val isMonitoringPaused: StateFlow<Boolean> = _isMonitoringPaused.asStateFlow()

    private val _isOnboardingCompleted = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false))
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    private val _isHistoryEnabled = MutableStateFlow(prefs.getBoolean(KEY_HISTORY_ENABLED, true))
    val isHistoryEnabled: StateFlow<Boolean> = _isHistoryEnabled.asStateFlow()

    private val _alertOnCaution = MutableStateFlow(prefs.getBoolean(KEY_ALERT_ON_CAUTION, false))
    val alertOnCaution: StateFlow<Boolean> = _alertOnCaution.asStateFlow()

    private val _cloudAnalysisEnabled = MutableStateFlow(prefs.getBoolean(KEY_CLOUD_ANALYSIS_ENABLED, false))
    val cloudAnalysisEnabled: StateFlow<Boolean> = _cloudAnalysisEnabled.asStateFlow()

    private val _backendBaseUrl = MutableStateFlow(prefs.getString(KEY_BACKEND_BASE_URL, "") ?: "")
    val backendBaseUrl: StateFlow<String> = _backendBaseUrl.asStateFlow()

    private val _excludedPackages = MutableStateFlow(
        prefs.getStringSet(KEY_EXCLUDED_PACKAGES, DEFAULT_EXCLUDED_PACKAGES) ?: DEFAULT_EXCLUDED_PACKAGES
    )
    val excludedPackages: StateFlow<Set<String>> = _excludedPackages.asStateFlow()

    fun setMonitoringPaused(paused: Boolean) {
        prefs.edit().putBoolean(KEY_MONITORING_PAUSED, paused).apply()
        _isMonitoringPaused.value = paused
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        _isOnboardingCompleted.value = completed
    }

    fun setHistoryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HISTORY_ENABLED, enabled).apply()
        _isHistoryEnabled.value = enabled
    }

    fun setAlertOnCaution(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALERT_ON_CAUTION, enabled).apply()
        _alertOnCaution.value = enabled
    }

    fun setCloudAnalysisEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLOUD_ANALYSIS_ENABLED, enabled).apply()
        _cloudAnalysisEnabled.value = enabled
    }

    fun setBackendBaseUrl(url: String) {
        val safeUrl = url.trim().take(512)
        prefs.edit().putString(KEY_BACKEND_BASE_URL, safeUrl).apply()
        _backendBaseUrl.value = safeUrl
    }

    fun addExcludedPackage(packageName: String) {
        val updated = _excludedPackages.value.toMutableSet().apply { add(packageName) }
        prefs.edit().putStringSet(KEY_EXCLUDED_PACKAGES, updated).apply()
        _excludedPackages.value = updated
    }

    fun removeExcludedPackage(packageName: String) {
        val updated = _excludedPackages.value.toMutableSet().apply { remove(packageName) }
        prefs.edit().putStringSet(KEY_EXCLUDED_PACKAGES, updated).apply()
        _excludedPackages.value = updated
    }

    fun isPackageExcluded(packageName: String): Boolean {
        return _excludedPackages.value.contains(packageName)
    }

    companion object {
        private const val PREFS_NAME = "garud_preferences"
        private const val KEY_MONITORING_PAUSED = "monitoring_paused"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_HISTORY_ENABLED = "history_enabled"
        private const val KEY_ALERT_ON_CAUTION = "alert_on_caution"
        private const val KEY_CLOUD_ANALYSIS_ENABLED = "cloud_analysis_enabled"
        private const val KEY_BACKEND_BASE_URL = "backend_base_url"
        private const val KEY_EXCLUDED_PACKAGES = "excluded_packages"

        // Exclude system UI or known internal components by default
        val DEFAULT_EXCLUDED_PACKAGES = setOf(
            "com.android.systemui",
            "android"
        )
    }
}
