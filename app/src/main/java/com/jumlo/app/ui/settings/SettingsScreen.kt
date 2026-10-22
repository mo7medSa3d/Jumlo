package com.jumlo.app.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.FileViewMode
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.NeonPanel
import com.jumlo.app.ui.icons.*
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics

internal fun formatReadableSaveLocation(rawLocation: String, defaultLabel: String): String {
    if (!rawLocation.startsWith("content://")) return defaultLabel
    val decoded = runCatching { Uri.decode(rawLocation) }.getOrDefault(rawLocation)
    val readable = decoded.substringAfterLast(':').trim('/').ifBlank {
        decoded.substringAfterLast('/').trim()
    }
    return if (readable.isNotBlank()) readable else defaultLabel
}

@Composable fun SettingsScreen(viewModel: AppViewModel, onRequestNotifications: () -> Unit,
    onExportLogs: () -> Unit, onOpenFiles: () -> Unit, onChooseFolder: () -> Unit,
    onChooseDownloadFolder: () -> Unit, onOpenSavedFolder: () -> Unit,
    onShowGuide: () -> Unit = {}) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val trash by viewModel.trashed.collectAsStateWithLifecycle()
    val savedOutputs by viewModel.outputs.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    var purgeIds by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val profileLabel = stringResource(R.string.profile_open)
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                viewModel.refreshStorage()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(settings.notificationsGranted) { notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled() }
    LaunchedEffect(savedOutputs.size, trash.size, settings.downloadDestinationFolder) { viewModel.refreshStorage() }
    val totalStorageBytes = storage.outputs + storage.sources + storage.temporary + storage.partials + storage.trash
    val readableSaveLocation = formatReadableSaveLocation(
        settings.downloadDestinationFolder,
        stringResource(R.string.redesign_storage_path)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings.screen"),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = QuietTheme.spacing.listBottomBuffer),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (busy) item("settings-busy") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item("settings-hero") {
            NeonPanel(accent=MaterialTheme.colorScheme.primary,
                modifier=Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Surface(
                        onClick = { panel="profile" },
                        shape=androidx.compose.foundation.shape.CircleShape,
                        color=MaterialTheme.colorScheme.primaryContainer,
                        modifier=Modifier.size(62.dp).semantics {
                            contentDescription=profileLabel
                            role=Role.Button
                        }) {
                        Box(contentAlignment=Alignment.Center) {
                            Icon(Icons.Rounded.Person,null,Modifier.size(30.dp),
                                tint=MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.neon_my_space),
                            style=QuietTheme.typography.screenTitle)
                        Text(stringResource(R.string.neon_my_space_subtitle),
                            style=QuietTheme.typography.metadata,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick={panel="profile"},modifier=Modifier.padding(start=0.dp)) {
                            Text(stringResource(R.string.profile_open))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Surface(onClick={viewModel.refreshStorage();panel="storage"},
                        modifier=Modifier.weight(1f).pressFeedback().heightIn(min=112.dp),
                        shape=RoundedCornerShape(20.dp),
                        color=MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.fillMaxSize().padding(14.dp),
                            verticalArrangement=Arrangement.SpaceBetween) {
                            Icon(Icons.Rounded.Storage,null,Modifier.size(26.dp),
                                tint=MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.neon_storage_shortcut),
                                style=QuietTheme.typography.bodyMedium.copy(fontWeight=FontWeight.SemiBold))
                            Text(android.text.format.Formatter.formatFileSize(context,totalStorageBytes),
                                style=QuietTheme.typography.metadata)
                        }
                    }
                    Surface(onClick=onOpenFiles,
                        modifier=Modifier.weight(1f).pressFeedback().heightIn(min=112.dp),
                        shape=RoundedCornerShape(20.dp),
                        color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.fillMaxSize().padding(14.dp),
                            verticalArrangement=Arrangement.SpaceBetween) {
                            Icon(Icons.Rounded.FolderOpen,null,Modifier.size(26.dp),
                                tint=MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.neon_my_documents),
                                style=QuietTheme.typography.bodyMedium.copy(fontWeight=FontWeight.SemiBold),
                                maxLines=3)
                            Text(stringResource(R.string.account_result_count,savedOutputs.size),
                                style=QuietTheme.typography.metadata)
                        }
                    }
                }
            }
        }
        item("appearance") { PreferenceHeading(stringResource(R.string.pref_appearance)) }
        item("theme") { PreferenceRow(stringResource(R.string.pref_theme), themeLabel(settings.themeMode), Icons.Rounded.Palette,
            { panel = "theme" }, Modifier.testTag("settings.theme")) }
        item("language") { PreferenceRow(stringResource(R.string.pref_language), languageLabel(settings.language), Icons.Rounded.Language,
            { panel = "language" }, Modifier.testTag("settings.language")) }
        item("accent") { PreferenceRow(stringResource(R.string.redesign_accent),when(settings.accentColor) {
                "DYNAMIC"->stringResource(R.string.redesign_accent_dynamic)
                "BLUE"->stringResource(R.string.redesign_accent_blue)
                "TEAL"->stringResource(R.string.redesign_accent_teal)
                "CORAL"->stringResource(R.string.redesign_accent_coral)
                else->stringResource(R.string.redesign_accent_violet)
            },Icons.Rounded.ColorLens,{panel="accent"}) }

        item("downloads") { PreferenceHeading(stringResource(R.string.pref_downloads)) }
        item("defaultVideoQuality") {
            PreferenceRow(
                stringResource(R.string.pref_default_video_quality),
                videoQualityLabel(settings.defaultVideoQuality),
                Icons.Rounded.Videocam,
                { panel = "videoQuality" },
                Modifier.testTag("settings.defaultVideoQuality")
            )
        }
        item("defaultAudioQuality") {
            PreferenceRow(
                stringResource(R.string.pref_default_audio_quality),
                audioQualityLabel(settings.defaultAudioQuality),
                Icons.Rounded.Audiotrack,
                { panel = "audioQuality" },
                Modifier.testTag("settings.defaultAudioQuality")
            )
        }
        item("location") {
            Column {
                PreferenceRow(
                    stringResource(R.string.pref_save_location),
                    readableSaveLocation,
                    Icons.Rounded.FolderOpen,
                    onChooseDownloadFolder,
                    Modifier.testTag("settings.downloadLocation")
                )
                if(settings.downloadDestinationFolder.startsWith("content://")) {
                    TextButton(onClick = viewModel::resetDownloadDestinationFolder, modifier = Modifier.testTag("settings.resetDownloadLocation")) {
                        Text(stringResource(R.string.action_reset) + " (" + stringResource(R.string.redesign_storage_path) + ")")
                    }
                }
            }
        }
        item("openSaved") { PreferenceRow(stringResource(R.string.redesign_open_folder),readableSaveLocation,Icons.Rounded.FolderOpen,onOpenSavedFolder) }

        item("files_storage") { PreferenceHeading(stringResource(R.string.pref_files_storage)) }
        item("fileView") { PreferenceRow(stringResource(R.string.redesign_file_view),
            when(settings.fileViewMode) {FileViewMode.LIST->stringResource(R.string.redesign_view_list);else->stringResource(R.string.redesign_view_grid)},
            Icons.Rounded.GridView,{panel="fileView"}) }
        item("storage") { PreferenceRow(stringResource(R.string.account_storage),
            android.text.format.Formatter.formatFileSize(context, totalStorageBytes),
            Icons.Rounded.Storage,
            { viewModel.refreshStorage(); panel = "storage" }, Modifier.testTag("settings.storage")) }
        item("cache") { PreferenceRow(stringResource(R.string.pref_clear_temporary),
            android.text.format.Formatter.formatFileSize(context, storage.temporary) + " · " + stringResource(R.string.pref_clear_temporary_summary),
            Icons.Rounded.CleaningServices, { panel = "cache" }, Modifier.testTag("settings.cache"), enabled = !busy) }

        item("privacy_about") { PreferenceHeading(stringResource(R.string.pref_privacy_about)) }
        item("about") { PreferenceRow(stringResource(R.string.account_help_about), null, Icons.Rounded.Info, { panel = "about" }) }
        item("bottom") { Spacer(Modifier.height(24.dp)) }
    }
    when (panel) {
        "profile" -> AlertDialog(
            onDismissRequest = { panel = null },
            title = { Text(stringResource(R.string.profile_local_title)) },
            text = {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.profile_local_explanation))
                    Text(stringResource(R.string.profile_documents_count,savedOutputs.size),
                        style=QuietTheme.typography.metadataStrong)
                    Text(stringResource(R.string.profile_storage_used,
                        android.text.format.Formatter.formatFileSize(context,storage.outputs)),
                        style=QuietTheme.typography.metadataStrong)
                }
            },
            confirmButton = {
                TextButton(onClick={panel=null;onOpenFiles()}) {
                    Text(stringResource(R.string.profile_view_documents))
                }
            },
            dismissButton = {
                TextButton(onClick={panel=null}) {
                    Text(stringResource(R.string.account_cancel))
                }
            }
        )
        "theme" -> ChoiceDialog(stringResource(R.string.pref_theme), ThemeMode.entries.map { themeLabel(it) }, settings.themeMode.ordinal,
            { viewModel.setThemeMode(ThemeMode.entries[it]); panel = null }, { panel = null })
        "language" -> ChoiceDialog(stringResource(R.string.pref_language), AppLanguage.entries.map { languageLabel(it) }, settings.language.ordinal,
            { viewModel.setLanguage(AppLanguage.entries[it]); panel = null }, { panel = null })
        "videoQuality" -> {
            val options = listOf("BEST", "1080p", "720p", "480p")
            val labels = options.map { videoQualityLabel(it) }
            ChoiceDialog(
                stringResource(R.string.pref_default_video_quality),
                labels,
                options.indexOf(settings.defaultVideoQuality).coerceAtLeast(0),
                { viewModel.setDefaultVideoQuality(options[it]); panel = null },
                { panel = null }
            )
        }
        "audioQuality" -> {
            val options = listOf("MP3_320", "MP3_192", "M4A", "ORIGINAL")
            val labels = options.map { audioQualityLabel(it) }
            ChoiceDialog(
                stringResource(R.string.pref_default_audio_quality),
                labels,
                options.indexOf(settings.defaultAudioQuality).coerceAtLeast(0),
                { viewModel.setDefaultAudioQuality(options[it]); panel = null },
                { panel = null }
            )
        }
        "fileView" -> {
            val modes = listOf(FileViewMode.LIST, FileViewMode.GRID)
            ChoiceDialog(
                stringResource(R.string.redesign_file_view),
                listOf(stringResource(R.string.redesign_view_list), stringResource(R.string.redesign_view_grid)),
                modes.indexOf(settings.fileViewMode).coerceAtLeast(0),
                { viewModel.setFileViewMode(modes[it]); panel = null },
                { panel = null }
            )
        }
        "accent" -> AccentPickerDialog(settings.accentColor,
            onSelect = { viewModel.setAccentColor(it); panel = null },
            onDismiss = { panel = null })
        "storage" -> StorageOverviewDialog(storage,{panel = null},onClean={panel=null;viewModel.cleanTemporary()},outputFiles=savedOutputs)
        "privacy" -> PrivacyDialog { panel = null }
        "about" -> AboutDialog { panel = null }
        "cache" -> AlertDialog(onDismissRequest = { panel = null }, title = { Text(stringResource(R.string.pref_clear_temporary)) },
            text = { Text(stringResource(R.string.pref_clear_temporary_body)) },
            confirmButton = { TextButton(onClick = { panel = null; viewModel.cleanTemporary() }, enabled = !busy) { Text(stringResource(R.string.pref_clear)) } },
            dismissButton = { TextButton({ panel = null }) { Text(stringResource(R.string.account_cancel)) } })
    }
    purgeIds?.let { ids ->
        AlertDialog(onDismissRequest = { purgeIds = null }, title = { Text(stringResource(R.string.pref_purge_title, ids.size)) },
            text = { Text(stringResource(R.string.pref_purge_body)) },
            confirmButton = { TextButton(onClick = { purgeIds = null; viewModel.purgeTrashIds(ids.toSet()) }, enabled = !busy,
                modifier = Modifier.testTag("settings.confirmPurge")) {
                Text(stringResource(R.string.pref_delete_forever), color = MaterialTheme.colorScheme.error)
            } }, dismissButton = { TextButton({ purgeIds = null }, Modifier.testTag("settings.cancelPurge")) { Text(stringResource(R.string.account_cancel)) } })
    }
}

