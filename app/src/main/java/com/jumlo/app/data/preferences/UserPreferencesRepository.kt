package com.jumlo.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jumlo.app.BuildConfig
import com.jumlo.app.core.OutputFavorites
import com.jumlo.app.core.ToolUsagePolicy
import com.jumlo.app.core.StoragePaths
import com.jumlo.app.diagnostics.AppDiagnostics
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
import kotlinx.coroutines.flow.combine
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
    private val mediaServiceSettings = MediaServiceSettingsStore(
        context.dataStore,
        KeystoreMediaServiceTokenCipher("${context.packageName}.media-service-token.v1"),
        allowDebugLoopback = BuildConfig.DEBUG,
        report = { area, error -> AppDiagnostics.report(area, error) }
    )
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
        val DEFAULT_VIDEO_QUALITY = stringPreferencesKey("default_video_quality")
        val DEFAULT_AUDIO_QUALITY = intPreferencesKey("default_audio_quality")
        val FILE_VIEW_MODE = stringPreferencesKey("file_view_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val FONT_SCALE = stringPreferencesKey("font_scale")
        val NOTIFICATIONS_GRANTED = booleanPreferencesKey("notifications_granted")
        val MEDIA_RIGHTS_ACCEPTED = booleanPreferencesKey("media_rights_accepted")
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
     * Optional alternate extraction service. Empty keeps the packaged/on-device
     * engines unchanged and prevents requests to a backend.
     */
    val mediaServiceUrlSnapshot: String get() = mediaServiceSettings.state.value.url

    val mediaServiceTokenSnapshot: String get() = mediaServiceSettings.state.value.token

    /** Read both values from one state so an in-flight settings change cannot mix origins. */
    val mediaServiceConfigurationSnapshot: Pair<String, String> get() =
        mediaServiceSettings.state.value.let { it.url to it.token }

    init {
        // The observer migrates plaintext before exposure, handles errors and clears stale state.
        scope.launch { mediaServiceSettings.observe() }
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
            prefs[Keys.PROCESSING_PRESETS]=Json.encodeToString(replacement)
        }
    }
    suspend fun removeProcessingPreset(toolId: String,name: String) {
        context.dataStore.edit { prefs ->
            val old=decodePresets(prefs[Keys.PROCESSING_PRESETS])
            val replacement=old.filterNot { it.toolId==toolId && it.name==name }
            prefs[Keys.PROCESSING_PRESETS]=Json.encodeToString(replacement)
        }
    }

    val settingsFlow: Flow<AppSettingsState> = combine(context.dataStore.data, mediaServiceSettings.state) { prefs, media ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val defaultLanguage = if (java.util.Locale.getDefault().language == "ar") AppLanguage.ARABIC else AppLanguage.ENGLISH
        val langStr = prefs[Keys.LANGUAGE] ?: defaultLanguage.name
        val counts = prefs[Keys.TOOL_COUNTS].orEmpty().split('|').mapNotNull { value ->
            val id = value.substringBefore('=')
            val count = value.substringAfter('=', "").toIntOrNull()
            if (id.matches(Regex("[a-z0-9_]{1,64}")) && count != null && count > 0) id to count else null
        }.toMap()
        val rankedTools = ToolUsagePolicy.ranked(counts, com.jumlo.app.data.jobs.CapabilityRegistry.tools.map { it.id })
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
            maxConcurrentDownloads = 2, // Fixed scheduler policy; legacy preference no longer controls jobs.
            defaultVideoQuality = (prefs[Keys.DEFAULT_VIDEO_QUALITY] ?: "1080").takeIf { it in setOf("BEST", "1080", "720", "480") } ?: "1080",
            defaultAudioQuality = (prefs[Keys.DEFAULT_AUDIO_QUALITY] ?: 192).takeIf { it in setOf(320, 192, 128) } ?: 192,
            wifiOnlyDefault = false,
            fileViewMode = FileViewMode.fromStored(prefs[Keys.FILE_VIEW_MODE]),
            accentColor = (prefs[Keys.ACCENT_COLOR] ?: "BLUE").takeIf { isValidAccent(it) } ?: "BLUE",
            fontScale = (prefs[Keys.FONT_SCALE] ?: "NORMAL").takeIf { it in setOf("SMALL","NORMAL","LARGE") } ?: "NORMAL",
            notificationsGranted = prefs[Keys.NOTIFICATIONS_GRANTED] ?: false,
            quickActionIds = prefs[Keys.QUICK_ACTION_IDS]?.split('|')?.filter { it.isNotBlank() } ?: AppSettingsState().quickActionIds,
            recentToolIds = prefs[Keys.RECENT_TOOLS]?.split('|')?.filter { it.matches(Regex("[a-z0-9_]{1,64}")) }.orEmpty(),
            mostUsedToolIds = rankedTools,
            rankedToolIds = rankedTools,
            favoriteOutputIds = OutputFavorites.sanitize(
                prefs[Keys.FAVORITE_OUTPUT_IDS].orEmpty().split('|').filter(String::isNotBlank)
            ).toSet(),
            mediaServiceUrl = media.url,
            mediaServiceToken = media.token,
            outputNames = runCatching {
                Json.decodeFromString<Map<String,String>>(prefs[Keys.OUTPUT_NAMES] ?: "{}").filter { (id,name) ->
                    com.jumlo.app.core.OutputWorkflowPolicy.isId(id) && name.isNotBlank() && name.length<=200 && name.none { it<' ' || it=='/' || it=='\\' }
                }
            }.getOrDefault(emptyMap()),
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

    suspend fun setDefaultVideoQuality(value: String) {
        require(value in setOf("BEST", "1080", "720", "480")) { "INVALID_VIDEO_QUALITY" }
        context.dataStore.edit { it[Keys.DEFAULT_VIDEO_QUALITY] = value }
    }

    suspend fun setDefaultAudioQuality(value: Int) {
        require(value in setOf(320, 192, 128)) { "INVALID_AUDIO_QUALITY" }
        context.dataStore.edit { it[Keys.DEFAULT_AUDIO_QUALITY] = value }
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

    val mediaServiceUrlFlow: Flow<String> = mediaServiceSettings.state.map { it.url }

    /** Release backends require HTTPS; debug additionally permits explicit loopback HTTP. */
    suspend fun setMediaServiceUrl(url: String) = mediaServiceSettings.setEndpoint(url)

    suspend fun setMediaServiceToken(token: String) = mediaServiceSettings.setToken(token)

    suspend fun setNotificationsGranted(granted: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_GRANTED] = granted }
    }

    /** Accent is either a named preset or a #RRGGBB hex seed. */
    private fun isValidAccent(value: String): Boolean =
        value in setOf("VIOLET", "BLUE", "TEAL", "CORAL", "DYNAMIC") ||
            (value.removePrefix("#").let { it.length == 6 && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' } })
}
