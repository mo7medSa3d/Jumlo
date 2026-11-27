package com.jumlo.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ChevronRight
import com.jumlo.app.ui.icons.rounded.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.formatBytes
import com.jumlo.app.ui.theme.ColorSystems
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.settingsIconStyleFor

private val SIMPLE_THEMES = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)

private fun areNotificationsGranted(context: Context): Boolean {
    val managerEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val postPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    return managerEnabled && postPermissionGranted
}

/** Preferences that change an everyday action. Platform and scheduler policy stay out of this page. */
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onRequestNotifications: () -> Unit,
    onExportLogs: () -> Unit,
    onChooseDownloadFolder: () -> Unit,
    onOpenSavedFolder: () -> Unit,
    onShowGuide: () -> Unit = {},
    onOpenStorage: (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    resetToRootEpoch: Int = 0
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val outputs by viewModel.outputs.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val proEntitlement by viewModel.proEntitlement.collectAsStateWithLifecycle()
    val notificationEpoch by viewModel.notificationPermissionEpoch.collectAsStateWithLifecycle()
    var proSheetOpen by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsOn by remember { mutableStateOf(areNotificationsGranted(context)) }
    LaunchedEffect(notificationEpoch) {
        notificationsOn = areNotificationsGranted(context)
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsOn = areNotificationsGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hostActivity = remember(context) {
        generateSequence<android.content.Context>(context) { c -> (c as? android.content.ContextWrapper)?.baseContext?.takeUnless { it === c } }
            .filterIsInstance<android.app.Activity>().firstOrNull()
    }
    // Row icons use fixed per-row colors, never the user accent.
    val dark = isDarkTheme(settings.themeMode)
    val totalStorageBytes = remember(storage) {
        storage.outputs + storage.partials + storage.temporary + storage.trash
    }
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(resetToRootEpoch) {
        if (resetToRootEpoch > 0) {
            panel = null
            listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(Unit) { viewModel.refreshStorage() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().align(Alignment.TopCenter).testTag("settings.screen"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = QuietTheme.spacing.listBottomBuffer + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item("pro-banner", contentType = "pro") {
                val colors = MaterialTheme.colorScheme
                val goldBorder = Color(0xFFD4A017)
                val goldDeep = Color(0xFFB47B00)
                val goldLight = Color(0xFFFFE58F)
                val proBg = if (dark) {
                    Brush.horizontalGradient(listOf(Color(0xFF211A0B), Color(0xFF2C220B)))
                } else {
                    Brush.horizontalGradient(listOf(Color(0xFFFFFBF0), Color(0xFFFFF3CF)))
                }
                Surface(
                    onClick = { proSheetOpen = true },
                    shape = QuietTheme.shapes.heroCard,
                    color = Color.Transparent,
                    border = BorderStroke(1.2.dp, goldBorder.copy(alpha = if (dark) 0.55f else 0.45f)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth().clip(QuietTheme.shapes.heroCard)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(proBg)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = QuietTheme.shapes.iconTile,
                            color = goldBorder.copy(alpha = if (dark) 0.22f else 0.16f),
                            border = BorderStroke(1.dp, goldBorder.copy(alpha = 0.35f)),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(R.drawable.ic_pro_crown),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    stringResource(if (proEntitlement.isPro) R.string.pro_active_badge else R.string.pro_title),
                                    style = QuietTheme.typography.sectionTitle.copy(fontWeight = FontWeight.Bold),
                                    color = if (dark) Color(0xFFFFF8E1) else Color(0xFF2A1E00)
                                )
                                Surface(
                                    shape = QuietTheme.shapes.pill,
                                    color = goldBorder
                                ) {
                                    Text(
                                        text = stringResource(R.string.pro_badge_short),
                                        style = QuietTheme.typography.badge.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF1A1200),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                stringResource(R.string.pro_upgrade_subtitle),
                                style = QuietTheme.typography.metadata,
                                color = if (dark) goldLight.copy(alpha = 0.8f) else goldDeep
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = if (dark) goldLight else goldDeep,
                            modifier = Modifier.size(20.dp)
                        )
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
            item("alerts-heading", contentType = "heading") { PreferenceHeading(stringResource(R.string.pref_alerts_feedback)) }
            item("alerts-group", contentType = "group") {
                PreferenceGroup {
                    PreferenceRow(
                        stringResource(R.string.pref_notifications),
                        stringResource(if (notificationsOn) R.string.pref_enabled else R.string.pref_disabled),
                        Icons.Rounded.Notifications,
                        onRequestNotifications,
                        Modifier.testTag("settings.notifications"),
                        iconStyle = settingsIconStyleFor("notifications", dark),
                        grouped = true
                    )
                    PreferenceGroupDivider()
                    PreferenceToggleRow(
                        title = stringResource(R.string.settings_ui_sounds),
                        checked = settings.soundsEnabled,
                        onCheckedChange = viewModel::setSoundsEnabled,
                        modifier = Modifier.testTag("settings.sounds"),
                        icon = Icons.Rounded.Audiotrack,
                        iconStyle = settingsIconStyleFor("sounds", dark),
                        grouped = true
                    )
                    PreferenceGroupDivider()
                    PreferenceToggleRow(
                        title = stringResource(R.string.pref_haptics),
                        checked = settings.hapticFeedbackOnCommit,
                        onCheckedChange = viewModel::setHapticFeedback,
                        modifier = Modifier.testTag("settings.haptics"),
                        icon = Icons.Rounded.Bolt,
                        iconStyle = settingsIconStyleFor("haptics", dark),
                        grouped = true
                    )
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
        "about" -> JumloAboutDialog(
            onReplayGuide = { panel = null; onShowGuide() },
            onOpenPrivacy = { panel = "privacy" },
            onDismiss = { panel = null }
        )
        "privacy" -> PrivacyDialog { panel = null }
        "cache" -> AlertDialog(onDismissRequest = { panel = null },
            title = { Text(stringResource(R.string.modern_clear_cache), modifier = Modifier.semantics { heading() }) },
            text = { Text(stringResource(R.string.pref_clear_temporary_body)) },
            confirmButton = { TextButton(enabled = !busy, onClick = { panel = null; viewModel.cleanTemporary() }) {
                Text(stringResource(R.string.pref_clear)) } },
            dismissButton = { TextButton(onClick = { panel = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
    LaunchedEffect(proSheetOpen) { if (proSheetOpen) viewModel.connectBilling() }
    if (proSheetOpen) {
        com.jumlo.app.ui.pro.JumloProSheet(
            entitlement = proEntitlement,
            onPurchase = { productId -> hostActivity?.let { viewModel.launchProPurchase(it, productId) } },
            onRestore = { viewModel.restoreProPurchases() },
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
