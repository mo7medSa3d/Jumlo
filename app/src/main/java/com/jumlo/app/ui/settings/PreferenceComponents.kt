package com.jumlo.app.ui.settings

import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumlo.app.BuildConfig
import com.jumlo.app.R
import com.jumlo.app.ui.StorageUsage
import com.jumlo.app.model.OutputRecord
import com.jumlo.app.core.StoragePaths
import com.jumlo.app.ui.components.QuietIconTile

@Composable internal fun PreferenceHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 18.dp, bottom = 6.dp)
            .semantics { heading() }
    )
}

/** iOS Inset Grouped container: groups related preference rows into one clean borderless surface. */
@Composable internal fun PreferenceGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val pillars = QuietTheme.pillars
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = QuietTheme.shapes.card,
        color = pillars.cardSurface,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable internal fun PreferenceGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 62.dp, end = 14.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    )
}

/** Dark-mode resolution shared by settings surfaces: AMOLED counts as dark. */
@Composable internal fun isDarkTheme(themeMode: com.jumlo.app.model.ThemeMode): Boolean =
    when (themeMode) {
        com.jumlo.app.model.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        com.jumlo.app.model.ThemeMode.LIGHT -> false
        else -> true
    }

/**
 * A settings/account row. Its icon never follows the user accent: callers pass
 * a fixed [iconStyle] from [com.jumlo.app.ui.theme.settingsIconStyleFor].
 * Destructive rows keep the error treatment regardless of the style.
 */
@Composable fun PreferenceRow(title: String, summary: String?, icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false,
    iconStyle: com.jumlo.app.ui.theme.SettingsIconStyle, grouped: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    val titleColor = if (destructive) colors.error else colors.onSurface
    val haptics = rememberAppHaptics()
    val shape = QuietTheme.shapes.card
    val rowContent: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 12.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuietIconTile(
                icon,
                tint = if (destructive) colors.error else iconStyle.tint,
                container = if (destructive) colors.errorContainer else iconStyle.container,
                size = 34.dp
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = titleColor)
                summary?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Rounded.ChevronRight, null,
                Modifier.size(18.dp), tint = colors.onSurfaceVariant.copy(alpha = 0.55f)
            )
        }
    }
    if (grouped) {
        Box(
            modifier = modifier.fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Button, onClick = {
                    if (destructive) haptics.secondaryAction() else haptics.actionClick()
                    onClick()
                })
        ) { rowContent() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth().pressFeedback()
                .clip(shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = {
                    if (destructive) haptics.secondaryAction() else haptics.actionClick()
                    onClick()
                }),
            shape = shape,
            color = if (destructive) colors.errorContainer.copy(alpha = 0.24f) else pillars.cardSurface,
            shadowElevation = 0.dp
        ) { rowContent() }
    }
}

/** Accessible, single-target preference switch with EN/AR layout semantics. */
@Composable internal fun PreferenceToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconStyle: com.jumlo.app.ui.theme.SettingsIconStyle? = null,
    grouped: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    val haptics = rememberAppHaptics()
    val shape = QuietTheme.shapes.card
    val onToggle: (Boolean) -> Unit = { next ->
        haptics.selectionToggle()
        onCheckedChange(next)
    }
    val rowContent: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 12.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null && iconStyle != null) {
                QuietIconTile(
                    icon,
                    tint = iconStyle.tint,
                    container = iconStyle.container,
                    size = 34.dp
                )
                Spacer(Modifier.width(14.dp))
            }
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.onSurface
            )
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
    if (grouped) {
        Box(
            modifier = modifier.fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
        ) { rowContent() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth()
                .clip(shape)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle),
            shape = shape,
            color = pillars.cardSurface,
            shadowElevation = 0.dp
        ) { rowContent() }
    }
}

@Composable internal fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title, modifier = Modifier.semantics { heading() }) },
        text = { Text(body, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.account_done)) } })
}

@Composable fun PrivacyDialog(onDismiss: () -> Unit) = InfoDialog(
    stringResource(R.string.account_privacy), stringResource(R.string.account_privacy_body), onDismiss)

@Composable fun AboutDialog(onDismiss: () -> Unit) = InfoDialog(
    stringResource(R.string.account_help_about), stringResource(R.string.account_about_body, BuildConfig.VERSION_NAME), onDismiss)

@Composable fun StorageOverviewDialog(usage: StorageUsage, onDismiss: () -> Unit, onClean: (() -> Unit)? = null, outputFiles: List<OutputRecord> = emptyList()) {
    val context = LocalContext.current
    val items = listOf(R.string.storage_imports to usage.sources, R.string.storage_results to usage.outputs,
        R.string.storage_transfers to usage.partials, R.string.storage_temporary to usage.temporary, R.string.storage_trash to usage.trash)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.account_storage), modifier = Modifier.semantics { heading() }) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items.forEach { (label, bytes) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(Formatter.formatFileSize(context, bytes), style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
                }
            }
            if(outputFiles.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.redesign_storage_by_type),style=MaterialTheme.typography.titleSmall,modifier=Modifier.semantics { heading() })
                val groups=outputFiles.groupBy { StoragePaths.category(it.mimeType) }
                listOf("Video","Audio","PDF","Images","Documents").forEach {category->
                    val bytes=groups[category].orEmpty().sumOf { it.sizeBytes }
                    if(bytes>0L) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(when(category) {
                                "Video"->R.string.redesign_filter_video
                                "Audio"->R.string.redesign_filter_audio
                                "PDF"->R.string.redesign_pdf_type
                                "Images"->R.string.redesign_filter_images
                                else->R.string.redesign_filter_documents
                            }),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                            Text(Formatter.formatFileSize(context,bytes),style=MaterialTheme.typography.bodySmall.copy(textDirection=TextDirection.Ltr))
                        }
                        LinearProgressIndicator(progress={if(usage.outputs>0) (bytes.toFloat()/usage.outputs).coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxWidth())
                    }
                }
            }
            Text(stringResource(R.string.storage_scope), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = {
        Row {
            if(onClean!=null) TextButton(onClean) {Text(stringResource(R.string.pref_clear_temporary))}
            TextButton(onDismiss) { Text(stringResource(R.string.account_done)) }
        }
    })
}