@Composable private fun videoQualityLabel(code: String): String = when (code) {
    "BEST" -> stringResource(R.string.quality_video_best)
    "1080p" -> "1080p Full HD"
    "720p" -> "720p HD"
    "480p" -> "480p SD"
    else -> "1080p Full HD"
}

@Composable private fun audioQualityLabel(code: String): String = when (code) {
    "MP3_320" -> "MP3 · 320 kbps"
    "MP3_192" -> "MP3 · 192 kbps"
    "M4A" -> "M4A · 192 kbps"
    else -> stringResource(R.string.media_original_audio)
}

@Composable private fun themeLabel(mode: ThemeMode): String = stringResource(when(mode) {
    ThemeMode.SYSTEM -> R.string.pref_theme_system; ThemeMode.LIGHT -> R.string.pref_theme_light; ThemeMode.DARK -> R.string.pref_theme_dark
})
@Composable private fun languageLabel(language: AppLanguage): String = stringResource(
    if(language == AppLanguage.ARABIC) R.string.account_language_ar else R.string.account_language_en)

@Composable private fun ChoiceDialog(title: String, labels: List<String>, selected: Int, onSelected: (Int) -> Unit, onDismiss: () -> Unit) {
    val haptics=rememberAppHaptics()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(Modifier.selectableGroup()) {
            labels.forEachIndexed { index, label ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(index == selected, role = Role.RadioButton,
                    onClick = { haptics.selectionToggle();onSelected(index) }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(index == selected, onClick = null); Text(label, Modifier.padding(start = 12.dp))
                }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.account_cancel)) } })
}
