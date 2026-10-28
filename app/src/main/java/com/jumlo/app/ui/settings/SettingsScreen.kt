package com.jumlo.app.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.HapticIntensity
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.SaveFolderCard
import com.jumlo.app.ui.components.formatBytes
import com.jumlo.app.ui.theme.ColorPresets
import com.jumlo.app.ui.theme.M3ColorFactory
import com.jumlo.app.ui.theme.QuietTheme

/** Preferences that change an everyday action. Platform and scheduler policy stay out of this page. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun SettingsScreen(
    viewModel: AppViewModel,
    onRequestNotifications: () -> Unit,
    onExportLogs: () -> Unit,
    onOpenFiles: () -> Unit,
    onChooseFolder: () -> Unit,
    onChooseDownloadFolder: () -> Unit,
    onOpenSavedFolder: () -> Unit,
    onShowGuide: () -> Unit = {},
    onOpenStorage: (() -> Unit)? = null
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val outputs by viewModel.outputs.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.refreshStorage() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings.screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = QuietTheme.spacing.listBottomBuffer),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item("appearance-heading") { PreferenceHeading(stringResource(R.string.pref_appearance)) }
        item("theme") { PreferenceRow(stringResource(R.string.pref_theme), themeLabel(settings.themeMode),
            Icons.Rounded.Palette, { panel = "theme" }, Modifier.testTag("settings.theme")) }
        if (Build.VERSION.SDK_INT >= 31) item("dynamic-color") {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .toggleable(settings.accentColor == "DYNAMIC", role = Role.Switch,
                    onValueChange = { viewModel.setAccentColor(if (it) "DYNAMIC" else "BLUE") })
                .padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.modern_dynamic_color), Modifier.weight(1f), style = QuietTheme.typography.body)
                Switch(settings.accentColor == "DYNAMIC", onCheckedChange = null)
            }
        }
        item("app-color") { PreferenceRow(stringResource(R.string.pref_app_color),
            previewAccentLabel(settings.accentColor), Icons.Rounded.Palette, { panel = "app-color" },
            Modifier.testTag("settings.appColor")) }
        item("language") { PreferenceRow(stringResource(R.string.pref_language), languageLabel(settings.language),
            Icons.Rounded.Language, { panel = "language" }, Modifier.testTag("settings.language")) }
        item("haptics") { PreferenceRow(stringResource(R.string.pref_haptics),
            hapticLabel(settings.hapticIntensity), Icons.Rounded.Vibration,
            { panel = "haptics" }, Modifier.testTag("settings.haptics")) }
        item("reduce-motion") {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .testTag("settings.reduceMotion")
                .toggleable(value = settings.respectReducedMotion, role = Role.Switch,
                    onValueChange = { viewModel.setRespectReducedMotion(it) })
                .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_reduce_motion), style = QuietTheme.typography.body)
                    Text(stringResource(R.string.pref_reduce_motion_summary),
                        style = QuietTheme.typography.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.respectReducedMotion, onCheckedChange = null)
            }
        }
        item("sounds") {
            PreferenceToggleRow(stringResource(R.string.settings_ui_sounds),
                settings.soundsEnabled, viewModel::setSoundsEnabled,
                Modifier.testTag("settings.sounds"))
        }
        item("save-heading") { PreferenceHeading(stringResource(R.string.pref_save_location)) }
        item("save-folder") {
            SaveFolderCard(settings.downloadDestinationFolder, onChooseDownloadFolder, onOpenSavedFolder)
            if (settings.downloadDestinationFolder.startsWith("content://")) TextButton(
                onClick = viewModel::resetDownloadDestinationFolder,
                modifier = Modifier.testTag("settings.resetDownloadLocation")) {
                Text(stringResource(R.string.modern_reset_save_folder))
            }
        }
        item("defaults-heading") { PreferenceHeading(stringResource(R.string.modern_download_defaults)) }
        item("video-quality") { PreferenceRow(stringResource(R.string.modern_video_quality),
            if (settings.defaultVideoQuality == "BEST") stringResource(R.string.modern_best_available)
            else "${settings.defaultVideoQuality}p", Icons.Rounded.Movie, { panel = "video" }, Modifier.testTag("settings.videoQuality")) }
        item("audio-quality") { PreferenceRow(stringResource(R.string.modern_audio_quality),
            stringResource(R.string.modern_audio_bitrate, settings.defaultAudioQuality), Icons.Rounded.Audiotrack,
            { panel = "audio" }, Modifier.testTag("settings.audioQuality")) }
        item("defaults-note") { Text(stringResource(R.string.modern_quality_defaults_note),
            style = QuietTheme.typography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item("storage-heading") { PreferenceHeading(stringResource(R.string.account_storage)) }
        item("storage") { PreferenceRow(stringResource(R.string.modern_storage_usage),
            formatBytes(storage.sources + storage.outputs + storage.partials + storage.temporary + storage.trash),
            Icons.Rounded.Storage, { viewModel.refreshStorage(); onOpenStorage?.invoke() ?: run { panel = "storage" } },
            Modifier.testTag("settings.storage")) }
        item("cache") { PreferenceRow(stringResource(R.string.modern_clear_cache), formatBytes(storage.temporary),
            Icons.Rounded.CleaningServices, { panel = "cache" }, Modifier.testTag("settings.cache"), enabled = !busy) }
        item("about") { PreferenceRow(stringResource(R.string.modern_about), null, Icons.Rounded.Info,
            { panel = "about" }, Modifier.testTag("settings.about")) }
    }
    when (panel) {
        "theme" -> ChoiceDialog(stringResource(R.string.pref_theme), ThemeMode.entries.map { themeLabel(it) },
            settings.themeMode.ordinal, { viewModel.setThemeMode(ThemeMode.entries[it]); panel = null }, { panel = null })
        "app-color" -> AppearanceColorPicker(
            current = settings.accentColor,
            onDismiss = { panel = null },
            onSelect = { viewModel.setAccentColor(it); panel = null }
        )
        "language" -> ChoiceDialog(stringResource(R.string.pref_language), AppLanguage.entries.map { languageLabel(it) },
            settings.language.ordinal, { viewModel.setLanguage(AppLanguage.entries[it]); panel = null }, { panel = null })
        "haptics" -> ChoiceDialog(stringResource(R.string.pref_haptics), HapticIntensity.entries.map { hapticLabel(it) },
            settings.hapticIntensity.ordinal, { viewModel.setHapticIntensity(HapticIntensity.entries[it]); panel = null }, { panel = null })
        "video" -> {
            val choices = listOf("BEST", "1080", "720", "480")
            ChoiceDialog(stringResource(R.string.modern_video_quality),
                choices.map { if (it == "BEST") stringResource(R.string.modern_best_available) else "${it}p" },
                choices.indexOf(settings.defaultVideoQuality),
                { viewModel.setDefaultVideoQuality(choices[it]); panel = null }, { panel = null })
        }
        "audio" -> {
            val choices = listOf(320, 192, 128)
            ChoiceDialog(stringResource(R.string.modern_audio_quality), choices.map { stringResource(R.string.modern_audio_bitrate, it) },
                choices.indexOf(settings.defaultAudioQuality),
                { viewModel.setDefaultAudioQuality(choices[it]); panel = null }, { panel = null })
        }
        "storage" -> StorageOverviewDialog(storage, { panel = null },
            onClean = { panel = null; viewModel.cleanTemporary() }, outputFiles = outputs)
        "about" -> AlertDialog(onDismissRequest = { panel = null },
            title = { Text(stringResource(R.string.modern_about)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.modern_about_body, com.jumlo.app.BuildConfig.VERSION_NAME))
                TextButton(onClick = { panel = null; onShowGuide() }) { Text(stringResource(R.string.quick_start_replay)) }
                TextButton(onClick = { panel = null; onExportLogs() }) { Text(stringResource(R.string.pref_export_diagnostics)) }
                TextButton(onClick = { panel = "privacy" }) { Text(stringResource(R.string.account_privacy)) }
            } }, confirmButton = { TextButton(onClick = { panel = null }) { Text(stringResource(R.string.account_done)) } })
        "privacy" -> PrivacyDialog { panel = null }
        "cache" -> AlertDialog(onDismissRequest = { panel = null },
            title = { Text(stringResource(R.string.modern_clear_cache)) },
            text = { Text(stringResource(R.string.pref_clear_temporary_body)) },
            confirmButton = { TextButton(enabled = !busy, onClick = { panel = null; viewModel.cleanTemporary() }) {
                Text(stringResource(R.string.pref_clear)) } },
            dismissButton = { TextButton(onClick = { panel = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
}

@Composable private fun themeLabel(mode: ThemeMode): String = stringResource(when (mode) {
    ThemeMode.SYSTEM -> R.string.pref_theme_system
    ThemeMode.LIGHT -> R.string.pref_theme_light
    ThemeMode.DARK -> R.string.pref_theme_dark
    ThemeMode.AMOLED -> R.string.pref_theme_amoled
})

private fun previewAccentLabel(accent: String): String {
    val preset = ColorPresets.ALL.firstOrNull { it.key == accent }
    if (preset != null) return preset.nameEn
    if (M3ColorFactory.hexColor(accent) != null) return accent
    return "Blue"
}
@Composable private fun hapticLabel(intensity: HapticIntensity): String = stringResource(when (intensity) {
    HapticIntensity.OFF -> R.string.pref_haptic_off
    HapticIntensity.LIGHT -> R.string.pref_haptic_light
    HapticIntensity.MEDIUM -> R.string.pref_haptic_medium
    HapticIntensity.STRONG -> R.string.pref_haptic_strong
})
@Composable private fun languageLabel(language: AppLanguage): String = stringResource(
    if (language == AppLanguage.ARABIC) R.string.account_language_ar else R.string.account_language_en)

@Composable private fun ChoiceDialog(title: String, labels: List<String>, selected: Int,
    onSelected: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(Modifier.selectableGroup()) {
            labels.forEachIndexed { index, label ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .selectable(index == selected, role = Role.RadioButton, onClick = { onSelected(index) })
                    .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(index == selected, onClick = null)
                    Text(label, Modifier.padding(start = 12.dp))
                }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}
