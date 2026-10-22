package com.jumlo.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jumlo.app.core.OutputFavorites
import com.jumlo.app.core.ToolUsagePolicy
import com.jumlo.app.core.StoragePaths
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.AppSettingsState
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.model.FileViewMode
import com.jumlo.app.model.HapticIntensity
import com.jumlo.app.model.ProcessingPreset
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "universal_utilities_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private object Keys {
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val PROCESSING_PRESETS = stringPreferencesKey("local_processing_presets_v1")
        val QUICK_ACTION_IDS = stringPreferencesKey("quick_action_ids")
        val OUTPUT_NAMES = stringPreferencesKey("output_names_v1")
        val FAVORITE_OUTPUT_IDS = stringPreferencesKey("favorite_output_ids")
        val RECENT_TOOLS = stringPreferencesKey("recent_tools")
        val TOOL_COUNTS = stringPreferencesKey("tool_counts")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val RESPECT_REDUCED_MOTION = booleanPreferencesKey("respect_reduced_motion")
        val HAPTIC_ON_COMMIT = booleanPreferencesKey("haptic_on_commit")
        val HAPTIC_INTENSITY = stringPreferencesKey("haptic_intensity")
        val UI_SOUNDS = booleanPreferencesKey("ui_sounds")
        val DOWNLOAD_DEST_FOLDER = stringPreferencesKey("download_dest_folder")
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val WIFI_ONLY_DEFAULT = booleanPreferencesKey("wifi_only_default")
        val FILE_VIEW_MODE = stringPreferencesKey("file_view_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val FONT_SCALE = stringPreferencesKey("font_scale")
        val NOTIFICATIONS_GRANTED = booleanPreferencesKey("notifications_granted")
        val MEDIA_SERVICE_URL = stringPreferencesKey("media_service_url_v1")
        val MEDIA_SERVICE_TOKEN = stringPreferencesKey("media_service_token_v1")
        val MEDIA_RIGHTS_ACCEPTED = booleanPreferencesKey("media_rights_accepted")
        val DEFAULT_VIDEO_QUALITY = stringPreferencesKey("default_video_quality")
        val DEFAULT_AUDIO_QUALITY = stringPreferencesKey("default_audio_quality")
    }

    /**
     * The user must confirm they have the rights to the content before a media
     * download is queued. The answer is remembered so it is asked once, and it can
     * be withdrawn again in Settings.
     */
    val mediaRightsAcceptedFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.MEDIA_RIGHTS_ACCEPTED] ?: false }

    suspend fun setMediaRightsAccepted(accepted: Boolean) {
        context.dataStore.edit { it[Keys.MEDIA_RIGHTS_ACCEPTED] = accepted }
    }

    /**
     * The extraction service the downloader may use. Empty means the app only offers
     * direct media links, which is the honest default for a store build.
     */
    @Volatile var mediaServiceUrlSnapshot: String = ""
        private set

    @Volatile var mediaServiceTokenSnapshot: String = ""
        private set

    init {
        // A cheap snapshot so the runtime can read the setting without suspending.
        scope.launch {
            context.dataStore.data.collect { prefs ->
                mediaServiceUrlSnapshot = prefs[Keys.MEDIA_SERVICE_URL].orEmpty()
                mediaServiceTokenSnapshot = prefs[Keys.MEDIA_SERVICE_TOKEN].orEmpty()
            }
        }
    }

    // Fail closed on corruption. A write must never silently overwrite unreadable data.
    private fun decodePresets(raw: String?): List<ProcessingPreset> {
        if(raw.isNullOrBlank()) return emptyList()
        return Json.decodeFromString<List<ProcessingPreset>>(raw).also { items ->
            require(items.size <= 16 && items.all { it.version==1 &&
                it.name.trim().length in 1..48 && it.toolId.matches(Regex("[a-z0-9_]{1,64}"))
            }) { "PRESET_INVALID" }
        }
    }
    val processingPresetsFlow: Flow<List<ProcessingPreset>> = context.dataStore.data.map { prefs ->
        decodePresets(prefs[Keys.PROCESSING_PRESETS])
    }
    suspend fun setProcessingPreset(preset: ProcessingPreset) {
        require(preset.version==1) { "PRESET_INVALID" }
        context.dataStore.edit { prefs ->
            val old=decodePresets(prefs[Keys.PROCESSING_PRESETS])
            val replacement=old.filterNot { it.toolId==preset.toolId && it.name==preset.name } + preset
            prefs[Keys.PROCESSING_PRESETS]=Json.encodeToString(replacement.takeLast(16))
        }
    }
    suspend fun removeProcessingPreset(toolId: String,name: String) {
        context.dataStore.edit { prefs ->
            val old=decodePresets(prefs[Keys.PROCESSING_PRESETS])
            val replacement=old.filterNot { it.toolId==toolId && it.name==name }
            prefs[Keys.PROCESSING_PRESETS]=Json.encodeToString(replacement.take(16))
        }
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
                runCatching { HapticIntensity.valueOf(prefs[Keys.HAPTIC_INTENSITY] ?: "LIGHT") }.getOrDefault(HapticIntensity.LIGHT),
            soundsEnabled = prefs[Keys.UI_SOUNDS] ?: true,
            downloadDestinationFolder = StoragePaths.canonicalDownloadFolder(prefs[Keys.DOWNLOAD_DEST_FOLDER]),
            maxConcurrentDownloads = (prefs[Keys.MAX_CONCURRENT_DOWNLOADS] ?: 2).coerceIn(1, 6),
            wifiOnlyDefault = prefs[Keys.WIFI_ONLY_DEFAULT] ?: false,
            fileViewMode = runCatching { FileViewMode.valueOf(prefs[Keys.FILE_VIEW_MODE] ?: "GRID") }.getOrDefault(FileViewMode.GRID).let { if (it == FileViewMode.GALLERY) FileViewMode.GRID else it },
            accentColor = (prefs[Keys.ACCENT_COLOR] ?: "BLUE").takeIf { it in setOf("VIOLET","BLUE","TEAL","CORAL","DYNAMIC") } ?: "BLUE",
            fontScale = (prefs[Keys.FONT_SCALE] ?: "NORMAL").takeIf { it in setOf("SMALL","NORMAL","LARGE") } ?: "NORMAL",
            notificationsGranted = prefs[Keys.NOTIFICATIONS_GRANTED] ?: false,
            quickActionIds = prefs[Keys.QUICK_ACTION_IDS]?.split('|')?.filter { it.isNotBlank() } ?: AppSettingsState().quickActionIds,
            recentToolIds = prefs[Keys.RECENT_TOOLS]?.split('|')?.filter {it.matches(Regex("[a-z0-9_]{1,64}"))}?.take(8).orEmpty(),
            mostUsedToolIds = prefs[Keys.TOOL_COUNTS].orEmpty().split('|').mapNotNull {value->
                val id=value.substringBefore('=');val n=value.substringAfter('=',"").toIntOrNull()
                if(id.matches(Regex("[a-z0-9_]{1,64}")) && n!=null && n>0) id to n else null
            }.sortedByDescending {it.second}.take(5).map {it.first},
            favoriteOutputIds = OutputFavorites.sanitize(
                prefs[Keys.FAVORITE_OUTPUT_IDS].orEmpty().split('|').filter(String::isNotBlank)
            ).toSet(),
            mediaServiceUrl = prefs[Keys.MEDIA_SERVICE_URL].orEmpty(),
            mediaServiceToken = prefs[Keys.MEDIA_SERVICE_TOKEN].orEmpty(),
            outputNames = runCatching {
                Json.decodeFromString<Map<String,String>>(prefs[Keys.OUTPUT_NAMES] ?: "{}").filter { (id,name) ->
                    com.jumlo.app.core.OutputWorkflowPolicy.isId(id) && name.isNotBlank() && name.length<=200 && name.none { it<' ' || it=='/' || it=='\\' }
                }
            }.getOrDefault(emptyMap()),
            defaultVideoQuality = (prefs[Keys.DEFAULT_VIDEO_QUALITY] ?: "1080p").takeIf { it in setOf("BEST", "1080p", "720p", "480p") } ?: "1080p",
            defaultAudioQuality = (prefs[Keys.DEFAULT_AUDIO_QUALITY] ?: "MP3_320").takeIf { it in setOf("MP3_320", "MP3_192", "M4A", "ORIGINAL") } ?: "MP3_320",
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

    suspend fun setOutputName(id:String,name:String?) {
        require(com.jumlo.app.core.OutputWorkflowPolicy.isId(id)) { "INVALID_OUTPUT_ID" }
        context.dataStore.edit { prefs ->
            val old=runCatching { Json.decodeFromString<Map<String,String>>(prefs[Keys.OUTPUT_NAMES] ?: "{}") }.getOrDefault(emptyMap())
            val next=old.toMutableMap()
            if(name==null) next.remove(id) else next[id]=com.jumlo.app.core.SafeNames.basename(name)
            prefs[Keys.OUTPUT_NAMES]=Json.encodeToString(next)
        }
    }

    suspend fun toggleFavoriteOutput(id: String) {
        context.dataStore.edit { prefs ->
            val old = prefs[Keys.FAVORITE_OUTPUT_IDS].orEmpty().split('|').filter(String::isNotBlank)
            prefs[Keys.FAVORITE_OUTPUT_IDS] = OutputFavorites.toggle(old, id).joinToString("|")
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

    suspend fun resetDownloadDestinationFolder() {
        context.dataStore.edit { it.remove(Keys.DOWNLOAD_DEST_FOLDER) }
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

    val mediaServiceUrlFlow: Flow<String> = context.dataStore.data.map { it[Keys.MEDIA_SERVICE_URL].orEmpty() }

    /** Only http/https endpoints are accepted, so a mistyped value cannot point the app elsewhere. */
    suspend fun setMediaServiceUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            context.dataStore.edit { it.remove(Keys.MEDIA_SERVICE_URL) }
            return
        }
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) { "INVALID_MEDIA_SERVICE_URL" }
        require(trimmed.length <= 300) { "INVALID_MEDIA_SERVICE_URL" }
        context.dataStore.edit { it[Keys.MEDIA_SERVICE_URL] = trimmed }
    }

    suspend fun setMediaServiceToken(token: String) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            context.dataStore.edit { it.remove(Keys.MEDIA_SERVICE_TOKEN) }
            return
        }
        require(trimmed.length <= 300) { "INVALID_MEDIA_SERVICE_TOKEN" }
        context.dataStore.edit { it[Keys.MEDIA_SERVICE_TOKEN] = trimmed }
    }

    suspend fun setNotificationsGranted(granted: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_GRANTED] = granted }
    }

    suspend fun setDefaultVideoQuality(quality: String) {
        require(quality in setOf("BEST", "1080p", "720p", "480p"))
        context.dataStore.edit { it[Keys.DEFAULT_VIDEO_QUALITY] = quality }
    }

    suspend fun setDefaultAudioQuality(quality: String) {
        require(quality in setOf("MP3_320", "MP3_192", "M4A", "ORIGINAL"))
        context.dataStore.edit { it[Keys.DEFAULT_AUDIO_QUALITY] = quality }
    }
}
