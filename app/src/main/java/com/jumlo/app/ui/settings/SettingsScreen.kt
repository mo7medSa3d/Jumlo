package com.jumlo.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.SaveFolderCard
import com.jumlo.app.ui.components.formatBytes
import com.jumlo.app.ui.theme.ColorSystems
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.settingsIconStyleFor

private val SIMPLE_THEMES = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)

/** Preferences that change an everyday action. Platform and scheduler policy stay out of this page. */
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onRequestNotifications: () -> Unit,
    onExportLogs: () -> Unit,
    onChooseDownloadFolder: () -> Unit,
    onOpenSavedFolder: () -> Unit,
    onShowGuide: () -> Unit = {},
    onOpenStorage: (() -> Unit)? = null
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val outputs by viewModel.outputs.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val proEntitlement by viewModel.proEntitlement.collectAsStateWithLifecycle()
    var proSheetOpen by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val hostActivity = remember(context) {
        generateSequence<android.content.Context>(context) { c -> (c as? android.content.ContextWrapper)?.baseContext?.takeUnless { it === c } }
            .filterIsInstance<android.app.Activity>().firstOrNull()
    }
    // Row icons use fixed per-row colors, never the user accent.
    val dark = isDarkTheme(settings.themeMode)
    val totalStorageBytes = remember(storage) {
        storage.sources + storage.outputs + storage.partials + storage.temporary + storage.trash
    }
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.refreshStorage() }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxSize().align(Alignment.TopCenter).testTag("settings.screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = QuietTheme.spacing.listBottomBuffer),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item("pro-banner", contentType = "pro") {
            Surface(
                onClick = { proSheetOpen = true },
                shape = QuietTheme.shapes.card,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(if (proEntitlement.isPro) R.string.pro_active_badge else R.string.pro_title),
                            style = QuietTheme.typography.sectionTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(R.string.pro_upgrade_subtitle),
                            style = QuietTheme.typography.metadata,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item("appearance-heading", contentType = "heading") { PreferenceHeading(stringResource(R.string.pref_appearance)) }
        item("appearance-group", contentType = "group") {
            PreferenceGroup {
                PreferenceRow(
                    stringResource(R.string.pref_theme),
                    themeLabel(if (settings.themeMode == ThemeMode.AMOLED) ThemeMode.DARK else settings.themeMode),
                    Icons.Rounded.DarkMode,
                    { panel = "theme" },
                    Modifier.testTag("settings.theme"),
                    iconStyle = settingsIconStyleFor("theme", dark),
                    grouped = true
                )
                PreferenceGroupDivider()
                PreferenceRow(
                    stringResource(R.string.pref_language),
                    languageLabel(settings.language),
                    Icons.Rounded.Language,
                    { panel = "language" },
                    Modifier.testTag("settings.language"),
                    iconStyle = settingsIconStyleFor("language", dark),
                    grouped = true
                )
            }
        }
        item("save-heading", contentType = "heading") { PreferenceHeading(stringResource(R.string.pref_save_location)) }
        item("save-folder", contentType = "save-folder") {
            SaveFolderCard(settings.downloadDestinationFolder, onChooseDownloadFolder, onOpenSavedFolder)
            if (settings.downloadDestinationFolder.startsWith("content://")) TextButton(
                onClick = viewModel::resetDownloadDestinationFolder,
                modifier = Modifier.testTag("settings.resetDownloadLocation")) {
                Text(stringResource(R.string.modern_reset_save_folder))
            }
        }
        item("storage-heading", contentType = "heading") { PreferenceHeading(stringResource(R.string.account_storage)) }
        item("storage-group", contentType = "group") {
            PreferenceGroup {
                PreferenceRow(
                    stringResource(R.string.modern_storage_usage),
                    formatBytes(totalStorageBytes),
                    Icons.Rounded.Storage,
                    { viewModel.refreshStorage(); onOpenStorage?.invoke() ?: run { panel = "storage" } },
                    Modifier.testTag("settings.storage"),
                    iconStyle = settingsIconStyleFor("storage", dark),
                    grouped = true
                )
                PreferenceGroupDivider()
                PreferenceRow(
                    stringResource(R.string.modern_clear_cache),
                    formatBytes(storage.temporary),
                    Icons.Rounded.CleaningServices,
                    { panel = "cache" },
                    Modifier.testTag("settings.cache"),
                    enabled = !busy,
                    iconStyle = settingsIconStyleFor("cache", dark),
                    grouped = true
                )
            }
        }
        item("about-spacer", contentType = "spacer") { Spacer(Modifier.height(10.dp)) }
        item("about", contentType = "row") {
            PreferenceRow(
                stringResource(R.string.modern_about),
                stringResource(R.string.settings_about_summary),
                Icons.Rounded.Info,
                { panel = "about" },
                Modifier.testTag("settings.about"),
                iconStyle = settingsIconStyleFor("about", dark)
            )
        }
    }
    }
    when (panel) {
        "theme" -> {
            val effectiveTheme = if (settings.themeMode == ThemeMode.AMOLED) ThemeMode.DARK else settings.themeMode
            val currentIdx = SIMPLE_THEMES.indexOf(effectiveTheme).coerceAtLeast(0)
            ChoiceDialog(
                stringResource(R.string.pref_theme),
                SIMPLE_THEMES.map { themeLabel(it) },
                currentIdx,
                { viewModel.setThemeMode(SIMPLE_THEMES[it]); panel = null },
                { panel = null }
            )
        }
        "language" -> ChoiceDialog(stringResource(R.string.pref_language), AppLanguage.entries.map { languageLabel(it) },
            settings.language.ordinal, { viewModel.setLanguage(AppLanguage.entries[it]); panel = null }, { panel = null })
        "storage" -> StorageOverviewDialog(storage, { panel = null },
            onClean = { panel = null; viewModel.cleanTemporary() }, outputFiles = outputs)
        "about" -> AlertDialog(onDismissRequest = { panel = null },
            title = { Text(stringResource(R.string.modern_about), modifier = Modifier.semantics { heading() }) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.modern_about_body, com.jumlo.app.BuildConfig.VERSION_NAME))
                TextButton(onClick = { panel = null; onShowGuide() }) { Text(stringResource(R.string.quick_start_replay)) }
                TextButton(onClick = { panel = null; onExportLogs() }) { Text(stringResource(R.string.pref_export_diagnostics)) }
                TextButton(onClick = { panel = "privacy" }) { Text(stringResource(R.string.account_privacy)) }
            } }, confirmButton = { TextButton(onClick = { panel = null }) { Text(stringResource(R.string.account_done)) } })
        "privacy" -> PrivacyDialog { panel = null }
        "cache" -> AlertDialog(onDismissRequest = { panel = null },
            title = { Text(stringResource(R.string.modern_clear_cache), modifier = Modifier.semantics { heading() }) },
            text = { Text(stringResource(R.string.pref_clear_temporary_body)) },
            confirmButton = { TextButton(enabled = !busy, onClick = { panel = null; viewModel.cleanTemporary() }) {
                Text(stringResource(R.string.pref_clear)) } },
            dismissButton = { TextButton(onClick = { panel = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
    if (proSheetOpen) {
        com.jumlo.app.ui.pro.JumloProSheet(
            entitlement = proEntitlement,
            onPurchase = { productId -> hostActivity?.let { viewModel.purchasePro(it, productId) } },
            onRestore = { viewModel.restorePurchases() },
            onDismiss = { proSheetOpen = false }
        )
    }
}


@Composable private fun themeLabel(mode: ThemeMode): String = stringResource(when (mode) {
    ThemeMode.SYSTEM -> R.string.pref_theme_system
    ThemeMode.LIGHT -> R.string.pref_theme_light
    ThemeMode.DARK -> R.string.pref_theme_dark
    ThemeMode.AMOLED -> R.string.pref_theme_amoled
})

/** Color system display labels follow the selected in-app language, not device locale. */
internal fun colorSystemLabel(system: String, language: AppLanguage): String {
    val found = ColorSystems.systemFor(system)
    return if (language.isRtl) found.nameAr else found.nameEn
}

@Composable private fun languageLabel(language: AppLanguage): String = stringResource(when (language) {
    AppLanguage.SYSTEM -> R.string.account_language_system
    AppLanguage.ARABIC -> R.string.account_language_ar
    AppLanguage.ENGLISH -> R.string.account_language_en
})

@Composable private fun ChoiceDialog(title: String, labels: List<String>, selected: Int,
    onSelected: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title, modifier = Modifier.semantics { heading() }) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
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
