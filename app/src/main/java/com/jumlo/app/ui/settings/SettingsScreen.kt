package com.jumlo.app.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.theme.QuietTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.model.FileViewMode
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.model.HapticIntensity
import com.jumlo.app.ui.util.rememberAppHaptics
import com.jumlo.app.ui.AppViewModel

@Composable fun SettingsScreen(viewModel: AppViewModel, onRequestNotifications: () -> Unit,
    onExportLogs: () -> Unit, onOpenFiles: () -> Unit, onChooseFolder: () -> Unit,
    onChooseDownloadFolder: () -> Unit, onOpenSavedFolder: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val trash by viewModel.trashed.collectAsStateWithLifecycle()
    val savedOutputs by viewModel.outputs.collectAsStateWithLifecycle()
    val tree by viewModel.tree.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    // Only this captured set is authorized. Later trash arrivals are excluded.
    var purgeIds by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(settings.notificationsGranted) { notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled() }
    LaunchedEffect(Unit) { viewModel.refreshStorage() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings.screen"),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = QuietTheme.spacing.listBottomBuffer),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item("settings-hero") {
            Column(Modifier.padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.settings_title),style=QuietTheme.typography.screenTitle,fontWeight=FontWeight.Bold)
                Text(stringResource(R.string.brand_settings_shortcuts),style=QuietTheme.typography.metadataStrong,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Surface(onClick={viewModel.refreshStorage();panel="storage"},modifier=Modifier.weight(1f).pressFeedback().height(108.dp),
                        shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.SpaceBetween) {
                            Icon(Icons.Rounded.Storage,null,tint=MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(stringResource(R.string.brand_settings_storage),style=QuietTheme.typography.bodyMedium.copy(fontWeight=FontWeight.SemiBold),color=MaterialTheme.colorScheme.onPrimaryContainer,maxLines=2)
                        }
                    }
                    Surface(onClick={panel="haptic-intensity"},modifier=Modifier.weight(1f).pressFeedback().height(108.dp),
                        shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.SpaceBetween) {
                            Icon(Icons.Rounded.Vibration,null,tint=MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.brand_haptics_level),style=QuietTheme.typography.bodyMedium.copy(fontWeight=FontWeight.SemiBold),maxLines=2)
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
        item("scale") { PreferenceRow(stringResource(R.string.redesign_font_scale),when(settings.fontScale) {
                "SMALL"->stringResource(R.string.redesign_scale_small)
                "LARGE"->stringResource(R.string.redesign_scale_large)
                else->stringResource(R.string.redesign_scale_normal)
            },Icons.Rounded.FormatSize,{panel="scale"}) }
        item("motion") { TogglePreference(stringResource(R.string.pref_reduce_motion), stringResource(R.string.pref_reduce_motion_summary),
            settings.respectReducedMotion, viewModel::setRespectReducedMotion, "settings.motion") }
        item("haptics") { TogglePreference(stringResource(R.string.pref_haptics), null, settings.hapticFeedbackOnCommit,
            viewModel::setHapticFeedback, "settings.haptics") }
        item("haptic-level") { PreferenceRow(stringResource(R.string.brand_haptics_level),
            stringResource(when(settings.hapticIntensity) {
                HapticIntensity.OFF->R.string.brand_haptics_off
                HapticIntensity.LIGHT->R.string.brand_haptics_light
                HapticIntensity.MEDIUM->R.string.brand_haptics_medium
                HapticIntensity.STRONG->R.string.brand_haptics_strong
            }),Icons.Rounded.Vibration,{panel="haptic-intensity"},Modifier.testTag("settings.hapticIntensity")) }
        item("sounds") { TogglePreference(stringResource(R.string.brand_settings_sound),null,
            settings.soundsEnabled,viewModel::setSoundsEnabled,"settings.sounds") }
        item("downloads") { PreferenceHeading(stringResource(R.string.pref_downloads)) }
        item("concurrent") { PreferenceRow(stringResource(R.string.redesign_concurrent),settings.maxConcurrentDownloads.toString(),Icons.Rounded.Download,{panel="concurrent"}) }
        item("location") { PreferenceRow(stringResource(R.string.pref_save_location),
            if(settings.downloadDestinationFolder.startsWith("content://")) stringResource(R.string.redesign_custom_location) else stringResource(R.string.redesign_storage_path),
            Icons.Rounded.FolderOpen, onChooseDownloadFolder, Modifier.testTag("settings.downloadLocation")) }
        item("openSaved") { PreferenceRow(stringResource(R.string.redesign_open_folder),stringResource(R.string.redesign_storage_folder),Icons.Rounded.FolderOpen,onOpenSavedFolder) }
        item("notifications") { PreferenceRow(stringResource(R.string.pref_notifications),
            stringResource(if (notificationsEnabled) R.string.pref_enabled else R.string.pref_disabled), Icons.Rounded.Notifications, {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) onRequestNotifications()
                else try { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
                catch (_: Exception) { viewModel.notifyMessage("EXTERNAL_APP_UNAVAILABLE") }
            }, Modifier.testTag("settings.notifications")) }
        item("files_storage") { PreferenceHeading(stringResource(R.string.pref_files_storage)) }
        item("fileView") { PreferenceRow(stringResource(R.string.redesign_file_view),
            when(settings.fileViewMode) {FileViewMode.LIST->stringResource(R.string.redesign_view_list);FileViewMode.GRID->stringResource(R.string.redesign_view_grid);FileViewMode.GALLERY->stringResource(R.string.redesign_view_gallery)},
            Icons.Rounded.GridView,{panel="fileView"}) }
        item("storage") { PreferenceRow(stringResource(R.string.account_storage), null, Icons.Rounded.Storage,
            { viewModel.refreshStorage(); panel = "storage" }, Modifier.testTag("settings.storage")) }
        item("folder") { PreferenceRow(stringResource(R.string.pref_folder_access), tree?.name ?: stringResource(R.string.pref_folder_access_summary),
            Icons.Rounded.FolderShared, onChooseFolder, Modifier.testTag("settings.permissions")) }
        item("cache") { PreferenceRow(stringResource(R.string.pref_clear_temporary), stringResource(R.string.pref_clear_temporary_summary),
            Icons.Rounded.CleaningServices, { panel = "cache" }, Modifier.testTag("settings.cache"), enabled = !busy) }
        item("trash") { PreferenceRow(stringResource(R.string.pref_empty_trash), stringResource(R.string.pref_trash_count, trash.size),
            Icons.Rounded.DeleteOutline, { purgeIds = trash.map { it.id } }, Modifier.testTag("settings.trash"),
            enabled = trash.isNotEmpty() && !busy, destructive = true) }
        item("privacy_about") { PreferenceHeading(stringResource(R.string.pref_privacy_about)) }
        item("privacy") { PreferenceRow(stringResource(R.string.account_privacy), null, Icons.Rounded.PrivacyTip, { panel = "privacy" }) }
        item("diagnostics") { PreferenceRow(stringResource(R.string.pref_export_diagnostics), stringResource(R.string.pref_export_diagnostics_summary),
            Icons.Rounded.Description, onExportLogs, Modifier.testTag("settings.diagnostics")) }
        item("about") { PreferenceRow(stringResource(R.string.account_help_about), null, Icons.Rounded.Info, { panel = "about" }) }
        item("bottom") { Spacer(Modifier.height(24.dp)) }
    }
    val accentKeys = listOf("VIOLET","DYNAMIC","BLUE","TEAL","CORAL")
    when (panel) {
        "haptic-intensity" -> ChoiceDialog(stringResource(R.string.brand_haptics_level),
            listOf(stringResource(R.string.brand_haptics_off),stringResource(R.string.brand_haptics_light),
                stringResource(R.string.brand_haptics_medium),stringResource(R.string.brand_haptics_strong)),
            settings.hapticIntensity.ordinal,
            {viewModel.setHapticIntensity(HapticIntensity.entries[it]);panel=null},{panel=null})
        "theme" -> ChoiceDialog(stringResource(R.string.pref_theme), ThemeMode.entries.map { themeLabel(it) }, settings.themeMode.ordinal,
            { viewModel.setThemeMode(ThemeMode.entries[it]); panel = null }, { panel = null })
        "language" -> ChoiceDialog(stringResource(R.string.pref_language), AppLanguage.entries.map { languageLabel(it) }, settings.language.ordinal,
            { viewModel.setLanguage(AppLanguage.entries[it]); panel = null }, { panel = null })
        "concurrent" -> ChoiceDialog(stringResource(R.string.redesign_concurrent),(1..4).map(Int::toString),settings.maxConcurrentDownloads.coerceIn(1,4)-1,
            {viewModel.setMaxConcurrentDownloads(it+1);panel=null},{panel=null})
        "fileView" -> ChoiceDialog(stringResource(R.string.redesign_file_view),listOf(stringResource(R.string.redesign_view_list),stringResource(R.string.redesign_view_grid),stringResource(R.string.redesign_view_gallery)),settings.fileViewMode.ordinal,
            {viewModel.setFileViewMode(FileViewMode.entries[it]);panel=null},{panel=null})
        "accent" -> ChoiceDialog(stringResource(R.string.redesign_accent),
            listOf(stringResource(R.string.redesign_accent_violet),stringResource(R.string.redesign_accent_dynamic),stringResource(R.string.redesign_accent_blue),stringResource(R.string.redesign_accent_teal),stringResource(R.string.redesign_accent_coral)),
            accentKeys.indexOf(settings.accentColor).coerceAtLeast(0),{viewModel.setAccentColor(accentKeys[it]);panel=null},{panel=null})
        "scale" -> ChoiceDialog(stringResource(R.string.redesign_font_scale),listOf(stringResource(R.string.redesign_scale_small),stringResource(R.string.redesign_scale_normal),stringResource(R.string.redesign_scale_large)),
            listOf("SMALL","NORMAL","LARGE").indexOf(settings.fontScale).coerceAtLeast(0),{viewModel.setFontScale(listOf("SMALL","NORMAL","LARGE")[it]);panel=null},{panel=null})
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

@Composable private fun themeLabel(mode: ThemeMode): String = stringResource(when(mode) {
    ThemeMode.SYSTEM -> R.string.pref_theme_system; ThemeMode.LIGHT -> R.string.pref_theme_light; ThemeMode.DARK -> R.string.pref_theme_dark
})
@Composable private fun languageLabel(language: AppLanguage): String = stringResource(
    if(language == AppLanguage.ARABIC) R.string.account_language_ar else R.string.account_language_en)

@Composable private fun TogglePreference(title: String, summary: String?, checked: Boolean, onChanged: (Boolean) -> Unit, tag: String) {
    val haptics=rememberAppHaptics()
    Row(Modifier.pressFeedback().fillMaxWidth().heightIn(min = 64.dp).testTag(tag).toggleable(checked, role = Role.Switch,
        onValueChange = {haptics.selectionToggle();onChanged(it)})
        .padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = QuietTheme.typography.body)
            summary?.let { Text(it, Modifier.padding(top = 3.dp), style = QuietTheme.typography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(16.dp)); Switch(checked, onCheckedChange = null)
    }
}

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
