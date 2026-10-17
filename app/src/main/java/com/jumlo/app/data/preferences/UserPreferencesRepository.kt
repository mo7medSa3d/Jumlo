package com.jumlo.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.AppSettingsState
import com.jumlo.app.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "universal_utilities_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val QUICK_ACTION_IDS = stringPreferencesKey("quick_action_ids")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val RESPECT_REDUCED_MOTION = booleanPreferencesKey("respect_reduced_motion")
        val HAPTIC_ON_COMMIT = booleanPreferencesKey("haptic_on_commit")
        val REORDER_HAPTICS = booleanPreferencesKey("reorder_haptics")
        val DEFAULT_DEST_FOLDER = stringPreferencesKey("default_dest_folder")
        val DOWNLOAD_DEST_FOLDER = stringPreferencesKey("download_dest_folder")
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val WIFI_ONLY_DEFAULT = booleanPreferencesKey("wifi_only_default")
        val STRIP_EXIF_DEFAULT = booleanPreferencesKey("strip_exif_default")
        val NOTIFICATIONS_GRANTED = booleanPreferencesKey("notifications_granted")
        val VAULT_AUTO_LOCK_SEC = intPreferencesKey("vault_auto_lock_sec")
        val MEDIA_TERMS_ACCEPTED = booleanPreferencesKey("media_terms_accepted")
    }

    val settingsFlow: Flow<AppSettingsState> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val defaultLanguage = if (java.util.Locale.getDefault().language == "ar") AppLanguage.ARABIC else AppLanguage.ENGLISH
        val langStr = prefs[Keys.LANGUAGE] ?: defaultLanguage.name
        AppSettingsState(
            hasCompletedOnboarding = prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: false,
            themeMode = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.SYSTEM),
            language = runCatching { AppLanguage.valueOf(langStr) }.getOrDefault(defaultLanguage),
            respectReducedMotion = prefs[Keys.RESPECT_REDUCED_MOTION] ?: false,
            hapticFeedbackOnCommit = prefs[Keys.HAPTIC_ON_COMMIT] ?: true,
            reorderHandleHaptics = prefs[Keys.REORDER_HAPTICS] ?: true,
            defaultDestinationFolder = prefs[Keys.DEFAULT_DEST_FOLDER] ?: "My files / Results",
            downloadDestinationFolder = prefs[Keys.DOWNLOAD_DEST_FOLDER] ?: "My files / Downloads",
            maxConcurrentDownloads = (prefs[Keys.MAX_CONCURRENT_DOWNLOADS] ?: 2).coerceIn(1, 6),
            wifiOnlyDefault = prefs[Keys.WIFI_ONLY_DEFAULT] ?: true,
            stripExifByDefault = prefs[Keys.STRIP_EXIF_DEFAULT] ?: true,
            notificationsGranted = prefs[Keys.NOTIFICATIONS_GRANTED] ?: false,
            vaultAutoLockSeconds = prefs[Keys.VAULT_AUTO_LOCK_SEC] ?: 120,
            mediaTermsAccepted = prefs[Keys.MEDIA_TERMS_ACCEPTED] ?: false,
            quickActionIds = prefs[Keys.QUICK_ACTION_IDS]?.split('|')?.filter { it.isNotBlank() } ?: AppSettingsState().quickActionIds,
            isLoaded = true
        )
    }

    suspend fun setQuickActionIds(ids: List<String>) {
        val safe=ids.filter { Regex("[a-z0-9_]{1,64}").matches(it) }.distinct().take(4)
        context.dataStore.edit { it[Keys.QUICK_ACTION_IDS] = safe.joinToString("|") }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.HAS_COMPLETED_ONBOARDING] = completed }
    }

    suspend fun setRespectReducedMotion(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RESPECT_REDUCED_MOTION] = enabled }
    }

    suspend fun setHapticOnCommit(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_ON_COMMIT] = enabled }
    }

    suspend fun setReorderHaptics(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REORDER_HAPTICS] = enabled }
    }

    suspend fun setMaxConcurrentDownloads(max: Int) {
        context.dataStore.edit { it[Keys.MAX_CONCURRENT_DOWNLOADS] = max.coerceIn(1, 6) }
    }

    suspend fun setWifiOnlyDefault(wifiOnly: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_ONLY_DEFAULT] = wifiOnly }
    }

    suspend fun setNotificationsGranted(granted: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_GRANTED] = granted }
    }

    suspend fun setVaultAutoLockSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.VAULT_AUTO_LOCK_SEC] = seconds }
    }

    suspend fun setMediaTermsAccepted(accepted: Boolean) {
        context.dataStore.edit { it[Keys.MEDIA_TERMS_ACCEPTED] = accepted }
    }
}
