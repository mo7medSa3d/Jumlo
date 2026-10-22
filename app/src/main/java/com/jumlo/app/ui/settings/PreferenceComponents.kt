package com.jumlo.app.ui.settings

import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
    Row(Modifier.fillMaxWidth().padding(top = 25.dp, bottom = 7.dp)
        .semantics { heading() }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(width = 4.dp, height = 20.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable fun PreferenceRow(title: String, summary: String?, icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val haptics=rememberAppHaptics()
    Row(modifier.pressFeedback().fillMaxWidth().heightIn(min = 76.dp)
        .clip(RoundedCornerShape(16.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = {
            if(destructive) haptics.secondaryAction() else haptics.actionClick()
            onClick()
        })
        .padding(vertical = 11.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        QuietIconTile(icon,
            tint=if(destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            container=if(destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            size=44.dp)
        Spacer(Modifier.width(14.dp))
        // The chevron anchors to the far edge in both LTR and RTL.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = color)
            summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.account_storage)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items.forEach { (label, bytes) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(Formatter.formatFileSize(context, bytes), style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
                }
            }
            if(outputFiles.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.redesign_storage_by_type),style=MaterialTheme.typography.titleSmall)
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
