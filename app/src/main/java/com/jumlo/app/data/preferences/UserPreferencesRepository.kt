package com.jumlo.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jumlo.app.core.ToolUsagePolicy
import com.jumlo.app.core.StoragePaths
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.AppSettingsState
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.model.FileViewMode
import com.jumlo.app.model.HapticIntensity
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
        val RECENT_TOOLS = stringPreferencesKey("recent_tools")
        val TOOL_COUNTS = stringPreferencesKey("tool_counts")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val RESPECT_REDUCED_MOTION = booleanPreferencesKey("respect_reduced_motion")
        val HAPTIC_ON_COMMIT = booleanPreferencesKey("haptic_on_commit")
        val HAPTIC_INTENSITY = stringPreferencesKey("haptic_intensity")
        val UI_SOUNDS = booleanPreferencesKey("ui_sounds")
        val REORDER_HAPTICS = booleanPreferencesKey("reorder_haptics")
        val DEFAULT_DEST_FOLDER = stringPreferencesKey("default_dest_folder")
        val DOWNLOAD_DEST_FOLDER = stringPreferencesKey("download_dest_folder")
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val WIFI_ONLY_DEFAULT = booleanPreferencesKey("wifi_only_default")
        val FILE_VIEW_MODE = stringPreferencesKey("file_view_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val FONT_SCALE = stringPreferencesKey("font_scale")
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
            hapticIntensity = if(prefs[Keys.HAPTIC_ON_COMMIT]==false) HapticIntensity.OFF else
                runCatching {HapticIntensity.valueOf(prefs[Keys.HAPTIC_INTENSITY] ?: "LIGHT")}.getOrDefault(HapticIntensity.LIGHT),
            soundsEnabled = prefs[Keys.UI_SOUNDS] ?: true,
            reorderHandleHaptics = prefs[Keys.REORDER_HAPTICS] ?: true,
            defaultDestinationFolder = prefs[Keys.DEFAULT_DEST_FOLDER] ?: "My files / Results",
            downloadDestinationFolder = StoragePaths.canonicalDownloadFolder(prefs[Keys.DOWNLOAD_DEST_FOLDER]),
            maxConcurrentDownloads = (prefs[Keys.MAX_CONCURRENT_DOWNLOADS] ?: 2).coerceIn(1, 6),
            wifiOnlyDefault = false,
            fileViewMode = runCatching { FileViewMode.valueOf(prefs[Keys.FILE_VIEW_MODE] ?: "GRID") }.getOrDefault(FileViewMode.GRID),
            accentColor = (prefs[Keys.ACCENT_COLOR] ?: "VIOLET").takeIf { it in setOf("VIOLET","BLUE","TEAL","CORAL","DYNAMIC") } ?: "VIOLET",
            fontScale = (prefs[Keys.FONT_SCALE] ?: "NORMAL").takeIf { it in setOf("SMALL","NORMAL","LARGE") } ?: "NORMAL",
            stripExifByDefault = prefs[Keys.STRIP_EXIF_DEFAULT] ?: true,
            notificationsGranted = prefs[Keys.NOTIFICATIONS_GRANTED] ?: false,
            vaultAutoLockSeconds = prefs[Keys.VAULT_AUTO_LOCK_SEC] ?: 120,
            mediaTermsAccepted = prefs[Keys.MEDIA_TERMS_ACCEPTED] ?: false,
            quickActionIds = prefs[Keys.QUICK_ACTION_IDS]?.split('|')?.filter { it.isNotBlank() } ?: AppSettingsState().quickActionIds,
            recentToolIds = prefs[Keys.RECENT_TOOLS]?.split('|')?.filter {it.matches(Regex("[a-z0-9_]{1,64}"))}?.take(8).orEmpty(),
            mostUsedToolIds = prefs[Keys.TOOL_COUNTS].orEmpty().split('|').mapNotNull {value->
                val id=value.substringBefore('=');val n=value.substringAfter('=',"").toIntOrNull()
                if(id.matches(Regex("[a-z0-9_]{1,64}")) && n!=null && n>0) id to n else null
            }.sortedByDescending {it.second}.take(5).map {it.first},
            isLoaded = true
        )
    }

    suspend fun recordToolOpen(id:String) {
        if(!id.matches(Regex("[a-z0-9_]{1,64}"))) return
        context.dataStore.edit {prefs ->
            val oldRecent=prefs[Keys.RECENT_TOOLS].orEmpty().split('|').filter(String::isNotBlank)
            prefs[Keys.RECENT_TOOLS]=ToolUsagePolicy.recent(oldRecent,id).joinToString("|")
            val oldCounts=prefs[Keys.TOOL_COUNTS].orEmpty().split('|').mapNotNull {entry ->
                val key=entry.substringBefore('='); val count=entry.substringAfter('=',"").toIntOrNull()
                if(key.matches(Regex("[a-z0-9_]{1,64}")) && count!=null) key to count else null
            }.toMap()
            prefs[Keys.TOOL_COUNTS]=ToolUsagePolicy.count(oldCounts,id).entries.joinToString("|") { "${it.key}=${it.value}" }
        }
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
        context.dataStore.edit { prefs ->
            prefs[Keys.HAPTIC_ON_COMMIT] = enabled
            prefs[Keys.HAPTIC_INTENSITY] = if(enabled) "LIGHT" else "OFF"
        }
    }
    suspend fun setHapticIntensity(intensity: HapticIntensity) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAPTIC_INTENSITY] = intensity.name
            prefs[Keys.HAPTIC_ON_COMMIT] = intensity != HapticIntensity.OFF
        }
    }
    suspend fun setSoundsEnabled(enabled:Boolean) {
        context.dataStore.edit { it[Keys.UI_SOUNDS] = enabled }
    }

    suspend fun setDownloadDestinationFolder(treeUri: String) {
        require(treeUri.startsWith("content://")) { "INVALID_TREE_URI" }
        context.dataStore.edit { it[Keys.DOWNLOAD_DEST_FOLDER] = treeUri }
    }

    suspend fun setFileViewMode(value: FileViewMode) { context.dataStore.edit { it[Keys.FILE_VIEW_MODE] = value.name } }
    suspend fun setAccentColor(value: String) {
        require(value in setOf("VIOLET","BLUE","TEAL","CORAL","DYNAMIC"))
        context.dataStore.edit { it[Keys.ACCENT_COLOR] = value }
    }
    suspend fun setFontScale(value: String) {
        require(value in setOf("SMALL","NORMAL","LARGE"))
        context.dataStore.edit { it[Keys.FONT_SCALE] = value }
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
}
